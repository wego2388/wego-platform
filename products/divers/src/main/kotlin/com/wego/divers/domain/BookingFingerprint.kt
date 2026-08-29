package com.wego.divers.domain

import java.security.MessageDigest

/**
 * The canonical representation of a `CreateBooking` request, hashed with
 * SHA-256 and persisted alongside the booking it created. Lets
 * `CreateBookingService` tell a true retry (identical fingerprint) apart
 * from an `Idempotency-Key` reused with different parameters (different
 * fingerprint) — the latter must be rejected as a conflict, not silently
 * treated as a replay of a different logical request.
 *
 * Deliberately narrow, not a generic serializer: only fields that
 * determine `CreateBookingService.create`'s outcome are included.
 * `CreateBookingRequest` carries no price/pricing input today — price is
 * always derived server-side from the offering, never client-supplied —
 * so no pricing field is part of this fingerprint; if a future packet adds
 * client-supplied pricing input, this must be revisited.
 */
object BookingFingerprint {
    private val WHITESPACE = Regex("\\s+")
    private val PHONE_FORMATTING = Regex("[\\s()-]")

    fun of(
        offeringId: OfferingId,
        partySize: Int,
        customer: CustomerContact,
    ): String {
        val canonical =
            listOf(
                "offeringId=${offeringId.value}",
                "partySize=$partySize",
                "customerName=${normalizeText(customer.name)}",
                "customerEmail=${customer.email?.let(::normalizeText).orEmpty()}",
                "customerPhone=${customer.phone?.let(::normalizePhone).orEmpty()}",
            ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    // Trim + collapse internal whitespace + lowercase: a request differing
    // only in case or incidental spacing is still the same logical intent.
    // This is a documented judgment call, not a stored value transform —
    // the booking's own customer.name/email keep whatever the caller sent.
    private fun normalizeText(value: String): String = value.trim().lowercase().replace(WHITESPACE, " ")

    // Strips common formatting punctuation but keeps digits and a leading
    // "+", so "+20 10 664 6101" and "+201066461010" fingerprint identically.
    private fun normalizePhone(value: String): String = value.trim().replace(PHONE_FORMATTING, "")
}
