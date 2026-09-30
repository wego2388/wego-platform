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
        require(phone == null || phone.isNotBlank()) { "Phone must not be blank when present" }
        require(email == null || email.isNotBlank()) { "Email must not be blank when present" }
    }
}
