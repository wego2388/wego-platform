package com.wego.travelmarketplace.domain

/**
 * `NEW -> IN_REVIEW -> CONFIRMED -> COMPLETED`, with `CANCELLED`/`EXPIRED` as
 * terminal exits from any non-terminal state. See [TravelRequest]'s guarded
 * transition methods for the enforced table — this enum only lists the
 * states, it does not encode which transitions are legal.
 */
enum class TravelRequestStatus {
    NEW,
    IN_REVIEW,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED || this == EXPIRED
}
