package com.wego.toursoperator.application

import com.wego.toursoperator.domain.FxRate
import java.time.LocalDate

/** Append-only: a rate is never edited; a newer row for the same day supersedes it. */
interface FxRateRepository {
    /** The latest rate set for [date], or null when none was set that day. */
    fun latestFor(date: LocalDate): FxRate?

    fun append(rate: FxRate)

    /** Newest first. */
    fun history(limit: Int): List<FxRate>
}
