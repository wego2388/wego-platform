package com.wego.toursoperator.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class OpsRegistryTest {
    private val audit = RegistryAudit(1, Instant.EPOCH, null, Instant.EPOCH, null)
    private val day = LocalDate.of(2027, 3, 10)

    private fun driver(
        licence: LocalDate = LocalDate.of(2030, 1, 1),
        active: Boolean = true,
    ) = Driver(UUID.randomUUID(), "Amr", "01028215951", DriverEngagement.PER_TRIP, licence, active, audit)

    private fun vehicle(
        seats: Int = 14,
        active: Boolean = true,
    ) = Vehicle(UUID.randomUUID(), "Hiace", null, VehicleType.VAN, seats, VehicleOwnership.OWNED, null, active, audit)

    private fun supplier(
        tours: Set<UUID> = emptySet(),
        active: Boolean = true,
    ) = Supplier(
        UUID.randomUUID(),
        "S01",
        "Sindbad",
        SupplierServiceType.DIVING_SNORKELING,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        active,
        tours,
        audit,
    )

    @Test
    fun `only the same window overlaps and only neighbouring windows are back to back`() {
        for (a in TimeSlot.entries) {
            for (b in TimeSlot.entries) {
                assertThat(TimeSlotOverlap.overlaps(a, b)).describedAs("$a/$b overlap").isEqualTo(a == b)
                assertThat(TimeSlotOverlap.backToBack(a, b)).describedAs("$a/$b adjacent").isEqualTo(
                    kotlin.math.abs(a.ordinal - b.ordinal) == 1,
                )
            }
        }
        assertThat(TimeSlotOverlap.backToBack(TimeSlot.SUNRISE, TimeSlot.MORNING)).isTrue()
        assertThat(TimeSlotOverlap.backToBack(TimeSlot.SUNRISE, TimeSlot.AFTERNOON)).isFalse()
        assertThat(TimeSlotOverlap.backToBack(TimeSlot.MORNING, TimeSlot.SUNSET)).isFalse()
    }

    @Test
    fun `a licence covers its last valid day and flags the last thirty days`() {
        assertThat(driver(licence = day).licenceCovers(day)).isTrue()
        assertThat(driver(licence = day.minusDays(1)).licenceCovers(day)).isFalse()
        val tour = UUID.randomUUID()

        fun codes(d: Driver) = AssignmentRules.evaluate(day, 2, tour, d, null, emptyList()).map { it.code }
        assertThat(codes(driver(licence = day.minusDays(1)))).containsExactly("driver_licence_expired")
        assertThat(codes(driver(licence = day))).containsExactly("driver_licence_expires_soon")
        assertThat(codes(driver(licence = day.plusDays(29)))).containsExactly("driver_licence_expires_soon")
        assertThat(codes(driver(licence = day.plusDays(30)))).isEmpty()
        assertThat(codes(driver(active = false))).containsExactly("driver_inactive")
        assertThat(AssignmentRules.evaluate(day, 2, tour, driver(licence = day.minusDays(1)), null, emptyList()).single().blocking).isTrue()
        assertThat(AssignmentRules.evaluate(day, 2, tour, driver(licence = day), null, emptyList()).single().blocking).isFalse()
    }

    @Test
    fun `seat shortfall and unlinked suppliers are warnings, inactive resources block`() {
        val tour = UUID.randomUUID()
        val seats = AssignmentRules.evaluate(day, 9, tour, null, vehicle(seats = 4), emptyList()).single()
        assertThat(seats.code).isEqualTo("vehicle_seats_below_guests")
        assertThat(seats.blocking).isFalse()
        assertThat(seats.value).isEqualTo(9)
        assertThat(seats.limit).isEqualTo(4)
        assertThat(AssignmentRules.evaluate(day, 9, tour, null, vehicle(seats = 9), emptyList())).isEmpty()
        assertThat(AssignmentRules.evaluate(day, 0, tour, null, vehicle(seats = 1), emptyList())).isEmpty()
        val issues =
            AssignmentRules.evaluate(
                day,
                1,
                tour,
                null,
                vehicle(active = false),
                listOf(supplier(active = false), supplier(setOf(tour))),
            )
        assertThat(issues.map { it.code }).containsExactlyInAnyOrder("vehicle_inactive", "supplier_inactive", "supplier_not_linked_to_tour")
        assertThat(issues.filter { it.blocking }.map { it.code }).containsExactlyInAnyOrder("vehicle_inactive", "supplier_inactive")
    }

    @Test
    fun `registry values are validated by the domain`() {
        assertThatThrownBy { vehicle(seats = 0) }.hasMessageContaining("seats")
        assertThatThrownBy {
            Vehicle(UUID.randomUUID(), null, null, VehicleType.VAN, 4, VehicleOwnership.OWNED, null, true, audit)
        }.hasMessageContaining("label or a plate")
        assertThatThrownBy {
            Vehicle(UUID.randomUUID(), "x", null, VehicleType.VAN, 4, VehicleOwnership.OWNED, UUID.randomUUID(), true, audit)
        }.hasMessageContaining("lender")
        assertThatThrownBy {
            Driver(
                UUID.randomUUID(),
                " Amr",
                null,
                DriverEngagement.OTHER,
                day,
                true,
                audit,
            )
        }.hasMessageContaining("name")
        assertThatThrownBy {
            Driver(
                UUID.randomUUID(),
                "Amr",
                "phone",
                DriverEngagement.OTHER,
                day,
                true,
                audit,
            )
        }.hasMessageContaining("workPhone")
        assertThatThrownBy {
            Supplier(
                UUID.randomUUID(),
                "bad code",
                "S",
                SupplierServiceType.BOAT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                emptySet(),
                audit,
            )
        }.hasMessageContaining("code")
        assertThatThrownBy {
            Supplier(
                UUID.randomUUID(),
                "S01",
                "S",
                SupplierServiceType.BOAT,
                null,
                null,
                null,
                721,
                null,
                null,
                null,
                null,
                null,
                true,
                emptySet(),
                audit,
            )
        }.hasMessageContaining("noticeHours")
        assertThatThrownBy {
            SlotAssignment(
                TourSlotId.generate(),
                day,
                TimeSlot.MORNING,
                null,
                null,
                emptySet(),
                1,
                Instant.EPOCH,
                null,
                Instant.EPOCH,
                null,
            )
        }.hasMessageContaining("needs")
    }

    @Test
    fun `vehicle display prefers the label and brackets the plate`() {
        assertThat(
            Vehicle(UUID.randomUUID(), "Hiace", "ABC 123", VehicleType.VAN, 14, VehicleOwnership.OWNED, null, true, audit).display,
        ).isEqualTo("Hiace (ABC 123)")
        assertThat(
            Vehicle(UUID.randomUUID(), null, "ABC 123", VehicleType.VAN, 14, VehicleOwnership.OWNED, null, true, audit).display,
        ).isEqualTo("ABC 123")
        assertThat(vehicle().display).isEqualTo("Hiace")
        assertThat(OpsRules.clean("  ")).isNull()
        assertThat(OpsRules.clean(" x ")).isEqualTo("x")
    }
}
