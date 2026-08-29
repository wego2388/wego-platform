package com.wego.divers

import com.wego.divers.domain.BoatCharter
import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.CharterStatus
import com.wego.divers.domain.CharterType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class BoatCharterTest {
    private fun create(
        boatName: String = "Barbarossa",
        licensedCapacity: Int = 50,
        startsOn: LocalDate = LocalDate.of(2026, 1, 1),
        endsOn: LocalDate? = null,
    ): BoatCharter =
        BoatCharter.create(
            id = BoatCharterId.generate(),
            boatName = boatName,
            charterType = CharterType.STANDING,
            licensedCapacity = licensedCapacity,
            startsOn = startsOn,
            endsOn = endsOn,
            notes = null,
            createdByUserId = null,
            now = Instant.parse("2026-08-29T00:00:00Z"),
        )

    @Test
    fun `rejects a blank boat name`() {
        assertThatIllegalArgumentException().isThrownBy { create(boatName = "  ") }
    }

    @Test
    fun `rejects a non-positive licensed capacity`() {
        assertThatIllegalArgumentException().isThrownBy { create(licensedCapacity = 0) }
        assertThatIllegalArgumentException().isThrownBy { create(licensedCapacity = -1) }
    }

    @Test
    fun `rejects an end date before the start date`() {
        val startsOn = LocalDate.of(2026, 8, 10)
        assertThatIllegalArgumentException().isThrownBy { create(startsOn = startsOn, endsOn = startsOn.minusDays(1)) }
    }

    @Test
    fun `starts active with no ended timestamp`() {
        val charter = create()
        assertThat(charter.status).isEqualTo(CharterStatus.ACTIVE)
        assertThat(charter.isActive).isTrue()
        assertThat(charter.endedAt).isNull()
    }

    @Test
    fun `ending sets status and timestamp together`() {
        val charter = create()
        val now = Instant.parse("2026-09-01T00:00:00Z")

        charter.end(now)

        assertThat(charter.status).isEqualTo(CharterStatus.ENDED)
        assertThat(charter.isActive).isFalse()
        assertThat(charter.endedAt).isEqualTo(now)
    }

    @Test
    fun `an already-ended charter cannot be ended again`() {
        val charter = create()
        charter.end(Instant.parse("2026-09-01T00:00:00Z"))

        assertThatIllegalArgumentException().isThrownBy { charter.end(Instant.parse("2026-09-02T00:00:00Z")) }
    }

    @Test
    fun `updating details preserves identity, charter type, and lifecycle state`() {
        val charter = create()
        charter.end(Instant.parse("2026-09-01T00:00:00Z"))

        val updated = charter.withUpdatedDetails("Al-Horeya", 40, charter.startsOn, charter.endsOn, "Renegotiated rate")

        assertThat(updated.id).isEqualTo(charter.id)
        assertThat(updated.charterType).isEqualTo(charter.charterType)
        assertThat(updated.status).isEqualTo(CharterStatus.ENDED)
        assertThat(updated.boatName).isEqualTo("Al-Horeya")
        assertThat(updated.licensedCapacity).isEqualTo(40)
    }
}
