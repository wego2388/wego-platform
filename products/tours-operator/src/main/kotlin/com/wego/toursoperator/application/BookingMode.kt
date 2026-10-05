package com.wego.toursoperator.application

/** Deployment capability, not an emergency switch and not a second booking authority. */
enum class BookingMode {
    ONLINE_PAYMENT,
    ENQUIRY_ONLY,
    ;

    val onlineEnabled: Boolean get() = this == ONLINE_PAYMENT
}
