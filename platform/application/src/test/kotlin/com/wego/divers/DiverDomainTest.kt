package com.wego.divers

import com.wego.divers.domain.Diver
import com.wego.divers.domain.DiverCertification
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.DiverStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DiverCertificationTest {
    @Test
    fun `rejects a blank agency`() {
        assertThatIllegalArgumentException().isThrownBy {
            DiverCertification(UUID.randomUUID(), "  ", "Open Water Diver", null, null)
        }
    }

    @Test
    fun `rejects a blank level`() {
        assertThatIllegalArgumentException().isThrownBy {
            DiverCertification(UUID.randomUUID(), "PADI", "  ", null, null)
        }
    }
}

class DiverTest {
    private fun create(
        fullName: String = "Ada Lovelace",
        email: String? = "ada@example.com",
        phone: String? = null,
        totalLoggedDives: Int = 0,
        maxDepthMeters: BigDecimal? = null,
        certifications: List<DiverCertification> = emptyList(),
        medicalNotes: String? = null,
    ): Diver =
        Diver.create(
            id = DiverId.generate(),
            fullName = fullName,
            nationality = "British",
            primaryLanguage = "English",
            email = email,
            phone = phone,
            emergencyContactName = "Anna Isaacs",
            emergencyContactPhone = "+201066461010",
            medicalNotes = medicalNotes,
            totalLoggedDives = totalLoggedDives,
            maxDepthMeters = maxDepthMeters,
            lastDiveOn = LocalDate.of(2026, 1, 1),
            bcdSize = "M",
            finSize = "42",
            wetsuitSize = "L",
            certifications = certifications,
            createdByUserId = null,
            now = Instant.parse("2026-08-29T00:00:00Z"),
        )

    @Test
    fun `rejects a blank full name`() {
        assertThatIllegalArgumentException().isThrownBy { create(fullName = "   ") }
    }

    @Test
    fun `rejects a diver with neither email nor phone`() {
        assertThatIllegalArgumentException().isThrownBy { create(email = null, phone = null) }
    }

    @Test
    fun `accepts a diver with only a phone number`() {
        assertThat(create(email = null, phone = "+201066461010").phone).isEqualTo("+201066461010")
    }

    @Test
    fun `rejects negative total logged dives`() {
        assertThatIllegalArgumentException().isThrownBy { create(totalLoggedDives = -1) }
    }

    @Test
    fun `rejects a negative max depth`() {
        assertThatIllegalArgumentException().isThrownBy { create(maxDepthMeters = BigDecimal("-1.0")) }
    }

    @Test
    fun `starts active with no archived timestamp`() {
        val diver = create()
        assertThat(diver.status).isEqualTo(DiverStatus.ACTIVE)
        assertThat(diver.isActive).isTrue()
        assertThat(diver.archivedAt).isNull()
    }

    @Test
    fun `archiving sets status and timestamp together`() {
        val diver = create()
        val now = Instant.parse("2026-09-01T00:00:00Z")

        diver.archive(now)

        assertThat(diver.status).isEqualTo(DiverStatus.ARCHIVED)
        assertThat(diver.isActive).isFalse()
        assertThat(diver.archivedAt).isEqualTo(now)
    }

    @Test
    fun `archiving redacts emergency contact and medical notes`() {
        val diver = create(medicalNotes = "Mild penicillin allergy")
        assertThat(diver.medicalNotes).isEqualTo("Mild penicillin allergy")
        assertThat(diver.emergencyContactName).isEqualTo("Anna Isaacs")

        diver.archive(Instant.parse("2026-09-01T00:00:00Z"))

        assertThat(diver.emergencyContactName).isNull()
        assertThat(diver.emergencyContactPhone).isNull()
        assertThat(diver.medicalNotes).isNull()
    }

    @Test
    fun `an already-archived diver cannot be archived again`() {
        val diver = create()
        diver.archive(Instant.parse("2026-09-01T00:00:00Z"))

        assertThatIllegalArgumentException().isThrownBy { diver.archive(Instant.parse("2026-09-02T00:00:00Z")) }
    }

    @Test
    fun `updating the profile preserves identity, status, and creation metadata`() {
        val diver = create()
        val certification = DiverCertification(UUID.randomUUID(), "PADI", "Advanced Open Water", "AOW-12345", LocalDate.of(2024, 5, 1))

        val updated =
            diver.withUpdatedProfile(
                fullName = "Ada K. Lovelace",
                nationality = diver.nationality,
                primaryLanguage = diver.primaryLanguage,
                email = diver.email,
                phone = diver.phone,
                emergencyContactName = diver.emergencyContactName,
                emergencyContactPhone = diver.emergencyContactPhone,
                medicalNotes = "No known conditions",
                totalLoggedDives = 42,
                maxDepthMeters = BigDecimal("30.0"),
                lastDiveOn = LocalDate.of(2026, 8, 1),
                bcdSize = diver.bcdSize,
                finSize = diver.finSize,
                wetsuitSize = diver.wetsuitSize,
                certifications = listOf(certification),
            )

        assertThat(updated.id).isEqualTo(diver.id)
        assertThat(updated.status).isEqualTo(diver.status)
        assertThat(updated.createdAt).isEqualTo(diver.createdAt)
        assertThat(updated.fullName).isEqualTo("Ada K. Lovelace")
        assertThat(updated.totalLoggedDives).isEqualTo(42)
        assertThat(updated.certifications).containsExactly(certification)
    }
}
