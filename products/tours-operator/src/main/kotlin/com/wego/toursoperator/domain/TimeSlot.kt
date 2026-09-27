package com.wego.toursoperator.domain

/**
 * The four available departure windows for any tour slot.
 * The exact clock time for each window is tour-specific and stored
 * in the tour's i18n schedule content — not encoded here.
 */
enum class TimeSlot {
    SUNRISE,
    MORNING,
    AFTERNOON,
    SUNSET,
}
