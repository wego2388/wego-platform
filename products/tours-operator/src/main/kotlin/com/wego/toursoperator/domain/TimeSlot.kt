package com.wego.toursoperator.domain

/**
 * The four available departure windows for any tour slot.
 * The exact clock time for each window is tour-specific and stored
 * in the tour's i18n schedule content — not encoded here.
 */

/**
 * [assumedDepartureHour] is the owner-approved departure hour (Cairo) used to
 * count cancellation hours before a tour (2026-10-07): sunrise 04:00,
 * morning 07:00, afternoon 13:00, sunset 15:00.
 */
enum class TimeSlot(
    val assumedDepartureHour: Int,
) {
    SUNRISE(4),
    MORNING(7),
    AFTERNOON(13),
    SUNSET(15),
}
