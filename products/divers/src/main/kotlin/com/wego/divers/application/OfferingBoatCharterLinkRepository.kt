package com.wego.divers.application

import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.OfferingBoatCharterLink
import com.wego.divers.domain.OfferingId

interface OfferingBoatCharterLinkRepository {
    fun findByOfferingId(offeringId: OfferingId): OfferingBoatCharterLink?

    /** Every offering currently linked to this charter — used to guard a capacity reduction. */
    fun findByBoatCharterId(boatCharterId: BoatCharterId): List<OfferingBoatCharterLink>

    /** Upsert: an offering has at most one link, so re-linking replaces the previous one. */
    fun save(link: OfferingBoatCharterLink)

    fun delete(offeringId: OfferingId)
}
