package com.wego.divers

import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentRentalRecord
import com.wego.divers.domain.EquipmentServiceRecord
import com.wego.divers.domain.EquipmentStatus
import com.wego.divers.domain.EquipmentType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EquipmentServiceRecordTest {
    @Test
    fun `rejects a blank description`() {
        assertThatIllegalArgumentException().isThrownBy {
            EquipmentServiceRecord(UUID.randomUUID(), EquipmentId.generate(), LocalDate.now(), "  ", null, Instant.now())
        }
    }
}

class EquipmentRentalRecordTest {
    @Test
    fun `rejects a blank customer name`() {
        assertThatIllegalArgumentException().isThrownBy {
            EquipmentRentalRecord(UUID.randomUUID(), EquipmentId.generate(), "  ", LocalDate.now(), null, null, Instant.now())
        }
    }

    @Test
    fun `rejects a return date before the rental date`() {
        val rentedOn = LocalDate.of(2026, 8, 10)
        assertThatIllegalArgumentException().isThrownBy {
            EquipmentRentalRecord(UUID.randomUUID(), EquipmentId.generate(), "Ada", rentedOn, rentedOn.minusDays(1), null, Instant.now())
        }
    }

    @Test
    fun `is open when there is no return date and closed once one is set`() {
        val rentedOn = LocalDate.of(2026, 8, 10)
        val open = EquipmentRentalRecord(UUID.randomUUID(), EquipmentId.generate(), "Ada", rentedOn, null, null, Instant.now())
        val closed = open.copy(returnedOn = rentedOn.plusDays(1))

        assertThat(open.isOpen).isTrue()
        assertThat(closed.isOpen).isFalse()
    }
}

class EquipmentTest {
    private fun create(
        label: String = "BCD #12",
        qrCode: String = "QR-BCD-012",
    ): Equipment =
        Equipment.create(
            id = EquipmentId.generate(),
            equipmentType = EquipmentType.BCD,
            label = label,
            qrCode = qrCode,
            itemSize = "M",
            serialNumber = "SN-001",
            createdByUserId = null,
            now = Instant.parse("2026-08-29T00:00:00Z"),
        )

    @Test
    fun `rejects a blank label`() {
        assertThatIllegalArgumentException().isThrownBy { create(label = "  ") }
    }

    @Test
    fun `rejects a blank qr code`() {
        assertThatIllegalArgumentException().isThrownBy { create(qrCode = "  ") }
    }

    @Test
    fun `starts active with no retired timestamp`() {
        val equipment = create()
        assertThat(equipment.status).isEqualTo(EquipmentStatus.ACTIVE)
        assertThat(equipment.isRetired).isFalse()
        assertThat(equipment.retiredAt).isNull()
    }

    @Test
    fun `maintenance cycle moves active to in-maintenance and back`() {
        val equipment = create()

        equipment.startMaintenance()
        assertThat(equipment.status).isEqualTo(EquipmentStatus.IN_MAINTENANCE)

        equipment.completeMaintenance()
        assertThat(equipment.status).isEqualTo(EquipmentStatus.ACTIVE)
    }

    @Test
    fun `cannot start maintenance on equipment already in maintenance`() {
        val equipment = create()
        equipment.startMaintenance()

        assertThatIllegalArgumentException().isThrownBy { equipment.startMaintenance() }
    }

    @Test
    fun `cannot complete maintenance on equipment that is not in maintenance`() {
        val equipment = create()

        assertThatIllegalArgumentException().isThrownBy { equipment.completeMaintenance() }
    }

    @Test
    fun `retiring sets status and timestamp together, from either active or in-maintenance`() {
        val now = Instant.parse("2026-09-01T00:00:00Z")
        val equipment = create()

        equipment.retire(now)

        assertThat(equipment.status).isEqualTo(EquipmentStatus.RETIRED)
        assertThat(equipment.isRetired).isTrue()
        assertThat(equipment.retiredAt).isEqualTo(now)
    }

    @Test
    fun `an already-retired item cannot be retired again`() {
        val equipment = create()
        equipment.retire(Instant.parse("2026-09-01T00:00:00Z"))

        assertThatIllegalArgumentException().isThrownBy { equipment.retire(Instant.parse("2026-09-02T00:00:00Z")) }
    }

    @Test
    fun `updating details preserves identity, qr code, and lifecycle state`() {
        val equipment = create()
        equipment.startMaintenance()

        val updated = equipment.withUpdatedDetails("BCD #12 (relabelled)", "L", "SN-002")

        assertThat(updated.id).isEqualTo(equipment.id)
        assertThat(updated.qrCode).isEqualTo(equipment.qrCode)
        assertThat(updated.status).isEqualTo(EquipmentStatus.IN_MAINTENANCE)
        assertThat(updated.label).isEqualTo("BCD #12 (relabelled)")
        assertThat(updated.itemSize).isEqualTo("L")
        assertThat(updated.serialNumber).isEqualTo("SN-002")
    }
}
