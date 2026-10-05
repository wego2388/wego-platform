package com.wego.toursoperator

import com.wego.toursoperator.application.ImageProcessor
import com.wego.toursoperator.application.ImageValidationException
import com.wego.toursoperator.domain.AssetMimeType
import com.wego.toursoperator.infrastructure.JdkImageProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

class MediaImageProcessorTest {
    private val processor: ImageProcessor = JdkImageProcessor()

    @Test
    fun `JPEG accepted with deterministic SHA and all bounded variants`() {
        val jpeg = makeImage(1600, 800, "jpg")
        val result = processor.process(jpeg)
        assertEquals(AssetMimeType.JPEG, result.mimeType)
        assertEquals(1600, result.width)
        assertEquals(800, result.height)
        assertEquals(listOf("w360", "w768", "w1024", "w1440"), result.variants.map { it.variant.label })
        assertEquals(result.processedBytes.size.toLong(), result.fileSizeBytes)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(result.processedBytes).joinToString("") { "%02x".format(it) },
            result.sha256,
        )
        assertEquals(result.sha256, processor.process(jpeg).sha256)
        result.variants.forEach { variant ->
            val decoded = ImageIO.read(ByteArrayInputStream(variant.bytes))
            assertEquals(variant.width, decoded.width)
            assertEquals(variant.height, decoded.height)
            assertEquals(variant.width / 2, variant.height)
            assertTrue(variant.width < result.width)
            assertTrue(variant.bytes.size <= ImageProcessor.MAX_FILE_SIZE_BYTES)
        }
    }

    @Test
    fun `PNG accepted and alpha preserved by clean re-encoding`() {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(12, 12, Color(120, 160, 200, 80).rgb)
        val result = processor.process(encode(image, "png"))
        val decoded = ImageIO.read(ByteArrayInputStream(result.processedBytes))
        assertEquals(AssetMimeType.PNG, result.mimeType)
        assertEquals(64, result.width)
        assertEquals(48, result.height)
        assertTrue(decoded.colorModel.hasAlpha())
        assertEquals(80, Color(decoded.getRGB(12, 12), true).alpha)
        assertEquals(0, Color(decoded.getRGB(0, 0), true).alpha)
    }

    @Test
    fun `oversize rejected before magic or decode and callers cannot raise hard caps`() {
        val oversized = ByteArray(10 * 1024 * 1024 + 1)
        assertEquals(
            "file_too_large",
            assertThrows<ImageValidationException> {
                processor.process(oversized, Long.MAX_VALUE, Int.MAX_VALUE)
            }.code,
        )
    }

    @Test
    fun `non-positive budgets are rejected`() {
        assertThrows<IllegalArgumentException> { processor.process(ByteArray(0), 0, 1) }
        assertThrows<IllegalArgumentException> { processor.process(ByteArray(0), 1, 0) }
    }

    @Test
    fun `fake PDF GIF SVG and empty images are rejected`() {
        listOf("%PDF-1.4", "GIF89a", "<svg></svg>", "").forEach { source ->
            assertEquals(
                "unsupported_type",
                assertThrows<ImageValidationException> {
                    processor.process(source.toByteArray())
                }.code,
            )
        }
    }

    @Test
    fun `corrupt JPEG and mismatched PNG header are rejected with safe error detail`() {
        val jpegHeader = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        listOf(jpegHeader + ByteArray(50), PNG_SIGNATURE + makeImage(64, 48, "jpg")).forEach { bytes ->
            val failure = assertThrows<ImageValidationException> { processor.process(bytes) }
            assertEquals("decode_failed", failure.code)
            assertEquals("Image is incomplete or malformed", failure.message)
        }
    }

    @Test
    fun `complete JPEG with missing EOI is rejected despite permissive ImageIO reader`() {
        val valid = makeImage(80, 48, "jpg")
        assertEquals(
            "decode_failed",
            assertThrows<ImageValidationException> {
                processor.process(valid.copyOf(valid.size - 2))
            }.code,
        )
    }

    @Test
    fun `JPEG truncated entropy even with replacement EOI is rejected`() {
        val valid = makeImage(640, 480, "jpg")
        val truncated = valid.copyOf(valid.size - 50) + byteArrayOf(0xff.toByte(), 0xd9.toByte())
        assertEquals(
            "decode_failed",
            assertThrows<ImageValidationException> {
                processor.process(truncated)
            }.code,
        )
    }

    @Test
    fun `JPEG and PNG trailing payload or concatenated image are rejected`() {
        listOf("jpg", "png").forEach { format ->
            val image = makeImage(80, 48, format)
            listOf(image + "private payload".toByteArray(), image + image).forEach { bytes ->
                assertEquals(
                    "decode_failed",
                    assertThrows<ImageValidationException> {
                        processor.process(bytes)
                    }.code,
                )
            }
        }
    }

    @Test
    fun `PNG missing IEND invalid CRC and invalid full signature are rejected`() {
        val png = makeImage(80, 48, "png")
        val invalidCrc = png.clone().apply { this[29] = (this[29].toInt() xor 1).toByte() }
        val invalidSignature = png.clone().apply { this[7] = 0 }
        listOf(png.copyOf(png.size - 12), invalidCrc, invalidSignature).forEach { bytes ->
            assertEquals(
                "decode_failed",
                assertThrows<ImageValidationException> {
                    processor.process(bytes)
                }.code,
            )
        }
    }

    @Test
    fun `PNG hostile unsigned chunk length is rejected without indexing overflow`() {
        val png = makeImage(80, 48, "png").apply { ByteBuffer.wrap(this, 8, 4).putInt(-1) }
        assertEquals("decode_failed", assertThrows<ImageValidationException> { processor.process(png) }.code)
    }

    @Test
    fun `PNG truncated compressed data with repaired chunk CRC is still rejected`() {
        val png = makeImage(80, 48, "png")
        val length = ByteBuffer.wrap(png, 33, 4).int
        assertEquals("IDAT", String(png, 37, 4, Charsets.US_ASCII))
        val compressed = png.copyOfRange(41, 41 + length - 5)
        val truncated = png.copyOfRange(0, 33) + pngChunk("IDAT", compressed) + png.copyOfRange(45 + length, png.size)
        assertEquals("decode_failed", assertThrows<ImageValidationException> { processor.process(truncated) }.code)
    }

    @Test
    fun `PNG incorrect zlib checksum and excess inflated pixels are rejected`() {
        val png = makeImage(80, 48, "png")
        val length = ByteBuffer.wrap(png, 33, 4).int
        val compressed = png.copyOfRange(41, 41 + length).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val wrongChecksum = png.copyOfRange(0, 33) + pngChunk("IDAT", compressed) + png.copyOfRange(45 + length, png.size)
        val smallHeader = png.copyOfRange(16, 29).apply { ByteBuffer.wrap(this).putInt(1).putInt(1) }
        val excessInflated = png.copyOfRange(0, 8) + pngChunk("IHDR", smallHeader) + png.copyOfRange(33, png.size)
        listOf(wrongChecksum, excessInflated).forEach { bytes ->
            assertEquals("decode_failed", assertThrows<ImageValidationException> { processor.process(bytes) }.code)
        }
    }

    @Test
    fun `PNG compressed stream may cross multiple consecutive IDAT chunks`() {
        val png = makeImage(80, 48, "png")
        val length = ByteBuffer.wrap(png, 33, 4).int
        val midpoint = length / 2
        val split =
            png.copyOfRange(0, 33) + pngChunk("IDAT", png.copyOfRange(41, 41 + midpoint)) +
                pngChunk("IDAT", png.copyOfRange(41 + midpoint, 41 + length)) + png.copyOfRange(45 + length, png.size)
        val result = processor.process(split)
        assertEquals(80, result.width)
        assertEquals(48, result.height)
    }

    @Test
    fun `legitimate Adam7 interlaced PNG and progressive JPEG remain supported`() {
        listOf("png", "jpeg").forEach { format ->
            val image = BufferedImage(17, 13, BufferedImage.TYPE_INT_RGB)
            val output = ByteArrayOutputStream()
            val writer = ImageIO.getImageWritersByFormatName(format).next()
            try {
                MemoryCacheImageOutputStream(output).use { stream ->
                    val parameter = writer.defaultWriteParam
                    assertTrue(parameter.canWriteProgressive())
                    parameter.progressiveMode = ImageWriteParam.MODE_DEFAULT
                    writer.output = stream
                    writer.write(null, IIOImage(image, null, null), parameter)
                }
            } finally {
                writer.dispose()
            }
            val result = processor.process(output.toByteArray())
            assertEquals(17, result.width)
            assertEquals(13, result.height)
        }
    }

    @Test
    fun `all APNG control chunks are rejected even when ImageIO reports a single image`() {
        val png = makeImage(80, 48, "png")
        listOf("acTL", "fcTL", "fdAT").forEach { type ->
            val animated = png.copyOfRange(0, 33) + pngChunk(type, ByteArray(8)) + png.copyOfRange(33, png.size)
            assertEquals(
                "animated_image",
                assertThrows<ImageValidationException> {
                    processor.process(animated)
                }.code,
            )
        }
    }

    @Test
    fun `actual PNG header pixel bomb and pathological skinny dimensions rejected predecode`() {
        val base = makeImage(1, 1, "png")
        listOf(100_000 to 100_000, 1 to 24_000_000, 24_000_000 to 1, Int.MAX_VALUE to Int.MAX_VALUE).forEach { dimensions ->
            val data = base.copyOfRange(16, 29)
            ByteBuffer.wrap(data).putInt(dimensions.first).putInt(dimensions.second)
            val bomb = base.copyOfRange(0, 8) + pngChunk("IHDR", data) + base.copyOfRange(33, base.size)
            assertEquals(
                "image_too_large",
                assertThrows<ImageValidationException> {
                    processor.process(bomb, Long.MAX_VALUE, Int.MAX_VALUE)
                }.code,
            )
        }
    }

    @Test
    fun `pixel budget and invalid zero dimensions are checked from header`() {
        val small = makeImage(100, 100, "jpg")
        processor.process(small)
        assertEquals(
            "image_too_large",
            assertThrows<ImageValidationException> {
                processor.process(small, maxPixels = 100)
            }.code,
        )
        val base = makeImage(1, 1, "png")
        val data = base.copyOfRange(16, 29).apply { ByteBuffer.wrap(this).putInt(0) }
        val malformed = base.copyOfRange(0, 8) + pngChunk("IHDR", data) + base.copyOfRange(33, base.size)
        assertEquals("decode_failed", assertThrows<ImageValidationException> { processor.process(malformed) }.code)
    }

    @Test
    fun `all eight EXIF orientations put source quadrants into the correct pixel positions`() {
        val expected =
            listOf(
                listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
                listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
                listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
                listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
                listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
                listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
                listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
                listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE),
            )
        for (orientation in 1..8) {
            val jpeg = withExif(makeImage(80, 48, "jpg"), tiffOrientation(orientation))
            val result = processor.process(jpeg)
            assertEquals(if (orientation >= 5) 48 else 80, result.width, "orientation=$orientation")
            assertEquals(if (orientation >= 5) 80 else 48, result.height, "orientation=$orientation")
            val decoded = ImageIO.read(ByteArrayInputStream(result.processedBytes))
            val points =
                listOf(
                    decoded.width / 4 to decoded.height / 4,
                    decoded.width * 3 / 4 to decoded.height / 4,
                    decoded.width / 4 to decoded.height * 3 / 4,
                    decoded.width * 3 / 4 to decoded.height * 3 / 4,
                )
            points.forEachIndexed { index, point ->
                val actual = Color(decoded.getRGB(point.first, point.second))
                val target = expected[orientation - 1][index]
                assertTrue(
                    kotlin.math.abs(actual.red - target.red) < 20 &&
                        kotlin.math.abs(actual.green - target.green) < 20 &&
                        kotlin.math.abs(actual.blue - target.blue) < 20,
                    "orientation=$orientation quadrant=$index actual=$actual expected=$target",
                )
            }
        }
    }

    @Test
    fun `big-endian TIFF orientation is applied`() {
        val result = processor.process(withExif(makeImage(80, 48, "jpg"), tiffOrientation(8, littleEndian = false)))
        assertEquals(48, result.width)
        assertEquals(80, result.height)
    }

    @Test
    fun `hostile EXIF unsigned offsets and invalid tag layouts are safely ignored and stripped`() {
        val mutations =
            listOf<(ByteArray) -> Unit>(
                { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, -8) },
                { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, Int.MAX_VALUE) },
                { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 0) },
                { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(8, (-1).toShort()) },
                { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(12, 4) },
                { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(14, Int.MAX_VALUE) },
                { it[0] = 'X'.code.toByte() },
                { it[2] = 43 },
                { it[18] = 9 },
            )
        mutations.forEach { mutate ->
            val tiff = tiffOrientation(6).apply(mutate)
            val result = processor.process(withExif(makeImage(80, 48, "jpg"), tiff))
            assertEquals(80, result.width)
            assertEquals(48, result.height)
            assertFalse(String(result.processedBytes, Charsets.ISO_8859_1).contains("Exif"))
        }
    }

    @Test
    fun `EXIF GPS JPEG comments and PNG text metadata do not survive in any output`() {
        val secret = "private-GPS-location-27-34"
        val jpeg = withExif(makeImage(800, 600, "jpg"), tiffOrientation(6) + secret.toByteArray())
        val commented = jpeg.copyOfRange(0, 2) + jpegSegment(0xfe, secret.toByteArray()) + jpeg.copyOfRange(2, jpeg.size)
        val png = makeImage(800, 600, "png")
        val textPng = png.copyOfRange(0, 33) + pngChunk("tEXt", "Comment\u0000$secret".toByteArray()) + png.copyOfRange(33, png.size)
        listOf(commented, textPng).forEach { bytes ->
            val result = processor.process(bytes)
            (listOf(result.processedBytes) + result.variants.map { it.bytes }).forEach { output ->
                val text = String(output, Charsets.ISO_8859_1)
                assertFalse(text.contains(secret))
                assertFalse(text.contains("Exif"))
            }
        }
    }

    @Test
    fun `no variants upscale or duplicate an exact width boundary`() {
        assertTrue(processor.process(makeImage(200, 150, "jpg")).variants.isEmpty())
        assertEquals(listOf("w360"), processor.process(makeImage(768, 384, "jpg")).variants.map { it.variant.label })
    }

    @Test
    fun `re-encoded output is bounded even when an indexed input compresses much smaller`() {
        val input = encode(BufferedImage(1024, 1024, BufferedImage.TYPE_BYTE_BINARY), "png")
        assertEquals(
            "file_too_large",
            assertThrows<ImageValidationException> {
                processor.process(input, maxFileSizeBytes = input.size.toLong())
            }.code,
        )
    }

    private fun makeImage(
        width: Int,
        height: Int,
        format: String,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
            colors.forEachIndexed { index, color ->
                graphics.color = color
                graphics.fillRect((index % 2) * (width / 2), (index / 2) * (height / 2), width / 2 + 1, height / 2 + 1)
            }
        } finally {
            graphics.dispose()
        }
        return encode(image, format)
    }

    private fun encode(
        image: BufferedImage,
        format: String,
    ): ByteArray =
        ByteArrayOutputStream().use { output ->
            assertTrue(ImageIO.write(image, format, output))
            output.toByteArray()
        }

    private fun withExif(
        jpeg: ByteArray,
        tiff: ByteArray,
    ): ByteArray = jpeg.copyOfRange(0, 2) + jpegSegment(0xe1, "Exif\u0000\u0000".toByteArray() + tiff) + jpeg.copyOfRange(2, jpeg.size)

    private fun jpegSegment(
        marker: Int,
        payload: ByteArray,
    ): ByteArray =
        byteArrayOf(0xff.toByte(), marker.toByte()) + ByteBuffer.allocate(2).putShort((payload.size + 2).toShort()).array() + payload

    private fun tiffOrientation(
        orientation: Int,
        littleEndian: Boolean = true,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(26).order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        buffer.put(if (littleEndian) 'I'.code.toByte() else 'M'.code.toByte())
        buffer.put(if (littleEndian) 'I'.code.toByte() else 'M'.code.toByte())
        buffer.putShort(42).putInt(8).putShort(1)
        buffer
            .putShort(0x0112)
            .putShort(3)
            .putInt(1)
            .putShort(orientation.toShort())
            .putShort(0)
            .putInt(0)
        return buffer.array()
    }

    private fun pngChunk(
        type: String,
        data: ByteArray,
    ): ByteArray {
        val typedData = type.toByteArray(Charsets.US_ASCII) + data
        val crc = CRC32().apply { update(typedData) }.value
        return ByteBuffer
            .allocate(data.size + 12)
            .putInt(data.size)
            .put(typedData)
            .putInt(crc.toInt())
            .array()
    }

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}
