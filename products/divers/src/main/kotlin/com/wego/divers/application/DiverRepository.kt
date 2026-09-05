package com.wego.divers.application

import com.wego.divers.domain.Diver
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.DiverStatus

interface DiverRepository {
    fun findById(id: DiverId): Diver?

    /** Row-locked read for a read-modify-write cycle — see JooqOfferingRepository.findByIdForUpdate for the established pattern. */
    fun findByIdForUpdate(id: DiverId): Diver?

    fun findAll(
        status: DiverStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Diver>

    /** A real `COUNT(*)` — not `findAll(...).size` against a paginated scan — for dashboard-shaped tallies. */
    fun countByStatus(status: DiverStatus): Int

    /** Also replaces the diver's full certification set — a diver profile has no other concurrent writer to race against. */
    fun save(diver: Diver)
}
