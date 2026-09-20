package com.wego.travelmarketplace

import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.ConfirmationType
import com.wego.travelmarketplace.domain.FulfilmentModel
import com.wego.travelmarketplace.domain.LocalizedText
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.Provider
import com.wego.travelmarketplace.domain.ProviderId
import com.wego.travelmarketplace.domain.ProviderStatus
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceMedia
import com.wego.travelmarketplace.domain.ServiceOption
import com.wego.travelmarketplace.domain.ServiceStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private val NOW: Instant = Instant.parse("2026-09-02T00:00:00Z")

class LocalizedTextTest {
    @Test
    fun `rejects a blank English variant`() {
        assertThatIllegalArgumentException().isThrownBy { LocalizedText(en = "  ", ar = "نص") }
    }

    @Test
    fun `rejects a blank Arabic variant`() {
        assertThatIllegalArgumentException().isThrownBy { LocalizedText(en = "text", ar = "  ") }
    }
}

class ProviderTest {
    private fun create(
        name: String = "Blue Horizon Diving",
        contactEmail: String? = "ops@example.com",
        contactPhone: String? = null,
    ): Provider = Provider.create(ProviderId.generate(), name, contactEmail, contactPhone, NOW)

    @Test
    fun `rejects a blank name`() {
        assertThatIllegalArgumentException().isThrownBy { create(name = "  ") }
    }

    @Test
    fun `rejects a provider with neither email nor phone`() {
        assertThatIllegalArgumentException().isThrownBy { create(contactEmail = null, contactPhone = null) }
    }

    @Test
    fun `starts active with no archived timestamp`() {
        val provider = create()
        assertThat(provider.status).isEqualTo(ProviderStatus.ACTIVE)
        assertThat(provider.isActive).isTrue()
        assertThat(provider.archivedAt).isNull()
    }

    @Test
    fun `an already-archived provider cannot be archived again`() {
        val provider = create()
        provider.archive(NOW)

        assertThatIllegalArgumentException().isThrownBy { provider.archive(NOW.plusSeconds(1)) }
    }
}

class CategoryTest {
    private fun create(code: String = "sea-adventures"): Category =
        Category.create(
            id = CategoryId.generate(),
            code = code,
            name = LocalizedText("Sea adventures", "مغامرات بحرية"),
            description = null,
            displayOrder = 0,
            now = NOW,
        )

    @Test
    fun `rejects a non-kebab-case code`() {
        assertThatIllegalArgumentException().isThrownBy { create(code = "Sea Adventures") }
    }

    @Test
    fun `rejects a negative display order`() {
        assertThatIllegalArgumentException().isThrownBy {
            Category.create(CategoryId.generate(), "sea-adventures", LocalizedText("Sea adventures", "مغامرات بحرية"), null, -1, NOW)
        }
    }

    @Test
    fun `an already-archived category cannot be archived again`() {
        val category = create()
        category.archive(NOW)

        assertThatIllegalArgumentException().isThrownBy { category.archive(NOW.plusSeconds(1)) }
    }
}

class ServiceTest {
    private fun option(price: String = "50.00"): ServiceOption =
        ServiceOption(
            id = UUID.randomUUID(),
            label = LocalizedText("2-hour trip", "رحلة ساعتين"),
            durationMinutes = 120,
            maxParticipants = 10,
            price = Money(BigDecimal(price), "EGP"),
            priceBasis = PriceBasis.PER_PERSON,
        )

    private fun media(): ServiceMedia =
        ServiceMedia(
            id = UUID.randomUUID(),
            assetReference = "asset-001",
            rightsEvidence = "Owner-supplied photo, rights confirmed 2026-08-01",
            locale = "en",
        )

    private fun create(
        fulfilmentModel: FulfilmentModel = FulfilmentModel.DIRECT,
        providerId: ProviderId? = null,
        options: List<ServiceOption> = listOf(option()),
        media: List<ServiceMedia> = listOf(media()),
    ): Service =
        Service.create(
            id = ServiceId.generate(),
            categoryId = CategoryId.generate(),
            name = LocalizedText("Desert safari", "رحلة سفاري"),
            description = LocalizedText("An evening desert safari.", "رحلة سفاري مسائية."),
            fulfilmentModel = fulfilmentModel,
            providerId = providerId,
            confirmationType = ConfirmationType.INSTANT,
            cancellationPolicy = LocalizedText("Free cancellation 24h ahead.", "إلغاء مجاني قبل 24 ساعة."),
            pickupInfo = null,
            inclusions = null,
            exclusions = null,
            options = options,
            media = media,
            now = NOW,
        )

    @Test
    fun `a PARTNER service must have a provider`() {
        assertThatIllegalArgumentException().isThrownBy {
            create(fulfilmentModel = FulfilmentModel.PARTNER, providerId = null)
        }
    }

    @Test
    fun `a DIRECT service must not have a provider`() {
        assertThatIllegalArgumentException().isThrownBy {
            create(fulfilmentModel = FulfilmentModel.DIRECT, providerId = ProviderId.generate())
        }
    }

    @Test
    fun `starts in DRAFT with no published or archived timestamp`() {
        val service = create()
        assertThat(service.status).isEqualTo(ServiceStatus.DRAFT)
        assertThat(service.publishedAt).isNull()
        assertThat(service.archivedAt).isNull()
        assertThat(service.isPublished).isFalse()
    }

    @Test
    fun `the full happy path reaches PUBLISHED through review and approval`() {
        val service = create()

        service.submitForReview()
        assertThat(service.status).isEqualTo(ServiceStatus.REVIEW)

        service.approve()
        assertThat(service.status).isEqualTo(ServiceStatus.APPROVED)

        service.publish(hasPublishableOption = true, hasRightsClearedMedia = true, now = NOW)
        assertThat(service.status).isEqualTo(ServiceStatus.PUBLISHED)
        assertThat(service.isPublished).isTrue()
        assertThat(service.publishedAt).isEqualTo(NOW)
    }

    @Test
    fun `cannot skip review to publish directly from DRAFT`() {
        val service = create()
        assertThatIllegalArgumentException().isThrownBy {
            service.publish(hasPublishableOption = true, hasRightsClearedMedia = true, now = NOW)
        }
    }

    @Test
    fun `cannot approve a draft that was never submitted for review`() {
        val service = create()
        assertThatIllegalArgumentException().isThrownBy { service.approve() }
    }

    @Test
    fun `publishing without a publishable option is rejected even once approved`() {
        val service = create()
        service.submitForReview()
        service.approve()

        assertThatIllegalArgumentException().isThrownBy {
            service.publish(hasPublishableOption = false, hasRightsClearedMedia = true, now = NOW)
        }
    }

    @Test
    fun `publishing without rights-cleared media is rejected even once approved`() {
        val service = create()
        service.submitForReview()
        service.approve()

        assertThatIllegalArgumentException().isThrownBy {
            service.publish(hasPublishableOption = true, hasRightsClearedMedia = false, now = NOW)
        }
    }

    @Test
    fun `suspending and republishing a service keeps its original publishedAt as a sticky marker`() {
        val service = create()
        service.submitForReview()
        service.approve()
        service.publish(hasPublishableOption = true, hasRightsClearedMedia = true, now = NOW)
        val firstPublishedAt = service.publishedAt

        service.suspend()
        assertThat(service.status).isEqualTo(ServiceStatus.SUSPENDED)
        assertThat(service.publishedAt).isEqualTo(firstPublishedAt)

        service.publish(hasPublishableOption = true, hasRightsClearedMedia = true, now = NOW.plusSeconds(60))
        assertThat(service.status).isEqualTo(ServiceStatus.PUBLISHED)
    }

    @Test
    fun `cannot suspend a service that is not currently published`() {
        val service = create()
        assertThatIllegalArgumentException().isThrownBy { service.suspend() }
    }

    @Test
    fun `archiving is terminal from any non-archived state`() {
        val service = create()
        service.archive(NOW)

        assertThat(service.status).isEqualTo(ServiceStatus.ARCHIVED)
        assertThatIllegalArgumentException().isThrownBy { service.archive(NOW.plusSeconds(1)) }
    }

    @Test
    fun `updating details preserves identity, status, and creation metadata`() {
        val service = create()
        val newOption = option(price = "75.00")

        val updated =
            service.withUpdatedDetails(
                categoryId = service.categoryId,
                name = LocalizedText("Desert safari (updated)", "رحلة سفاري (محدثة)"),
                description = service.description,
                fulfilmentModel = service.fulfilmentModel,
                providerId = service.providerId,
                confirmationType = service.confirmationType,
                cancellationPolicy = service.cancellationPolicy,
                pickupInfo = service.pickupInfo,
                inclusions = service.inclusions,
                exclusions = service.exclusions,
                options = listOf(newOption),
                media = service.media,
            )

        assertThat(updated.id).isEqualTo(service.id)
        assertThat(updated.status).isEqualTo(service.status)
        assertThat(updated.createdAt).isEqualTo(service.createdAt)
        assertThat(updated.options).containsExactly(newOption)
    }
}
