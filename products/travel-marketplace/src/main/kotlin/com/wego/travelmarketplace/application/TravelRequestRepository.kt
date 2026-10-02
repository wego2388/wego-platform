package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestReference
import com.wego.travelmarketplace.domain.TravelRequestStatus

interface TravelRequestRepository {
    fun findById(id: TravelRequestId): TravelRequest?

    fun findByIdForUpdate(id: TravelRequestId): TravelRequest?

    /** Public lookup surface — the only field a customer supplies to check their own request. */
    fun findByReference(reference: TravelRequestReference): TravelRequest?

    /**
     * The idempotency check every create-request call makes first, outside
     * any row lock (there is no row to lock yet on the first attempt) — a
     * unique DB constraint on `idempotency_key` is the actual safety net
     * under concurrent duplicate submissions; this lookup is the fast path
     * that avoids relying on that constraint violation alone.
     */
    fun findByIdempotencyKey(idempotencyKey: String): TravelRequest?

    /** Staff-facing roster, newest first. */
    fun findAll(
        status: TravelRequestStatus?,
        limit: Int,
        offset: Int,
    ): List<TravelRequest>

    /**
     * Every `NEW`/`IN_REVIEW` request whose [TravelRequest.requestedDate] is
     * before [asOfDate] — the experience date already passed without ever
     * being confirmed. Deliberately date-based, not a fixed "N hours since
     * creation" cutoff: a request for three weeks out should not expire
     * just because staff has not looked at it yet within some arbitrary
     * window, and no such window is an owner-approved business rule yet.
     */
    fun findExpirable(asOfDate: java.time.LocalDate): List<TravelRequest>

    fun save(request: TravelRequest)
}
