package com.wego.toursoperator.domain

enum class CancellationPolicy {
    /** 48 h+ full refund / 24–48 h 50% / <24 h no refund. */
    STANDARD,

    /** Free cancellation up to 24 hours before. */
    FLEXIBLE,

    /** No refund under any circumstances. */
    NON_REFUNDABLE,
}

/**
 * What a cancellation is expected to return, from the tour's policy and how
 * far ahead of the tour the customer cancelled (STANDARD, from the site's
 * terms: 48 h or more full refund, 24–48 h half, under 24 h nothing). It only
 * computes the expectation printed on the money-to-return form; it records and
 * moves no money.
 */
object CancellationRefund {
    private const val FULL_HOURS = 48L
    private const val HALF_HOURS = 24L

    fun percent(
        policy: CancellationPolicy,
        hoursBefore: Long,
    ): Int =
        when (policy) {
            CancellationPolicy.STANDARD ->
                when {
                    hoursBefore >= FULL_HOURS -> 100
                    hoursBefore >= HALF_HOURS -> 50
                    else -> 0
                }
            CancellationPolicy.FLEXIBLE -> if (hoursBefore >= HALF_HOURS) 100 else 0
            CancellationPolicy.NON_REFUNDABLE -> 0
        }
}
