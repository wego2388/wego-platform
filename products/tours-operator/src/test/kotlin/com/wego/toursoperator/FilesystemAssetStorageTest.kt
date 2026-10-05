package com.wego.toursoperator

import com.wego.toursoperator.application.AssetStorageException
import com.wego.toursoperator.application.ImageProcessor
import com.wego.toursoperator.infrastructure.FilesystemAssetStorage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FilesystemAssetStorageTest {
    @TempDir
    lateinit var tempDir: Path

    private val root: Path get() = tempDir.resolve("media")

    private fun storage() = FilesystemAssetStorage(root)

    @Test
    fun `blank or filesystem root cannot be configured as an asset volume`() {
        assertThrows<IllegalArgumentException> { FilesystemAssetStorage(Path.of("")) }
        assertThrows<IllegalArgumentException> { FilesystemAssetStorage(Path.of("/")) }
    }

    @Test
    fun `write read and delete round-trip has no temporary leftovers`() {
        val store = storage()
        val bytes = "hello safari media".toByteArray()
        store.write(KEY, bytes)
        assertArrayEquals(bytes, store.read(KEY))
        assertTrue(store.exists(KEY))
        assertNoTemporaryFiles()
        store.delete(KEY)
        store.delete(KEY)
        assertNull(store.read(KEY))
        assertFalse(store.exists(KEY))
    }

    @Test
    fun `Linux directory fsync supports publication cleanup and deletion across storage instances`() {
        val bytes = "durable publication".toByteArray()
        storage().write(KEY, bytes)
        val parent = root.resolve(KEY).parent
        FileChannel.open(parent, READ, NOFOLLOW_LINKS).use { channel -> channel.force(true) }
        assertArrayEquals(bytes, storage().read(KEY))
        assertNoTemporaryFiles()

        storage().delete(KEY)
        FileChannel.open(parent, READ, NOFOLLOW_LINKS).use { channel -> channel.force(true) }
        assertFalse(storage().exists(KEY))
        assertNull(storage().read(KEY))
        assertNoTemporaryFiles()
    }

    @Test
    fun `immutable targets cannot be overwritten even by identical retry bytes`() {
        val store = storage()
        store.write(KEY, "v1".toByteArray())
        listOf("v2", "v1").forEach { contents ->
            val error = assertThrows<AssetStorageException> { store.write(KEY, contents.toByteArray()) }
            assertEquals("Managed asset storage operation failed", error.message)
            assertArrayEquals("v1".toByteArray(), store.read(KEY))
            assertNoTemporaryFiles()
        }
    }

    @Test
    fun `missing nested keys return null and false without creating directories`() {
        val store = storage()
        assertNull(store.read(KEY))
        assertFalse(store.exists(KEY))
        store.delete(KEY)
        assertFalse(Files.exists(root.resolve("assets")))
    }

    @Test
    fun `generated category and variant paths are accepted`() {
        val store = storage()
        listOf("DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS").forEach { category ->
            val key = "assets/category_media/$category/$ASSET.png"
            store.write(key, "category image".toByteArray())
            assertTrue(store.exists(key))
        }
        listOf(360, 768, 1024, 1440).forEach { width ->
            val key = KEY.removeSuffix(".jpg") + "_w$width.jpg"
            store.write(key, "variant".toByteArray())
            assertTrue(store.exists(key))
        }
    }

    @Test
    fun `traversal absolute arbitrary categories filenames and variants are rejected everywhere`() {
        val store = storage()
        val invalid =
            listOf(
                "../outside/file.jpg",
                "../../etc/passwd",
                "/etc/passwd",
                "assets/tour_media/$OWNER/../$ASSET.jpg",
                "assets/tour_media/$OWNER/./$ASSET.jpg",
                "assets/tour_media/$OWNER/$ASSET.jpg/",
                "assets\\tour_media\\$OWNER\\$ASSET.jpg",
                "assets/tour_media/abc/file.jpg",
                "assets/tour_media/$OWNER/${ASSET.uppercase()}.jpg",
                "assets/category_media/OTHER/$ASSET.png",
                "assets/category_media/desert/$ASSET.jpg",
                KEY.removeSuffix(".jpg") + "_w99.jpg",
                KEY.removeSuffix(".jpg") + "_base.jpg",
                KEY.removeSuffix(".jpg") + ".svg",
                KEY + "\u0000",
            )
        invalid.forEach { key ->
            assertThrows<IllegalArgumentException> { store.write(key, ByteArray(1)) }
            assertThrows<IllegalArgumentException> { store.read(key) }
            assertThrows<IllegalArgumentException> { store.exists(key) }
            assertThrows<IllegalArgumentException> { store.delete(key) }
        }
    }

    @Test
    fun `relative configured root is made absolute and normalized`() {
        val relative =
            Path
                .of("")
                .toAbsolutePath()
                .relativize(root)
                .resolve("child/..")
        val store = FilesystemAssetStorage(relative)
        store.write(KEY, "relative root".toByteArray())
        assertArrayEquals("relative root".toByteArray(), Files.readAllBytes(root.resolve(KEY)))
    }

    @Test
    fun `root symlink and ancestor symlink are rejected at construction`() {
        val outside = Files.createDirectory(tempDir.resolve("outside"))
        val link = Files.createSymbolicLink(tempDir.resolve("link"), outside)
        assertThrows<AssetStorageException> { FilesystemAssetStorage(link) }
        assertThrows<AssetStorageException> { FilesystemAssetStorage(link.resolve("child")) }
        assertFalse(Files.exists(outside.resolve("child")))
    }

    @Test
    fun `directory symlinks cannot redirect write read delete or exists outside volume`() {
        val store = storage()
        val outside = Files.createDirectory(tempDir.resolve("outside"))
        Files.createSymbolicLink(root.resolve("assets"), outside)
        assertThrows<AssetStorageException> { store.write(KEY, "unsafe".toByteArray()) }
        assertThrows<AssetStorageException> { store.read(KEY) }
        assertThrows<AssetStorageException> { store.delete(KEY) }
        assertThrows<AssetStorageException> { store.exists(KEY) }
        Files.list(outside).use { assertEquals(0, it.count()) }
    }

    @Test
    fun `deep owner directory symlink is rejected`() {
        val store = storage()
        Files.createDirectories(root.resolve("assets/tour_media"))
        val outside = Files.createDirectory(tempDir.resolve("outside"))
        Files.createSymbolicLink(root.resolve("assets/tour_media/$OWNER"), outside)
        assertThrows<AssetStorageException> { store.write(KEY, "unsafe".toByteArray()) }
        assertFalse(Files.exists(outside.resolve("$ASSET.jpg")))
    }

    @Test
    fun `target symlink cannot reveal replace or delete an external file`() {
        val store = storage()
        Files.createDirectories(root.resolve(KEY).parent)
        val outside = Files.write(tempDir.resolve("outside.jpg"), "owner original".toByteArray())
        Files.createSymbolicLink(root.resolve(KEY), outside)
        assertThrows<AssetStorageException> { store.write(KEY, "replacement".toByteArray()) }
        assertThrows<AssetStorageException> { store.read(KEY) }
        assertThrows<AssetStorageException> { store.exists(KEY) }
        assertThrows<AssetStorageException> { store.delete(KEY) }
        assertArrayEquals("owner original".toByteArray(), Files.readAllBytes(outside))
        assertTrue(Files.isSymbolicLink(root.resolve(KEY)))
        assertNoTemporaryFiles()
    }

    @Test
    fun `root replaced by symlink after construction is rejected`() {
        val store = storage()
        Files.delete(root)
        val outside = Files.createDirectory(tempDir.resolve("outside"))
        Files.createSymbolicLink(root, outside)
        assertThrows<AssetStorageException> { store.write(KEY, "unsafe".toByteArray()) }
        assertThrows<AssetStorageException> { store.read(KEY) }
    }

    @Test
    fun `failed publication leaves no partial target or temporary file`() {
        val store = storage()
        Files.createDirectories(root.resolve(KEY))
        assertThrows<AssetStorageException> { store.write(KEY, "cannot replace directory".toByteArray()) }
        assertTrue(Files.isDirectory(root.resolve(KEY)))
        assertNoTemporaryFiles()
    }

    @Test
    fun `new asset directories and files have owner-only permissions`() {
        storage().write(KEY, "private".toByteArray())
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(root.resolve(KEY)))
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root))
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root.resolve(KEY).parent))
    }

    @Test
    fun `empty or oversized writes and oversized disk reads are bounded`() {
        val store = storage()
        assertThrows<IllegalArgumentException> { store.write(KEY, ByteArray(0)) }
        assertThrows<IllegalArgumentException> { store.write(KEY, ByteArray(ImageProcessor.MAX_FILE_SIZE_BYTES.toInt() + 1)) }
        Files.createDirectories(root.resolve(KEY).parent)
        FileChannel.open(root.resolve(KEY), WRITE, CREATE_NEW).use { channel ->
            channel.position(ImageProcessor.MAX_FILE_SIZE_BYTES)
            channel.write(ByteBuffer.wrap(byteArrayOf(1)))
        }
        assertThrows<AssetStorageException> { store.read(KEY) }
    }

    @Test
    fun `concurrent storage instances publish exactly one complete immutable winner`() {
        val stores = (1..8).map { storage() }
        val ready = CountDownLatch(stores.size)
        val start = CountDownLatch(1)
        val winners = AtomicInteger()
        val failures = AtomicInteger()
        val executor = Executors.newFixedThreadPool(stores.size)
        try {
            val tasks =
                stores.mapIndexed { index, store ->
                    executor.submit {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        try {
                            store.write(KEY, ByteArray(8192) { index.toByte() })
                            winners.incrementAndGet()
                        } catch (_: AssetStorageException) {
                            failures.incrementAndGet()
                        }
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, winners.get())
            assertEquals(stores.size - 1, failures.get())
            val bytes = requireNotNull(storage().read(KEY))
            assertEquals(8192, bytes.size)
            assertTrue(bytes.all { it == bytes.first() })
            assertNoTemporaryFiles()
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertNoTemporaryFiles() {
        Files.walk(root).use { files -> assertFalse(files.anyMatch { it.fileName.toString().startsWith(".upload-") }) }
    }

    companion object {
        private const val OWNER = "12345678-abcd-1234-abcd-123456789abc"
        private const val ASSET = "23456789-bcde-2345-bcde-23456789abcd"
        private const val KEY = "assets/tour_media/$OWNER/$ASSET.jpg"
    }
}
