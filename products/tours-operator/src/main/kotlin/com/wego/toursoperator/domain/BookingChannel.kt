package com.wego.toursoperator.domain

/**
 * How a booking entered the system.
 *
 * ONLINE: public checkout. It is paid through the online provider and must be
 * paid within the 30-minute window or it expires.
 * OFFICE: created by staff. The 30-minute window does not apply; it stays NEW
 * until staff cancel it, and what has been paid is tracked by
 * [OfficeCollection] entries, never by the online payment ledger.
 */
enum class BookingChannel {
    ONLINE,
    OFFICE,
}
