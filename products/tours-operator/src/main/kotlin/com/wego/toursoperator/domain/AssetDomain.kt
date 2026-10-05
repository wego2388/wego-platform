package com.wego.toursoperator.domain

import java.time.Instant
import java.util.UUID

/**
 * Variant sizes produced for every uploaded image. The base variant is the
 * re-encoded original (EXIF stripped, orientation applied). Mobile variants
 * are width-constrained derivatives of the base.
 */
enum class AssetVariant(
    val label: String,
) {
    BASE("base"),
    W360("w360"),
    W768("w768"),
    W1024("w1024"),
    W1440("w1440"),
    ;

    companion object {
        fun fromLabel(label: String): AssetVariant? = entries.firstOrNull { it.label == label }
    }
}

/**
 * One derived file for a single [ManagedAsset]. The storage key is the
 * server-assigned path inside the media volume (never the original filename).
 */
data class AssetVariantRecord(
    val assetId: UUID,
    val variant: AssetVariant,
    val storageKey: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
)

/**
 * An immutable server-assigned asset produced from a validated upload.
 * Callers never supply storage paths, dimensions, or SHA-256 hashes;
 * all fields are written by the server after decode/re-encode.
 *
 * The asset id is stable for the life of the record. Deleting an asset
 * is only permitted when no live media reference points to it.
 */
data class ManagedAsset(
    val id: UUID,
    /** Discriminator: "tour_media" or "category_media". */
    val ownerType: String,
    /** UUID string for tours; category code for categories. */
    val ownerRef: String,
    /** Server-assigned path inside the media volume. */
    val storageKey: String,
    val mimeType: AssetMimeType,
    val originalWidth: Int,
    val originalHeight: Int,
    val fileSizeBytes: Long,
    val sha256: String,
    val variants: List<AssetVariantRecord>,
    val uploadRequestId: UUID = id,
) {
    init {
        require(ownerType in setOf("tour_media", "category_media")) { "unknown asset owner type" }
        require(
            if (ownerType == "tour_media") {
                runCatching { UUID.fromString(ownerRef).toString() == ownerRef }.getOrDefault(false)
            } else {
                TourCategory.entries.any { it.name == ownerRef }
            },
        ) { "invalid asset owner reference" }
        require(storageKey.matches(STORAGE_KEY_PATTERN)) {
            "invalid generated asset storage key"
        }
        require(storageKey == "assets/$ownerType/$ownerRef/$id.${mimeType.extension}") { "asset key must match its identity" }
        require(originalWidth > 0 && originalHeight > 0) { "dimensions must be positive" }
        require(fileSizeBytes > 0) { "file size must be positive" }
        require(sha256.matches(SHA256_PATTERN)) { "sha256 must be 64 lowercase hex digits" }
        require(variants.map { it.variant }.toSet().size == variants.size) { "duplicate variants" }
        require(
            variants.all {
                it.assetId == id &&
                    it.variant != AssetVariant.BASE &&
                    it.width > 0 &&
                    it.height > 0 &&
                    it.fileSizeBytes > 0 &&
                    it.storageKey == storageKey.replace(".${mimeType.extension}", "_${it.variant.label}.${mimeType.extension}")
            },
        ) { "invalid asset variant" }
    }

    companion object {
        val STORAGE_KEY_PATTERN =
            Regex("^assets/(tour_media/[0-9a-f-]{36}|category_media/(DESERT|SEA|CULTURAL|SHOWS|TRANSFERS))/[0-9a-f-]{36}\\.(jpg|png)$")
        val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

enum class AssetMimeType(
    val headerBytes: ByteArray,
    val extension: String,
) {
    JPEG(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), "jpg"),
    PNG(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()), "png"),
    ;

    companion object {
        /** Detect type from the first bytes of the file — never trust Content-Type or filename. */
        fun detect(header: ByteArray): AssetMimeType? =
            entries.firstOrNull { mime ->
                header.size >= mime.headerBytes.size &&
                    mime.headerBytes.indices.all { i -> header[i] == mime.headerBytes[i] }
            }

        fun fromMimeString(mime: String): AssetMimeType? =
            when (mime) {
                "image/jpeg" -> JPEG
                "image/png" -> PNG
                else -> null
            }
    }
}

/**
 * Cover image for a tour category. Uses the same DRAFT/APPROVED rights model
 * as [TourMedia]: the public site only shows APPROVED category covers.
 */
data class CategoryMedia(
    val category: String,
    val assetId: UUID?,
    val alt: Map<ContentLocale, String>,
    val rightsStatus: MediaRightsStatus,
    val approvedAt: Instant?,
    val approvedByUserId: UUID?,
    val updatedAt: Instant,
    val updatedByUserId: UUID?,
) {
    init {
        require(TourCategory.entries.any { it.name == category }) { "unknown category" }
        require(alt.values.all { it.isNotBlank() && it.length <= 200 }) {
            "alt text must be 1–200 characters"
        }
        require((rightsStatus == MediaRightsStatus.APPROVED) == (approvedAt != null)) {
            "approvedAt must be set if and only if rights are APPROVED"
        }
        require(rightsStatus != MediaRightsStatus.APPROVED || assetId != null) { "approved category must have an asset" }
    }

    /** Fingerprint of the file identity and alt texts — identical to TourMedia.revision semantics. */
    fun revision(storageKey: String): String =
        fingerprint(
            buildString {
                append(storageKey.length).append(':').append(storageKey)
                ContentLocale.entries.forEach { locale ->
                    alt[locale]?.let {
                        append('|')
                            .append(locale.code)
                            .append(':')
                            .append(it.length)
                            .append(':')
                            .append(it)
                    }
                }
            },
        )

    fun approve(
        now: Instant,
        actorUserId: UUID?,
    ): CategoryMedia {
        require(assetId != null) { "category needs an asset before approval" }
        require(alt.containsKey(ContentLocale.EN)) { "category media needs at least English alt text before approval" }
        return copy(
            rightsStatus = MediaRightsStatus.APPROVED,
            approvedAt = now,
            approvedByUserId = actorUserId,
        )
    }

    companion object {
        val CATEGORY_PATTERN = Regex("^[A-Z][A-Z0-9_-]{0,63}$")
    }
}

/** Outcome of a validated binary upload before it is stored as an asset. */
data class ValidatedImage(
    val mimeType: AssetMimeType,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val sha256: String,
    /** Re-encoded bytes: EXIF stripped, orientation applied, quality normalised. */
    val processedBytes: ByteArray,
    /** Derived width-constrained variants. */
    val variants: List<VariantImage>,
)

data class VariantImage(
    val variant: AssetVariant,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
)
