package com.wego.divers.application

import com.wego.divers.domain.OfferingId

sealed interface UnlinkOfferingFromCharterResult {
    data object Unlinked : UnlinkOfferingFromCharterResult

    data object NoLink : UnlinkOfferingFromCharterResult
}

class UnlinkOfferingFromCharterService(
    private val linkRepository: OfferingBoatCharterLinkRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun unlink(offeringId: OfferingId): UnlinkOfferingFromCharterResult =
        transactionRunner.runInTransaction {
            if (linkRepository.findByOfferingId(offeringId) == null) return@runInTransaction UnlinkOfferingFromCharterResult.NoLink
            linkRepository.delete(offeringId)
            UnlinkOfferingFromCharterResult.Unlinked
        }
}
