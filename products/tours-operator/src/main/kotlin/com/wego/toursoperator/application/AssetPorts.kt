package com.wego.toursoperator.application

import com.wego.toursoperator.domain.AssetVariant
import com.wego.toursoperator.domain.ManagedAsset
import com.wego.toursoperator.domain.ValidatedImage
import java.util.UUID

/** Ports for image processing (implemented in infrastructure). */

interface ImageProcessor {
    /**
     * Validate and re-encode a raw upload:
     * - Reads the magic bytes to confirm JPEG or PNG (never trusts filename/Content-Type).
     * - Decodes; checks actual pixel dimensions against [maxPixels] budget.
     * - Applies EXIF orientation if present, then strips all EXIF/GPS/comment metadata.
     * - Re-encodes at a normalised quality.
     * - Produces [AssetVariant] width-constrained derivatives (skip if source is narrower).
     * - Returns a [ValidatedImage] with processed bytes and variants.
     *
     * Throws [ImageValidationException] on any structural or size violation.
     */
    fun process(
        bytes: ByteArray,
        maxFileSizeBytes: Long = MAX_FILE_SIZE_BYTES,
        maxPixels: Int = MAX_PIXELS,
    ): ValidatedImage

    companion object {
        const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024 // 10 MiB
        const val MAX_PIXELS = 24_000_000 // 24 MP
        val VARIANT_WIDTHS = listOf(360, 768, 1024, 1440)
    }
}

/** Thrown when a binary upload fails structural or size validation. */
class ImageValidationException(
    val code: String,
    message: String,
) : RuntimeException(message)

/** Low-level byte storage for the media volume (implemented in infrastructure). */
interface AssetStorage {
    /**
     * Write [bytes] to the given [storageKey] inside the media volume.
     * Uses atomic write (temp file → rename) to prevent partial files.
     * Throws [AssetStorageException] on I/O failure.
     */
    fun write(
        storageKey: String,
        bytes: ByteArray,
    )

    /**
     * Read [storageKey] from the media volume.
     * Returns null when the key does not exist (never throws for missing files).
     */
    fun read(storageKey: String): ByteArray?

    /**
     * Delete [storageKey] from the media volume.
     * Idempotent: does not throw when the key is absent.
     */
    fun delete(storageKey: String)

    /** True when [storageKey] exists in the volume. */
    fun exists(storageKey: String): Boolean
}

class AssetStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Repository for [ManagedAsset] and its variants (implemented by jOOQ). */
interface AssetRepository {
    fun save(asset: ManagedAsset): ManagedAsset

    fun findById(id: UUID): ManagedAsset?

    fun findByOwner(
        ownerType: String,
        ownerRef: String,
    ): List<ManagedAsset>

    fun findByUploadRequest(
        ownerType: String,
        ownerRef: String,
        requestId: UUID,
    ): ManagedAsset?

    /** True when any media row references this asset id. */
    fun isReferenced(assetId: UUID): Boolean

    /**
     * True only for an exact approved link to [assetId] at [publicPath] on an active tour.
     * Used by the public endpoint to re-check publication rights on every request.
     */
    fun isTourMediaApproved(
        assetId: UUID,
        publicPath: String,
    ): Boolean
}
