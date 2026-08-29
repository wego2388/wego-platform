package com.wego.divers.domain

enum class CharterType {
    /** An ongoing agreement (Barbarossa, Al-Horeya) — not tied to one date. */
    STANDING,

    /** A single day's charter, booked as work requires. */
    DAILY,

    /** A multi-day dive safari charter. */
    SAFARI,
}
