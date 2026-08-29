package com.wego.divers.domain

import java.time.LocalDate
import java.util.UUID

/** Free-text agency/level — real certifying bodies (PADI, SSI, CMAS, TDI, RAID, national federations) don't fit a closed enum. */
data class DiverCertification(
    val id: UUID,
    val agency: String,
    val level: String,
    val certificationNumber: String?,
    val issuedOn: LocalDate?,
) {
    init {
        require(agency.isNotBlank()) { "Certification agency must not be blank" }
        require(level.isNotBlank()) { "Certification level must not be blank" }
    }
}
