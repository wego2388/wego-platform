package com.wego.toursoperator.domain

enum class PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,

    /** Provider reports money captured but the booking can no longer be safely confirmed. */
    REVIEW_REQUIRED,

    /** Provider initiation outcome is ambiguous; automatic retry is forbidden until reconciled. */
    RECONCILIATION_REQUIRED,
}
