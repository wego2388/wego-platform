package com.wego.divers.application

import com.wego.divers.domain.BoatCharter
import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.CharterStatus
import com.wego.divers.domain.CharterType

interface BoatCharterRepository {
    fun findById(id: BoatCharterId): BoatCharter?

    /** Row-locked read for a read-modify-write cycle — see JooqOfferingRepository.findByIdForUpdate for the established pattern. */
    fun findByIdForUpdate(id: BoatCharterId): BoatCharter?

    fun findAll(
        charterType: CharterType?,
        status: CharterStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<BoatCharter>

    fun save(charter: BoatCharter)
}
