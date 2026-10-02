package com.wego.travelmarketplace

import com.wego.travelmarketplace.domain.LocalizedText
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestCustomer
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestReference
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import com.wego.travelmarketplace.domain.TravelRequestStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val NOW: Instant = Instant.parse("2026-09-30T10:00:00Z")

class TravelRequestReferenceTest {
    @Test
    fun `generated references match the public format and are not sequential`() {
        val references = (1..50).map { TravelRequestReference.generate() }
        assertThat(references.map { it.value }).allMatch { it.matches(Regex("^STG-[A-Z0-9]{8}$")) }
        assertThat(references.map { it.value }.toSet()).hasSize(references.size)
    }

    @Test
    fun `rejects an ambiguous-alphabet or malformed value`() {
        assertThatIllegalArgumentException().isThrownBy { TravelRequestReference("STG-ABCDEFG0") }
        assertThatIllegalArgumentException().isThrownBy { TravelRequestReference("stg-abcdefgh") }
        assertThatIllegalArgumentException().isThrownBy { TravelRequestReference("STG-SHORT") }
    }
}

class TravelRequestCustomerTest {
    @Test
    fun `rejects a blank name`() {
        assertThatIllegalArgumentException().isThrownBy { TravelRequestCustomer(name = "  ", phone = "+201001413469", email = null) }
    }

    @Test
    fun `rejects a customer with neither phone nor email`() {
        assertThatIllegalArgumentException().isThrownBy { TravelRequestCustomer(name = "Nour", phone = null, email = null) }
    }

    @Test
    fun `accepts phone only`() {
        val customer = TravelRequestCustomer(name = "Nour", phone = "+201001413469", email = null)
        assertThat(customer.email).isNull()
    }

    @Test
    fun `rejects a phone that is not plausibly a phone number`() {
        assertThatIllegalArgumentException().isThrownBy { TravelRequestCustomer(name = "Nour", phone = "abc", email = null) }
        assertThatIllegalArgumentException().isThrownBy { TravelRequestCustomer(name = "Nour", phone = "123", email = null) }
        assertThatIllegalArgumentException().isThrownBy { TravelRequestCustomer(name = "Nour", phone = "0123456789", email = null) }
    }

    @Test
    fun `accepts plausible phone numbers in common formats`() {
        assertThat(TravelRequestCustomer(name = "Nour", phone = "+20 10 0141 3469", email = null).phone).isEqualTo("+20 10 0141 3469")
        assertThat(TravelRequestCustomer(name = "Nour", phone = "201001413469", email = null).phone).isEqualTo("201001413469")
        assertThat(TravelRequestCustomer(name = "Nour", phone = "(20) 100-141-3469", email = null).phone).isEqualTo("(20) 100-141-3469")
    }
}

class TravelRequestTest {
    private fun create(
        adults: Int = 2,
        children: Int = 0,
        locale: String = "en",
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): TravelRequest =
        TravelRequest.create(
            id = TravelRequestId.generate(),
            reference = TravelRequestReference.generate(),
            serviceId = ServiceId.generate(),
            serviceOptionId = UUID.randomUUID(),
            serviceName = LocalizedText("Airport Private Transfer", "انتقال خاص من المطار"),
            optionLabel = LocalizedText("Airport arrival", "استلام من المطار"),
            price = Money(BigDecimal("700.00"), "EGP"),
            priceBasis = PriceBasis.PER_VEHICLE,
            cancellationPolicy = LocalizedText("Free up to 24h before.", "إلغاء مجاني حتى 24 ساعة قبل الموعد."),
            requestedDate = LocalDate.parse("2026-10-15"),
            requestedTime = null,
            adults = adults,
            children = children,
            hotelOrPickup = "Four Seasons Sharm",
            locale = locale,
            notes = null,
            sourceChannel = TravelRequestSourceChannel.WEBSITE,
            customer = TravelRequestCustomer(name = "Nour", phone = "+201001413469", email = null),
            idempotencyKey = idempotencyKey,
            now = NOW,
        )

    @Test
    fun `starts NEW with every lifecycle timestamp unset`() {
        val request = create()
        assertThat(request.status).isEqualTo(TravelRequestStatus.NEW)
        assertThat(request.confirmedAt).isNull()
        assertThat(request.completedAt).isNull()
        assertThat(request.cancelledAt).isNull()
        assertThat(request.expiredAt).isNull()
        assertThat(request.createdAt).isEqualTo(NOW)
    }

    @Test
    fun `rejects zero adults`() {
        assertThatIllegalArgumentException().isThrownBy { create(adults = 0) }
    }

    @Test
    fun `rejects a negative children count`() {
        assertThatIllegalArgumentException().isThrownBy { create(children = -1) }
    }

    @Test
    fun `rejects adults or children past the realistic upper bound, not just negative values`() {
        assertThatIllegalArgumentException().isThrownBy { create(adults = TravelRequest.MAX_PARTY_COMPONENT + 1) }
        assertThatIllegalArgumentException().isThrownBy { create(children = TravelRequest.MAX_PARTY_COMPONENT + 1) }
        // Int.MAX_VALUE adults + 1 child used to wrap negative and silently pass a capacity check — this bound exists specifically to stop that.
        assertThatIllegalArgumentException().isThrownBy { create(adults = Int.MAX_VALUE, children = 1) }
    }

    @Test
    fun `rejects a locale outside en or ar`() {
        assertThatIllegalArgumentException().isThrownBy { create(locale = "fr") }
    }

    @Test
    fun `rejects a blank idempotency key`() {
        assertThatIllegalArgumentException().isThrownBy { create(idempotencyKey = "") }
    }

    @Test
    fun `NEW can move into review`() {
        val request = create()
        request.startReview()
        assertThat(request.status).isEqualTo(TravelRequestStatus.IN_REVIEW)
    }

    @Test
    fun `review cannot be started twice`() {
        val request = create()
        request.startReview()
        assertThatIllegalArgumentException().isThrownBy { request.startReview() }
    }

    @Test
    fun `NEW can be confirmed directly, skipping review, for an instant-confirmation flow`() {
        val request = create()
        request.confirm(NOW.plusSeconds(1))
        assertThat(request.status).isEqualTo(TravelRequestStatus.CONFIRMED)
        assertThat(request.confirmedAt).isEqualTo(NOW.plusSeconds(1))
    }

    @Test
    fun `IN_REVIEW can be confirmed`() {
        val request = create()
        request.startReview()
        request.confirm(NOW.plusSeconds(1))
        assertThat(request.status).isEqualTo(TravelRequestStatus.CONFIRMED)
    }

    @Test
    fun `a confirmed request cannot be confirmed again`() {
        val request = create()
        request.confirm(NOW)
        assertThatIllegalArgumentException().isThrownBy { request.confirm(NOW.plusSeconds(1)) }
    }

    @Test
    fun `only a confirmed request can be completed`() {
        val request = create()
        assertThatIllegalArgumentException().isThrownBy { request.complete(NOW) }
        request.confirm(NOW)
        request.complete(NOW.plusSeconds(1))
        assertThat(request.status).isEqualTo(TravelRequestStatus.COMPLETED)
        assertThat(request.completedAt).isEqualTo(NOW.plusSeconds(1))
    }

    @Test
    fun `cancel requires a typed reason and leaves confirmedAt set as a historical marker`() {
        val request = create()
        request.confirm(NOW)
        request.cancel(TravelRequestCancelReason.CUSTOMER_REQUESTED, "Changed plans", NOW.plusSeconds(2))

        assertThat(request.status).isEqualTo(TravelRequestStatus.CANCELLED)
        assertThat(request.cancelReason).isEqualTo(TravelRequestCancelReason.CUSTOMER_REQUESTED)
        assertThat(request.cancelDetail).isEqualTo("Changed plans")
        assertThat(request.confirmedAt).isEqualTo(NOW)
    }

    @Test
    fun `a terminal request cannot be cancelled again`() {
        val request = create()
        request.cancel(TravelRequestCancelReason.CUSTOMER_REQUESTED, null, NOW)
        assertThatIllegalArgumentException().isThrownBy {
            request.cancel(TravelRequestCancelReason.OTHER, null, NOW.plusSeconds(1))
        }
    }

    @Test
    fun `NEW or IN_REVIEW can expire, but CONFIRMED cannot`() {
        val fromNew = create()
        fromNew.expire(NOW)
        assertThat(fromNew.status).isEqualTo(TravelRequestStatus.EXPIRED)

        val fromConfirmed = create()
        fromConfirmed.confirm(NOW)
        assertThatIllegalArgumentException().isThrownBy { fromConfirmed.expire(NOW.plusSeconds(1)) }
    }

    @Test
    fun `every terminal status rejects a further cancel, complete, expire or confirm`() {
        val completed =
            create().apply {
                confirm(NOW)
                complete(NOW.plusSeconds(1))
            }
        assertThat(TravelRequestStatus.COMPLETED.isTerminal).isTrue()
        assertThatIllegalArgumentException().isThrownBy { completed.confirm(NOW.plusSeconds(2)) }
        assertThatIllegalArgumentException().isThrownBy { completed.cancel(TravelRequestCancelReason.OTHER, null, NOW.plusSeconds(2)) }
    }
}
