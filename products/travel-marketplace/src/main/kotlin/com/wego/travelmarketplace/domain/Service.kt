package com.wego.travelmarketplace.domain

import java.time.Instant

/**
 * A bookable experience — the marketplace's core catalog aggregate. Mirrors
 * `com.wego.divers.domain.Diver`/`Offering`'s conventions exactly (mutable
 * status behind a private setter, `init { require(...) }` invariants, a
 * `create()` companion, named guarded lifecycle methods) even though this is
 * a different, isolated product — see WEGO-010-A Packet 0R for why that
 * isolation is real, not just a naming convention.
 *
 * [options] and [media] are full-replace child collections, the same
 * pattern `Diver.certifications` already uses: no separate CRUD endpoint per
 * option/photo, one atomic update replaces the whole list.
 */
class Service(
    val id: ServiceId,
    val categoryId: CategoryId,
    val name: LocalizedText,
    val description: LocalizedText,
    val fulfilmentModel: FulfilmentModel,
    val providerId: ProviderId?,
    val confirmationType: ConfirmationType,
    val cancellationPolicy: LocalizedText,
    val pickupInfo: LocalizedText?,
    val inclusions: LocalizedText?,
    val exclusions: LocalizedText?,
    val options: List<ServiceOption>,
    val media: List<ServiceMedia>,
    status: ServiceStatus,
    val createdAt: Instant,
    publishedAt: Instant?,
    archivedAt: Instant?,
) {
    var status: ServiceStatus = status
        private set

    var publishedAt: Instant? = publishedAt
        private set

    var archivedAt: Instant? = archivedAt
        private set

    init {
        require((fulfilmentModel == FulfilmentModel.PARTNER) == (providerId != null)) {
            "A PARTNER service must have a provider; a DIRECT service must not"
        }
        require((status == ServiceStatus.ARCHIVED) == (archivedAt != null)) {
            "archivedAt must be set if and only if the service is archived"
        }
        // publishedAt is a sticky "first published at" historical marker,
        // not a strict mirror of the current status — a later suspend()
        // deliberately leaves it set, so only the PUBLISHED-without-a-
        // timestamp direction is actually invalid.
        require(status != ServiceStatus.PUBLISHED || publishedAt != null) {
            "publishedAt must be set when the service is currently published"
        }
    }

    val isPublished: Boolean get() = status == ServiceStatus.PUBLISHED

    /** `DRAFT -> REVIEW`. */
    fun submitForReview() {
        require(status == ServiceStatus.DRAFT) { "Only a draft service can be submitted for review" }
        status = ServiceStatus.REVIEW
    }

    /** `REVIEW -> APPROVED`. */
    fun approve() {
        require(status == ServiceStatus.REVIEW) { "Only a service under review can be approved" }
        status = ServiceStatus.APPROVED
    }

    /**
     * `APPROVED -> PUBLISHED`, or `SUSPENDED -> PUBLISHED` (re-publish after
     * a suspension). [hasPublishableOption]/[hasRightsClearedMedia] are
     * computed by the caller from this aggregate's own [options]/[media] —
     * kept as explicit booleans here rather than re-deriving them so this
     * method stays a pure function with no hidden dependency on collection
     * contents beyond what its own signature states. Enforces
     * `SERVICE_CONTENT_TEMPLATE.md`'s closing rule: "price, capacity,
     * cancellation, pickup, operator and media rights are mandatory" before
     * publication — a real domain guard, not a dashboard form hint.
     */
    fun publish(
        hasPublishableOption: Boolean,
        hasRightsClearedMedia: Boolean,
        now: Instant,
    ) {
        require(status == ServiceStatus.APPROVED || status == ServiceStatus.SUSPENDED) {
            "Only an approved or suspended service can be published"
        }
        require(hasPublishableOption) { "A service needs at least one active, priced option before it can publish" }
        require(hasRightsClearedMedia) { "A service needs at least one media asset with rights evidence before it can publish" }
        status = ServiceStatus.PUBLISHED
        publishedAt = now
    }

    /** `PUBLISHED -> SUSPENDED` — pulled from public view without archiving its content or history. */
    fun suspend() {
        require(status == ServiceStatus.PUBLISHED) { "Only a published service can be suspended" }
        status = ServiceStatus.SUSPENDED
    }

    /** Terminal from any non-archived state. An already-archived service cannot be archived again. */
    fun archive(now: Instant) {
        require(status != ServiceStatus.ARCHIVED) { "Service is already archived" }
        status = ServiceStatus.ARCHIVED
        archivedAt = now
    }

    fun withUpdatedDetails(
        categoryId: CategoryId,
        name: LocalizedText,
        description: LocalizedText,
        fulfilmentModel: FulfilmentModel,
        providerId: ProviderId?,
        confirmationType: ConfirmationType,
        cancellationPolicy: LocalizedText,
        pickupInfo: LocalizedText?,
        inclusions: LocalizedText?,
        exclusions: LocalizedText?,
        options: List<ServiceOption>,
        media: List<ServiceMedia>,
    ): Service =
        Service(
            id = id,
            categoryId = categoryId,
            name = name,
            description = description,
            fulfilmentModel = fulfilmentModel,
            providerId = providerId,
            confirmationType = confirmationType,
            cancellationPolicy = cancellationPolicy,
            pickupInfo = pickupInfo,
            inclusions = inclusions,
            exclusions = exclusions,
            options = options,
            media = media,
            status = status,
            createdAt = createdAt,
            publishedAt = publishedAt,
            archivedAt = archivedAt,
        )

    companion object {
        fun create(
            id: ServiceId,
            categoryId: CategoryId,
            name: LocalizedText,
            description: LocalizedText,
            fulfilmentModel: FulfilmentModel,
            providerId: ProviderId?,
            confirmationType: ConfirmationType,
            cancellationPolicy: LocalizedText,
            pickupInfo: LocalizedText?,
            inclusions: LocalizedText?,
            exclusions: LocalizedText?,
            options: List<ServiceOption>,
            media: List<ServiceMedia>,
            now: Instant,
        ): Service =
            Service(
                id = id,
                categoryId = categoryId,
                name = name,
                description = description,
                fulfilmentModel = fulfilmentModel,
                providerId = providerId,
                confirmationType = confirmationType,
                cancellationPolicy = cancellationPolicy,
                pickupInfo = pickupInfo,
                inclusions = inclusions,
                exclusions = exclusions,
                options = options,
                media = media,
                status = ServiceStatus.DRAFT,
                createdAt = now,
                publishedAt = null,
                archivedAt = null,
            )
    }
}
