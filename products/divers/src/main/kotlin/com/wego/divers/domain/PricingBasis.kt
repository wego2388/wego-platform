package com.wego.divers.domain

/**
 * How an offering's `unitPrice` turns into a booking's total.
 * `PER_PARTICIPANT`: total = unitPrice × party size (a per-diver trip,
 * course, or package). `FLAT`: total = unitPrice regardless of party size
 * (e.g. a single equipment-rental line, a whole-boat charter). Every
 * offering states this explicitly — it is never inferred from
 * [OfferingType], since two offerings of the same type can price
 * differently (a private charter vs. a per-diver group trip).
 */
enum class PricingBasis {
    PER_PARTICIPANT,
    FLAT,
}
