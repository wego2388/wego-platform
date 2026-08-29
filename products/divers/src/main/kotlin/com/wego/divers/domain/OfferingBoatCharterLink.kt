package com.wego.divers.domain

import java.time.Instant

/** At most one per offering — a boat trip runs on one real boat. */
data class OfferingBoatCharterLink(
    val offeringId: OfferingId,
    val boatCharterId: BoatCharterId,
    val linkedAt: Instant,
)
