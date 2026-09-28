package com.wego.toursoperator.domain

enum class CancellationPolicy {
    /** 48 h+ full refund / 24–48 h 50% / <24 h no refund. */
    STANDARD,

    /** Free cancellation up to 24 hours before. */
    FLEXIBLE,

    /** No refund under any circumstances. */
    NON_REFUNDABLE,
}
