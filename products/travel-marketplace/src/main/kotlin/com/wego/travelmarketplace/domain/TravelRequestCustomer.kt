package com.wego.travelmarketplace.domain

/**
 * Minimal customer identity captured on request creation — deliberately not
 * a full account/profile: this client explicitly chose anonymous,
 * no-signup requests (`clients/sharm-to-go/delivery/01_REQUEST_AND_BOOKING.md`:
 * "Require customer name and at least one valid reachable contact"). At
 * least one of [phone]/[email] must be present so staff can always reach the
 * customer, but neither alone is mandatory since the owner's own decision
 * was WhatsApp-first contact with email as a fallback, not the reverse.
 */
data class TravelRequestCustomer(
    val name: String,
    val phone: String?,
    val email: String?,
) {
    init {
        require(name.isNotBlank()) { "Customer name must not be blank" }
        require(phone != null || email != null) { "At least one of phone or email is required" }
        require(phone == null || isPlausiblePhoneNumber(phone)) { "Phone is not a plausible phone number" }
        require(email == null || email.isNotBlank()) { "Email must not be blank when present" }
    }

    companion object {
        /**
         * An E.164-shaped plausibility check (optional leading `+`, 7-15
         * digits, no leading zero after the country code) — not full
         * carrier-verified reachability. This deliberately does not pull in
         * libphonenumber (no existing dependency on it anywhere in this
         * monorepo) for what is, for now, a single format check; revisit if
         * country-aware formatting/normalization is ever actually needed.
         * Rejects what the previous "any non-blank string" check accepted,
         * such as "abc" or "123".
         */
        private val PLAUSIBLE_PHONE = Regex("^\\+?[1-9]\\d{6,14}$")

        fun isPlausiblePhoneNumber(raw: String): Boolean {
            val stripped = raw.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '.' }
            return PLAUSIBLE_PHONE.matches(stripped)
        }
    }
}
