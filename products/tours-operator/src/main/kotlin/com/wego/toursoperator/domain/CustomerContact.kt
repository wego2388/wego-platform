package com.wego.toursoperator.domain

/**
 * Embedded customer contact snapshot — recorded at booking creation time
 * and never mutated. No separate customer account is required; lookup is
 * by booking reference + phone.
 */
data class CustomerContact(
    val fullName: String,
    val phone: String,
    val nationality: String,
    val email: String?,
) {
    init {
        require(fullName.isNotBlank()) { "Customer full name must not be blank" }
        require(phone.isNotBlank()) { "Customer phone must not be blank" }
        require(nationality.isNotBlank()) { "Customer nationality must not be blank" }
        require(nationality.length == 2 && nationality == nationality.uppercase()) {
            "Nationality must be a 2-letter ISO 3166-1 alpha-2 country code in uppercase"
        }
    }
}
