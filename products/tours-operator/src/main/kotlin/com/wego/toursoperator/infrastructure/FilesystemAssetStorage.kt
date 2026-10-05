package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.AssetStorage
import com.wego.toursoperator.application.AssetStorageException
import com.wego.toursoperator.application.ImageProcessor
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions

/**
 * Private single-service volume, never a public static directory.
 *
 * Publication uses an atomic same-filesystem hard link to a fully written temporary file.
 * Unlike ATOMIC_MOVE, this cannot silently replace an existing immutable target. Unsupported
 * filesystems fail closed; there is no partial-copy or overwrite fallback. Directory/file
 * symlinks are rejected, and newly created directories/files have private POSIX permissions.
 * Local Linux directory fsync is required: file data, created directories, publication links
 * and unlinks are forced before success. An unsupported directory sync is a storage failure.
 * The volume must not be concurrently modified by another local principal.
 */
class FilesystemAssetStorage(
    volumeRoot: Path,
) : AssetStorage {
    private val root = volumeRoot.toAbsolutePath().normalize()
    private val lock = Any()

    init {
        require(volumeRoot.toString().isNotBlank() && root != root.root) { "A dedicated media volume root is required" }
        storageOperation { ensureRoot(create = true) }
    }

    override fun write(
        storageKey: String,
        bytes: ByteArray,
    ) = synchronized(lock) {
        require(bytes.isNotEmpty() && bytes.size <= ImageProcessor.MAX_FILE_SIZE_BYTES) {
            "Managed asset bytes exceed the storage budget"
        }
        storageOperation {
            val target = resolve(storageKey, createParents = true)
            var temporary: Path? = null
            try {
                temporary = Files.createTempFile(target.parent, ".upload-", ".tmp", FILE_PERMISSIONS)
                FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS).use { channel ->
                    val buffer = ByteBuffer.wrap(bytes)
                    while (buffer.hasRemaining()) channel.write(buffer)
                    channel.force(true)
                }
                // createLink is atomic no-clobber publication, even across storage instances.
                resolve(storageKey, createParents = false)
                Files.createLink(target, temporary)
                forceDirectory(target.parent)
            } finally {
                temporary?.let {
                    if (Files.deleteIfExists(it)) forceDirectory(it.parent)
                }
            }
        }
    }

    override fun read(storageKey: String): ByteArray? =
        synchronized(lock) {
            storageOperation {
                val path = resolve(storageKey, createParents = false)
                if (!isRegularFile(path)) return@storageOperation null
                FileChannel.open(path, READ, NOFOLLOW_LINKS).use { channel ->
                    if (channel.size() > ImageProcessor.MAX_FILE_SIZE_BYTES) throw IOException("Asset byte budget exceeded")
                    val output = ByteArrayOutputStream()
                    val buffer = ByteBuffer.allocate(8192)
                    while (channel.read(buffer) >= 0) {
                        buffer.flip()
                        if (output.size().toLong() + buffer.remaining() > ImageProcessor.MAX_FILE_SIZE_BYTES) {
                            throw IOException("Asset byte budget exceeded")
                        }
                        output.write(buffer.array(), 0, buffer.remaining())
                        buffer.clear()
                    }
                    output.toByteArray()
                }
            }
        }

    override fun delete(storageKey: String) =
        synchronized(lock) {
            storageOperation {
                val path = resolve(storageKey, createParents = false)
                if (isRegularFile(path)) {
                    Files.delete(path)
                    forceDirectory(path.parent)
                }
            }
        }

    override fun exists(storageKey: String): Boolean =
        synchronized(lock) {
            storageOperation { isRegularFile(resolve(storageKey, createParents = false)) }
        }

    private fun resolve(
        storageKey: String,
        createParents: Boolean,
    ): Path {
        require(STORAGE_KEY.matches(storageKey)) { "Invalid managed asset storage key" }
        ensureRoot(create = false)
        val target = root.resolve(storageKey).normalize()
        require(target.startsWith(root)) { "Invalid managed asset storage key" }
        var directory = root
        for (part in root.relativize(target.parent)) {
            directory = directory.resolve(part)
            ensureDirectory(directory, createParents)
        }
        return target
    }

    private fun ensureRoot(create: Boolean) {
        var directory = requireNotNull(root.root)
        for (part in root) {
            directory = directory.resolve(part)
            ensureDirectory(directory, create)
        }
    }

    private fun ensureDirectory(
        path: Path,
        create: Boolean,
    ) {
        if (create) {
            try {
                Files.createDirectory(path, DIRECTORY_PERMISSIONS)
            } catch (_: FileAlreadyExistsException) {
                // Accept a concurrently created directory only after no-follow validation.
            }
        }
        val attributes =
            try {
                Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (e: NoSuchFileException) {
                if (!create) return
                throw e
            }
        if (attributes.isSymbolicLink || !attributes.isDirectory) throw IOException("Unsafe asset directory")
        if (create) {
            // Also force already-existing paths: another storage instance may have just created
            // the same directory and not yet persisted its entry in the parent directory.
            forceDirectory(path)
            path.parent?.let { forceDirectory(it) }
        }
    }

    private fun forceDirectory(path: Path) {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        if (attributes.isSymbolicLink || !attributes.isDirectory) throw IOException("Unsafe asset directory")
        FileChannel.open(path, READ, NOFOLLOW_LINKS).use { channel -> channel.force(true) }
    }

    private fun isRegularFile(path: Path): Boolean {
        val attributes =
            try {
                Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            } catch (_: NoSuchFileException) {
                return false
            }
        if (attributes.isSymbolicLink || !attributes.isRegularFile) throw IOException("Unsafe asset file")
        return true
    }

    private inline fun <T> storageOperation(action: () -> T): T =
        try {
            action()
        } catch (e: IOException) {
            throw AssetStorageException("Managed asset storage operation failed", e)
        } catch (e: SecurityException) {
            throw AssetStorageException("Managed asset storage operation failed", e)
        } catch (e: UnsupportedOperationException) {
            throw AssetStorageException("Managed asset storage operation failed", e)
        }

    companion object {
        private const val UUID_PART = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        private val STORAGE_KEY =
            Regex(
                "^assets/(?:tour_media/$UUID_PART|category_media/(?:DESERT|SEA|CULTURAL|SHOWS|TRANSFERS))/" +
                    "$UUID_PART(?:_w(?:360|768|1024|1440))?\\.(?:jpg|png)$",
            )
        private val DIRECTORY_PERMISSIONS = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        private val FILE_PERMISSIONS = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
    }
}
