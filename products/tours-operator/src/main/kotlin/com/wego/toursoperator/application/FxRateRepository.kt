package com.wego.toursoperator.application

import com.wego.toursoperator.domain.FxRate
import java.time.LocalDate

/** Append-only: a rate is never edited; a newer row for the same day supersedes it. */
interface FxRateRepository {
    /** The latest rate set for [date], or null when none was set that day. */
    fun latestFor(date: LocalDate): FxRate?

    /** The latest rate of the most recent day before [date] that has one (the previous day's rate). */
    fun latestBefore(date: LocalDate): FxRate? = null

    fun append(rate: FxRate)

    /** Newest first. */
    fun history(limit: Int): List<FxRate>
}
