package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.ImageProcessor
import com.wego.toursoperator.application.ImageValidationException
import com.wego.toursoperator.domain.AssetMimeType
import com.wego.toursoperator.domain.AssetVariant
import com.wego.toursoperator.domain.ValidatedImage
import com.wego.toursoperator.domain.VariantImage
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

/** Only structurally complete, static JPEG/PNG are decoded; originals are never returned. */
class JdkImageProcessor : ImageProcessor {
    override fun process(
        bytes: ByteArray,
        maxFileSizeBytes: Long,
        maxPixels: Int,
    ): ValidatedImage {
        require(maxFileSizeBytes > 0 && maxPixels > 0) { "Image budgets must be positive" }
        val byteLimit = minOf(maxFileSizeBytes, ImageProcessor.MAX_FILE_SIZE_BYTES).toInt()
        val pixelLimit = minOf(maxPixels, ImageProcessor.MAX_PIXELS)
        if (bytes.size > byteLimit) invalid("file_too_large", "File exceeds the upload byte limit")
        val mime =
            AssetMimeType.detect(bytes)
                ?: invalid("unsupported_type", "File is not a JPEG or PNG")
        when (mime) {
            AssetMimeType.JPEG -> validateJpeg(bytes)
            AssetMimeType.PNG -> validatePng(bytes, pixelLimit)
        }

        val decoded = decode(bytes, mime, pixelLimit)
        val orientation = if (mime == AssetMimeType.JPEG) readExifOrientation(bytes) else 1
        val clean = cleanAndOrient(decoded, mime, orientation)
        decoded.flush()
        try {
            val processed = encode(clean, mime, byteLimit)
            return ValidatedImage(
                mimeType = mime,
                width = clean.width,
                height = clean.height,
                fileSizeBytes = processed.size.toLong(),
                sha256 =
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(processed)
                        .joinToString("") { "%02x".format(it) },
                processedBytes = processed,
                variants = buildVariants(clean, mime, byteLimit),
            )
        } finally {
            clean.flush()
        }
    }

    private fun decode(
        bytes: ByteArray,
        mime: AssetMimeType,
        pixelLimit: Int,
    ): BufferedImage =
        try {
            MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
                val readers = ImageIO.getImageReaders(stream)
                if (!readers.hasNext()) invalid("decode_failed", "No supported image reader")
                val reader = readers.next()
                try {
                    val expectedFormat = if (mime == AssetMimeType.PNG) "png" else "jpeg"
                    if (!reader.formatName.equals(expectedFormat, ignoreCase = true)) {
                        invalid("unsupported_type", "Image header and decoded format disagree")
                    }
                    var warned = false
                    reader.addIIOReadWarningListener { _, _ -> warned = true }
                    reader.setInput(stream, false, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    validateDimensions(width.toLong(), height.toLong(), pixelLimit)
                    val image = reader.read(0)
                    if (warned || image == null || image.width != width || image.height != height) {
                        image?.flush()
                        invalid("decode_failed", "Image is incomplete or malformed")
                    }
                    image
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: ImageValidationException) {
            throw e
        } catch (_: Exception) {
            invalid("decode_failed", "Image could not be safely decoded")
        }

    /** ImageIO tolerates missing JPEG EOI; verify marker/scan boundaries before using it. */
    private fun validateJpeg(bytes: ByteArray) {
        var position = 2
        var hasFrame = false
        var hasScan = false
        while (position < bytes.size) {
            if (unsignedByte(bytes, position) != 0xff) malformed()
            while (position < bytes.size && unsignedByte(bytes, position) == 0xff) position++
            if (position >= bytes.size) malformed()
            val marker = unsignedByte(bytes, position++)
            if (marker == 0xd9) {
                if (!hasFrame || !hasScan || position != bytes.size) malformed()
                return
            }
            if (marker == 0 || marker == 0xd8 || marker in 0xd0..0xd7) malformed()
            if (marker == 0x01) continue
            if (position > bytes.size - 2) malformed()
            val length = unsignedShort(bytes, position, false)
            if (length < 2 || length > bytes.size - position) malformed()
            if (marker in FRAME_MARKERS) {
                if (length < 8) malformed()
                hasFrame = true
            }
            position += length
            if (marker == 0xda) {
                if (!hasFrame || length < 6) malformed()
                hasScan = true
                position = scanEnd(bytes, position)
            }
        }
        malformed()
    }

    private fun scanEnd(
        bytes: ByteArray,
        start: Int,
    ): Int {
        var position = start
        while (position < bytes.size) {
            if (unsignedByte(bytes, position) != 0xff) {
                position++
                continue
            }
            val markerStart = position
            while (position < bytes.size && unsignedByte(bytes, position) == 0xff) position++
            if (position >= bytes.size) malformed()
            val marker = unsignedByte(bytes, position)
            if (marker == 0 || marker in 0xd0..0xd7) {
                position++
            } else {
                return markerStart
            }
        }
        malformed()
    }

    /** PNG frame-control chunks are rejected even though the JDK reader hides APNG frames. */
    private fun validatePng(
        bytes: ByteArray,
        pixelLimit: Int,
    ) {
        if (bytes.size < 33 || !bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) malformed()
        var position = 8
        var first = true
        var hasImageData = false
        var endedImageData = false
        var compressedData: PngDataValidator? = null
        try {
            while (position <= bytes.size - 12) {
                val length = unsignedInt(bytes, position, false)
                if (length > bytes.size.toLong() - position - 12) malformed()
                val count = length.toInt()
                val type = String(bytes, position + 4, 4, Charsets.US_ASCII)
                if (!type.matches(Regex("[A-Za-z]{4}"))) malformed()
                if (type in ANIMATION_CHUNKS) invalid("animated_image", "Animated images are not accepted")
                if (first) {
                    if (type != "IHDR" || count != 13) malformed()
                    val width = unsignedInt(bytes, position + 8, false)
                    val height = unsignedInt(bytes, position + 12, false)
                    validateDimensions(width, height, pixelLimit)
                    compressedData = PngDataValidator(expectedPngDataLength(bytes, position, width, height))
                } else if (type == "IHDR") {
                    malformed()
                }
                if (type.first().isUpperCase() && type !in PNG_CRITICAL_CHUNKS) malformed()
                val crc = CRC32().apply { update(bytes, position + 4, count + 4) }
                if (crc.value != unsignedInt(bytes, position + 8 + count, false)) malformed()
                if (type == "IDAT") {
                    if (endedImageData) malformed()
                    hasImageData = true
                    requireNotNull(compressedData).feed(bytes, position + 8, count)
                } else if (hasImageData) {
                    endedImageData = true
                }
                position += count + 12
                if (type == "IEND") {
                    if (count != 0 || !hasImageData || position != bytes.size) malformed()
                    requireNotNull(compressedData).validateComplete()
                    return
                }
                first = false
            }
            malformed()
        } finally {
            compressedData?.close()
        }
    }

    private fun expectedPngDataLength(
        bytes: ByteArray,
        position: Int,
        width: Long,
        height: Long,
    ): Long {
        val bitDepth = unsignedByte(bytes, position + 16)
        val colorType = unsignedByte(bytes, position + 17)
        val channels =
            when (colorType) {
                0, 3 -> 1
                2 -> 3
                4 -> 2
                6 -> 4
                else -> malformed()
            }
        val supportedDepths =
            when (colorType) {
                0 -> setOf(1, 2, 4, 8, 16)
                3 -> setOf(1, 2, 4, 8)
                else -> setOf(8, 16)
            }
        if (bitDepth !in supportedDepths ||
            unsignedByte(bytes, position + 18) != 0 ||
            unsignedByte(bytes, position + 19) != 0
        ) {
            malformed()
        }
        val bitsPerPixel = bitDepth * channels
        return when (unsignedByte(bytes, position + 20)) {
            0 -> height * ((width * bitsPerPixel + 7) / 8 + 1)
            1 ->
                ADAM7_PASSES.sumOf { pass ->
                    val passWidth = ((width - pass[0] + pass[2] - 1) / pass[2]).coerceAtLeast(0)
                    val passHeight = ((height - pass[1] + pass[3] - 1) / pass[3]).coerceAtLeast(0)
                    if (passWidth == 0L || passHeight == 0L) 0 else passHeight * ((passWidth * bitsPerPixel + 7) / 8 + 1)
                }
            else -> malformed()
        }
    }

    private fun validateDimensions(
        width: Long,
        height: Long,
        pixelLimit: Int,
    ) {
        if (width <= 0 || height <= 0) malformed()
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || width * height > pixelLimit) {
            invalid("image_too_large", "Image dimensions exceed the safe pixel budget")
        }
    }

    private fun readExifOrientation(bytes: ByteArray): Int {
        var position = 2
        while (position <= bytes.size - 4) {
            if (unsignedByte(bytes, position) != 0xff) return 1
            while (position < bytes.size && unsignedByte(bytes, position) == 0xff) position++
            if (position >= bytes.size) return 1
            val marker = unsignedByte(bytes, position++)
            if (marker == 0xda || marker == 0xd9) return 1
            if (marker == 0x01) continue
            if (position > bytes.size - 2) return 1
            val length = unsignedShort(bytes, position, false)
            if (length < 2 || length > bytes.size - position) return 1
            val start = position + 2
            if (marker == 0xe1 &&
                length >= 8 &&
                bytes.copyOfRange(start, start + 6).contentEquals(EXIF_SIGNATURE)
            ) {
                return readTiffOrientation(bytes, start + 6, position + length)
            }
            position += length
        }
        return 1
    }

    /** TIFF offsets are unsigned and relative to the TIFF header, bounded to this APP1 only. */
    private fun readTiffOrientation(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        if (end - start < 8) return 1
        val littleEndian =
            when {
                bytes[start] == 'I'.code.toByte() && bytes[start + 1] == 'I'.code.toByte() -> true
                bytes[start] == 'M'.code.toByte() && bytes[start + 1] == 'M'.code.toByte() -> false
                else -> return 1
            }
        if (unsignedShort(bytes, start + 2, littleEndian) != 42) return 1
        val relativeIfd = unsignedInt(bytes, start + 4, littleEndian)
        if (relativeIfd < 8 || relativeIfd > end.toLong() - start - 2) return 1
        val directory = start + relativeIfd.toInt()
        val entries = unsignedShort(bytes, directory, littleEndian)
        if (entries.toLong() * 12 > end.toLong() - directory - 2) return 1
        for (entry in 0 until entries) {
            val offset = directory + 2 + entry * 12
            if (unsignedShort(bytes, offset, littleEndian) == 0x0112 &&
                unsignedShort(bytes, offset + 2, littleEndian) == 3 &&
                unsignedInt(bytes, offset + 4, littleEndian) == 1L
            ) {
                return unsignedShort(bytes, offset + 8, littleEndian).takeIf { it in 1..8 } ?: 1
            }
        }
        return 1
    }

    private fun unsignedByte(
        bytes: ByteArray,
        offset: Int,
    ): Int = bytes[offset].toInt() and 0xff

    private fun unsignedShort(
        bytes: ByteArray,
        offset: Int,
        littleEndian: Boolean,
    ): Int {
        val first = unsignedByte(bytes, offset)
        val second = unsignedByte(bytes, offset + 1)
        return if (littleEndian) first or (second shl 8) else (first shl 8) or second
    }

    private fun unsignedInt(
        bytes: ByteArray,
        offset: Int,
        littleEndian: Boolean,
    ): Long {
        var value = 0L
        for (index in 0..3) {
            val shift = if (littleEndian) index * 8 else (3 - index) * 8
            value = value or (unsignedByte(bytes, offset + index).toLong() shl shift)
        }
        return value
    }

    private fun cleanAndOrient(
        src: BufferedImage,
        mime: AssetMimeType,
        orientation: Int,
    ): BufferedImage {
        val swap = orientation in 5..8
        val width = if (swap) src.height else src.width
        val height = if (swap) src.width else src.height
        val type =
            if (mime == AssetMimeType.PNG && src.colorModel.hasAlpha()) {
                BufferedImage.TYPE_INT_ARGB
            } else {
                BufferedImage.TYPE_INT_RGB
            }
        val clean = BufferedImage(width, height, type)
        val transform =
            when (orientation) {
                2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, src.width.toDouble(), 0.0)
                3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, src.width.toDouble(), src.height.toDouble())
                4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, src.height.toDouble())
                5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
                6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, src.height.toDouble(), 0.0)
                7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, src.height.toDouble(), src.width.toDouble())
                8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, src.width.toDouble())
                else -> AffineTransform()
            }
        val graphics = clean.createGraphics()
        try {
            if (mime == AssetMimeType.JPEG) {
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, width, height)
            }
            graphics.drawImage(src, transform, null)
        } finally {
            graphics.dispose()
        }
        return clean
    }

    private fun encode(
        image: BufferedImage,
        mime: AssetMimeType,
        byteLimit: Int,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersByMIMEType(if (mime == AssetMimeType.PNG) "image/png" else "image/jpeg")
        if (!writers.hasNext()) invalid("encode_failed", "Image encoder is unavailable")
        val writer = writers.next()
        val stream = BoundedImageOutputStream(output, byteLimit)
        try {
            stream.use {
                val parameters = writer.defaultWriteParam
                if (mime == AssetMimeType.JPEG) {
                    parameters.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    parameters.compressionQuality = 0.85f
                }
                writer.output = stream
                writer.write(null, IIOImage(image, null, null), parameters)
                stream.flush()
            }
        } catch (_: Exception) {
            if (stream.exceeded) invalid("file_too_large", "Re-encoded image exceeds the safe byte limit")
            invalid("encode_failed", "Image could not be safely re-encoded")
        } finally {
            writer.dispose()
        }
        return output.toByteArray()
    }

    private fun buildVariants(
        src: BufferedImage,
        mime: AssetMimeType,
        byteLimit: Int,
    ): List<VariantImage> =
        ImageProcessor.VARIANT_WIDTHS.filter { it < src.width }.map { targetWidth ->
            val targetHeight = (src.height.toLong() * targetWidth / src.width).toInt().coerceAtLeast(1)
            val scaled = BufferedImage(targetWidth, targetHeight, src.type)
            try {
                val graphics = scaled.createGraphics()
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    graphics.drawImage(src, 0, 0, targetWidth, targetHeight, null)
                } finally {
                    graphics.dispose()
                }
                val variant = requireNotNull(AssetVariant.fromLabel("w$targetWidth"))
                VariantImage(variant, targetWidth, targetHeight, encode(scaled, mime, byteLimit))
            } finally {
                scaled.flush()
            }
        }

    private fun malformed(): Nothing = invalid("decode_failed", "Image is incomplete or malformed")

    private fun invalid(
        code: String,
        message: String,
    ): Nothing = throw ImageValidationException(code, message)

    /** ImageIO can stop after pixels and miss a truncated zlib checksum; verify the full stream. */
    private class PngDataValidator(
        private val expectedBytes: Long,
    ) : AutoCloseable {
        private val inflater = Inflater()
        private val buffer = ByteArray(8192)
        private var produced = 0L

        fun feed(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            if (length == 0) return
            if (inflater.finished()) malformedData()
            inflater.setInput(bytes, offset, length)
            try {
                while (!inflater.needsInput() && !inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    produced += count
                    if (produced > expectedBytes || inflater.needsDictionary()) malformedData()
                    if (count == 0 && !inflater.needsInput() && !inflater.finished()) malformedData()
                }
                if (inflater.finished() && inflater.remaining != 0) malformedData()
            } catch (_: DataFormatException) {
                malformedData()
            }
        }

        fun validateComplete() {
            if (!inflater.finished() || produced != expectedBytes) malformedData()
        }

        override fun close() {
            inflater.end()
        }

        private fun malformedData(): Nothing = throw ImageValidationException("decode_failed", "Image is incomplete or malformed")
    }

    /** Bound ImageIO's in-memory seekable output, not just the final ByteArrayOutputStream. */
    private class BoundedImageOutputStream(
        output: ByteArrayOutputStream,
        private val limit: Int,
    ) : MemoryCacheImageOutputStream(output) {
        var exceeded = false
            private set

        override fun write(value: Int) {
            checkBound(streamPosition + 1)
            super.write(value)
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            checkBound(streamPosition + length)
            super.write(bytes, offset, length)
        }

        override fun seek(position: Long) {
            checkBound(position)
            super.seek(position)
        }

        private fun checkBound(position: Long) {
            if (position > limit) {
                exceeded = true
                throw IOException("Image output byte budget exceeded")
            }
        }
    }

    companion object {
        private const val MAX_DIMENSION = 16_384L
        private val FRAME_MARKERS = setOf(0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf)
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        private val EXIF_SIGNATURE = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)
        private val ANIMATION_CHUNKS = setOf("acTL", "fcTL", "fdAT")
        private val PNG_CRITICAL_CHUNKS = setOf("IHDR", "PLTE", "IDAT", "IEND")
        private val ADAM7_PASSES =
            listOf(
                intArrayOf(0, 0, 8, 8),
                intArrayOf(4, 0, 8, 8),
                intArrayOf(0, 4, 4, 8),
                intArrayOf(2, 0, 4, 4),
                intArrayOf(0, 2, 2, 4),
                intArrayOf(1, 0, 2, 2),
                intArrayOf(0, 1, 1, 2),
            )
    }
}
