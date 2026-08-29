package com.wego.divers.domain

data class CustomerContact(
    val name: String,
    val email: String?,
    val phone: String?,
) {
    init {
        require(name.isNotBlank()) { "Customer name must not be blank" }
        // isNullOrBlank, not != null: a blank-but-present string (e.g. an
        // empty "customerEmail": "") must not satisfy "at least one contact
        // is present" — it isn't a usable contact.
        require(!email.isNullOrBlank() || !phone.isNullOrBlank()) { "Customer contact must include an email or a phone number" }
    }
}
