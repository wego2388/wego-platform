package com.wego.divers.api

import com.wego.divers.application.BookingQueryService
import com.wego.divers.application.DiverQueryService
import com.wego.divers.application.EquipmentQueryService
import com.wego.divers.application.OfferingQueryService
import com.wego.divers.domain.EquipmentStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Real business KPIs for a super-admin/operations dashboard, replacing the
 * previously bare "product-neutral shell" landing page. One endpoint per
 * existing module's own permission — not one combined endpoint — so a
 * caller only ever sees the sections their real role already grants them,
 * enforced server-side the same way every other read in this module is,
 * not just hidden client-side.
 */
@RestController
@RequestMapping("/api/v1/divers/dashboard")
class DashboardController(
    private val bookingQueryService: BookingQueryService,
    private val offeringQueryService: OfferingQueryService,
    private val diverQueryService: DiverQueryService,
    private val equipmentQueryService: EquipmentQueryService,
) {
    @GetMapping("/bookings")
    @PreAuthorize("hasAuthority('booking:view')")
    fun bookings(): BookingsDashboardResponse =
        BookingsDashboardResponse(
            bookingsToday = bookingQueryService.countCreatedToday(),
            paidRevenueThisMonth = bookingQueryService.paidRevenueThisMonth().map { MoneyDto(it.amount.toPlainString(), it.currencyCode) },
        )

    @GetMapping("/offerings")
    @PreAuthorize("hasAuthority('offering:view')")
    fun offerings(): OfferingsDashboardResponse =
        OfferingsDashboardResponse(
            upcomingTrips =
                offeringQueryService.listUpcoming().map {
                    UpcomingOfferingResponse(id = it.id.value, offeringType = it.offeringType, title = it.title, startsOn = it.startsOn)
                },
        )

    @GetMapping("/divers")
    @PreAuthorize("hasAuthority('diver:view')")
    fun divers(): DiversDashboardResponse = DiversDashboardResponse(activeDivers = diverQueryService.countActive())

    @GetMapping("/equipment")
    @PreAuthorize("hasAuthority('equipment:view')")
    fun equipment(): EquipmentDashboardResponse {
        val breakdown = equipmentQueryService.statusBreakdown()
        return EquipmentDashboardResponse(
            active = breakdown[EquipmentStatus.ACTIVE] ?: 0,
            inMaintenance = breakdown[EquipmentStatus.IN_MAINTENANCE] ?: 0,
            retired = breakdown[EquipmentStatus.RETIRED] ?: 0,
        )
    }
}
