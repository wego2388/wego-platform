package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorAsset.TOURS_OPERATOR_ASSET
import com.wego.generated.jooq.tables.ToursOperatorAssetVariant.TOURS_OPERATOR_ASSET_VARIANT
import com.wego.generated.jooq.tables.ToursOperatorCategoryMedia.TOURS_OPERATOR_CATEGORY_MEDIA
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourMedia.TOURS_OPERATOR_TOUR_MEDIA
import com.wego.toursoperator.application.AssetRepository
import com.wego.toursoperator.application.CategoryMediaRepository
import com.wego.toursoperator.domain.AssetMimeType
import com.wego.toursoperator.domain.AssetVariant
import com.wego.toursoperator.domain.AssetVariantRecord
import com.wego.toursoperator.domain.CategoryMedia
import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.ManagedAsset
import com.wego.toursoperator.domain.MediaRightsStatus
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository("stoAssetRepositoryImpl")
class JooqAssetRepository(
    private val dsl: DSLContext,
    @Qualifier("stoObjectMapper") private val objectMapper: ObjectMapper,
) : AssetRepository {
    @Transactional
    override fun save(asset: ManagedAsset): ManagedAsset {
        val a = TOURS_OPERATOR_ASSET
        dsl
            .insertInto(a)
            .set(a.ID, asset.id)
            .set(a.OWNER_TYPE, asset.ownerType)
            .set(a.OWNER_REF, asset.ownerRef)
            .set(a.STORAGE_KEY, asset.storageKey)
            .set(a.MIME_TYPE, if (asset.mimeType == AssetMimeType.JPEG) "image/jpeg" else "image/png")
            .set(a.ORIGINAL_WIDTH, asset.originalWidth)
            .set(a.ORIGINAL_HEIGHT, asset.originalHeight)
            .set(a.FILE_SIZE_BYTES, asset.fileSizeBytes)
            .set(a.SHA256, asset.sha256)
            .set(a.UPLOAD_REQUEST_ID, asset.uploadRequestId)
            .execute()

        val v = TOURS_OPERATOR_ASSET_VARIANT
        dsl.deleteFrom(v).where(v.ASSET_ID.eq(asset.id)).execute()
        asset.variants.forEach { variant ->
            dsl
                .insertInto(v)
                .set(v.ASSET_ID, variant.assetId)
                .set(v.VARIANT, variant.variant.label)
                .set(v.STORAGE_KEY, variant.storageKey)
                .set(v.WIDTH, variant.width)
                .set(v.HEIGHT, variant.height)
                .set(v.FILE_SIZE_BYTES, variant.fileSizeBytes)
                .execute()
        }
        return asset
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): ManagedAsset? {
        val a = TOURS_OPERATOR_ASSET
        val record = dsl.selectFrom(a).where(a.ID.eq(id)).fetchOne() ?: return null
        val variants = fetchVariants(id)
        return record.toAsset(variants)
    }

    @Transactional(readOnly = true)
    override fun findByOwner(
        ownerType: String,
        ownerRef: String,
    ): List<ManagedAsset> {
        val a = TOURS_OPERATOR_ASSET
        val records =
            dsl
                .selectFrom(a)
                .where(a.OWNER_TYPE.eq(ownerType), a.OWNER_REF.eq(ownerRef))
                .fetch()
        return records.map { r ->
            r.toAsset(fetchVariants(r.id))
        }
    }

    @Transactional(readOnly = true)
    override fun findByUploadRequest(
        ownerType: String,
        ownerRef: String,
        requestId: UUID,
    ): ManagedAsset? {
        val a = TOURS_OPERATOR_ASSET
        return dsl
            .selectFrom(a)
            .where(a.OWNER_TYPE.eq(ownerType), a.OWNER_REF.eq(ownerRef), a.UPLOAD_REQUEST_ID.eq(requestId))
            .fetchOne()
            ?.let { it.toAsset(fetchVariants(it.id)) }
    }

    @Transactional(readOnly = true)
    override fun isReferenced(assetId: UUID): Boolean {
        val m = TOURS_OPERATOR_TOUR_MEDIA
        val cm = TOURS_OPERATOR_CATEGORY_MEDIA
        val inTour =
            dsl.fetchExists(
                dsl
                    .selectOne()
                    .from(m)
                    .where(m.PATH.like("%$assetId%")),
            )
        val inCategory =
            dsl.fetchExists(
                dsl
                    .selectOne()
                    .from(cm)
                    .where(cm.ASSET_ID.eq(assetId)),
            )
        return inTour || inCategory
    }

    @Transactional(readOnly = true)
    override fun isTourMediaApproved(
        assetId: UUID,
        publicPath: String,
    ): Boolean {
        val m = TOURS_OPERATOR_TOUR_MEDIA
        val t = TOURS_OPERATOR_TOUR
        val asset = findById(assetId) ?: return false
        if (asset.ownerType != "tour_media") return false
        val tourId = UUID.fromString(asset.ownerRef)
        return dsl.fetchExists(
            dsl
                .selectOne()
                .from(m)
                .join(t)
                .on(m.TOUR_ID.eq(t.ID))
                .where(
                    m.TOUR_ID.eq(tourId),
                    m.PATH.eq(publicPath),
                    m.RIGHTS_STATUS.eq(MediaRightsStatus.APPROVED.name),
                    t.IS_ACTIVE.eq(true),
                    t.SLUG.eq(publicPath.split('/')[3]),
                ),
        )
    }

    private fun fetchVariants(assetId: UUID): List<AssetVariantRecord> {
        val v = TOURS_OPERATOR_ASSET_VARIANT
        return dsl.selectFrom(v).where(v.ASSET_ID.eq(assetId)).fetch { r ->
            AssetVariantRecord(
                assetId = r.assetId,
                variant = AssetVariant.fromLabel(r.variant) ?: AssetVariant.BASE,
                storageKey = r.storageKey,
                width = r.width,
                height = r.height,
                fileSizeBytes = r.fileSizeBytes,
            )
        }
    }

    private fun com.wego.generated.jooq.tables.records.ToursOperatorAssetRecord.toAsset(variants: List<AssetVariantRecord>): ManagedAsset =
        ManagedAsset(
            id = this.id,
            ownerType = this.ownerType,
            ownerRef = this.ownerRef,
            storageKey = this.storageKey,
            mimeType = requireNotNull(AssetMimeType.fromMimeString(this.mimeType)),
            originalWidth = this.originalWidth,
            originalHeight = this.originalHeight,
            fileSizeBytes = this.fileSizeBytes,
            sha256 = this.sha256,
            variants = variants,
            uploadRequestId = this.uploadRequestId,
        )
}

@Repository("stoCategoryMediaRepositoryImpl")
class JooqCategoryMediaRepository(
    private val dsl: DSLContext,
    @Qualifier("stoObjectMapper") private val objectMapper: ObjectMapper,
    private val assetRepository: AssetRepository,
) : CategoryMediaRepository {
    @Transactional(readOnly = true)
    override fun findAll(): List<CategoryMedia> {
        val cm = TOURS_OPERATOR_CATEGORY_MEDIA
        return dsl.selectFrom(cm).orderBy(cm.CATEGORY).fetch { it.toDomain() }
    }

    @Transactional(readOnly = true)
    override fun findByCategory(category: String): CategoryMedia? {
        val cm = TOURS_OPERATOR_CATEGORY_MEDIA
        return dsl
            .selectFrom(cm)
            .where(cm.CATEGORY.eq(category))
            .fetchOne()
            ?.toDomain()
    }

    @Transactional
    override fun findByCategoryForUpdate(category: String): CategoryMedia? {
        val cm = TOURS_OPERATOR_CATEGORY_MEDIA
        return dsl
            .selectFrom(cm)
            .where(cm.CATEGORY.eq(category))
            .forUpdate()
            .fetchOne()
            ?.toDomain()
    }

    @Transactional
    override fun save(media: CategoryMedia) {
        val cm = TOURS_OPERATOR_CATEGORY_MEDIA
        val now = toOffset(media.updatedAt)
        dsl
            .insertInto(cm)
            .set(cm.CATEGORY, media.category)
            .set(cm.ASSET_ID, media.assetId)
            .set(cm.ALT, JSON.valueOf(altToJson(media.alt)))
            .set(cm.RIGHTS_STATUS, media.rightsStatus.name)
            .set(cm.APPROVED_AT, media.approvedAt?.let(::toOffset))
            .set(cm.APPROVED_BY_USER_ID, media.approvedByUserId)
            .set(cm.UPDATED_AT, now)
            .set(cm.UPDATED_BY_USER_ID, media.updatedByUserId)
            .onConflict(cm.CATEGORY)
            .doUpdate()
            .set(cm.ASSET_ID, media.assetId)
            .set(cm.ALT, JSON.valueOf(altToJson(media.alt)))
            .set(cm.RIGHTS_STATUS, media.rightsStatus.name)
            .set(cm.APPROVED_AT, media.approvedAt?.let(::toOffset))
            .set(cm.APPROVED_BY_USER_ID, media.approvedByUserId)
            .set(cm.UPDATED_AT, now)
            .set(cm.UPDATED_BY_USER_ID, media.updatedByUserId)
            .execute()
    }

    @Transactional(readOnly = true)
    override fun findApprovedCoverPath(category: String): String? {
        val cm = TOURS_OPERATOR_CATEGORY_MEDIA
        val a = TOURS_OPERATOR_ASSET
        val record =
            dsl
                .selectFrom(cm)
                .where(cm.CATEGORY.eq(category), cm.RIGHTS_STATUS.eq(MediaRightsStatus.APPROVED.name))
                .fetchOne() ?: return null
        val assetId = record.assetId ?: return null
        val asset = dsl.selectFrom(a).where(a.ID.eq(assetId)).fetchOne() ?: return null
        return asset.storageKey
    }

    private fun com.wego.generated.jooq.tables.records.ToursOperatorCategoryMediaRecord.toDomain(): CategoryMedia =
        CategoryMedia(
            category = this.category,
            assetId = this.assetId,
            alt = altFromJson(this.alt),
            rightsStatus = MediaRightsStatus.valueOf(this.rightsStatus),
            approvedAt = this.approvedAt?.toInstant(),
            approvedByUserId = this.approvedByUserId,
            updatedAt = this.updatedAt.toInstant(),
            updatedByUserId = this.updatedByUserId,
        )

    private fun altToJson(alt: Map<ContentLocale, String>): String = objectMapper.writeValueAsString(alt.mapKeys { it.key.code })

    private fun altFromJson(json: JSON?): Map<ContentLocale, String> {
        if (json == null) return emptyMap()
        val map = objectMapper.readValue(json.data(), Map::class.java)
        return map
            .mapNotNull { (code, text) ->
                ContentLocale.fromCode(code.toString())?.let { it to text as String }
            }.toMap()
    }

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
