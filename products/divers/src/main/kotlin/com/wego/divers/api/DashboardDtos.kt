package com.wego.divers.api

import com.wego.divers.domain.OfferingType
import java.time.LocalDate
import java.util.UUID

data class BookingsDashboardResponse(
    val bookingsToday: Int,
    val paidRevenueThisMonth: List<MoneyDto>,
)

data class UpcomingOfferingResponse(
    val id: UUID,
    val offeringType: OfferingType,
    val title: String,
    val startsOn: LocalDate,
)

data class OfferingsDashboardResponse(
    val upcomingTrips: List<UpcomingOfferingResponse>,
)

data class DiversDashboardResponse(
    val activeDivers: Int,
)

data class EquipmentDashboardResponse(
    val active: Int,
    val inMaintenance: Int,
    val retired: Int,
)
