package com.wego.divers.application

import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.OfferingBoatCharterLink
import com.wego.divers.domain.OfferingId
import java.time.Clock
import java.time.Instant

data class LinkOfferingToCharterCommand(
    val offeringId: OfferingId,
    val boatCharterId: BoatCharterId,
)

sealed interface LinkOfferingToCharterResult {
    data class Linked(
        val link: OfferingBoatCharterLink,
    ) : LinkOfferingToCharterResult

    data object OfferingNotFound : LinkOfferingToCharterResult

    data object CharterNotFound : LinkOfferingToCharterResult

    data object CharterNotActive : LinkOfferingToCharterResult

    /** The real rule this whole registry exists for: a trip can never claim more seats than the boat is licensed for. */
    data object OfferingCapacityExceedsCharter : LinkOfferingToCharterResult

    /** An offering with no fixed capacity has nothing to check against — link it to a real number first. */
    data object OfferingHasNoCapacity : LinkOfferingToCharterResult
}

/**
 * Deliberately not restricted to [OfferingType.DIVE_TRIP][com.wego.divers.domain.OfferingType]/boat-diving
 * offerings — any capacity-bearing offering that genuinely uses a boat (a course's boat day, a
 * multi-day/signature package with a boat leg) can be linked. The real, enforced rule is the capacity
 * check below, not the offering's category; a course or package that never touches a boat simply never
 * gets linked in practice, so no extra type check is needed to keep that true.
 */
class LinkOfferingToCharterService(
    private val offeringRepository: OfferingRepository,
    private val boatCharterRepository: BoatCharterRepository,
    private val linkRepository: OfferingBoatCharterLinkRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun link(command: LinkOfferingToCharterCommand): LinkOfferingToCharterResult =
        transactionRunner.runInTransaction {
            val offering =
                offeringRepository.findById(command.offeringId) ?: return@runInTransaction LinkOfferingToCharterResult.OfferingNotFound
            val charter =
                boatCharterRepository.findByIdForUpdate(command.boatCharterId)
                    ?: return@runInTransaction LinkOfferingToCharterResult.CharterNotFound
            if (!charter.isActive) return@runInTransaction LinkOfferingToCharterResult.CharterNotActive

            val capacity = offering.capacity ?: return@runInTransaction LinkOfferingToCharterResult.OfferingHasNoCapacity
            if (capacity > charter.licensedCapacity) return@runInTransaction LinkOfferingToCharterResult.OfferingCapacityExceedsCharter

            val link = OfferingBoatCharterLink(command.offeringId, command.boatCharterId, Instant.now(clock))
            linkRepository.save(link)
            LinkOfferingToCharterResult.Linked(link)
        }
}
