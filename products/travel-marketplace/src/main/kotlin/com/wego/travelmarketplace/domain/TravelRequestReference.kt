package com.wego.travelmarketplace.domain

import java.security.SecureRandom

/**
 * The customer-facing lookup handle for a [TravelRequest] — the only thing a
 * customer types into a public status-lookup form or reads back over
 * WhatsApp. Deliberately **not** sequential (unlike, say, an incrementing
 * booking number): `delivery/01_REQUEST_AND_BOOKING.md` requires a format
 * that is "not guessable", so a sequential `STG-2026-0042`-style reference
 * would let anyone enumerate other customers' requests by changing one
 * digit. Instead this is 8 characters from a 32-symbol unambiguous alphabet
 * (no `0/O/1/I` confusion pairs) drawn from [SecureRandom] — ~40 bits of
 * entropy, which is not brute-forceable through a public rate-limited
 * lookup endpoint, while staying short enough to read aloud or type on a
 * phone. Contains no PII: it encodes nothing about the customer, service or
 * date.
 */
@JvmInline
value class TravelRequestReference(
    val value: String,
) {
    init {
        require(FORMAT.matches(value)) { "Reference must match $FORMAT" }
    }

    companion object {
        private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val CODE_LENGTH = 8
        private val FORMAT = Regex("^STG-[$ALPHABET]{$CODE_LENGTH}$")
        private val random = SecureRandom()

        fun generate(): TravelRequestReference {
            val code =
                buildString(CODE_LENGTH) {
                    repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
                }
            return TravelRequestReference("STG-$code")
        }
    }
}
