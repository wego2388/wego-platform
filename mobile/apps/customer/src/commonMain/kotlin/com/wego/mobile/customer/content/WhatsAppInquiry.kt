package com.wego.mobile.customer.content

import com.wego.mobile.shared.catalog.Offering
import com.wego.mobile.shared.locale.AppLocale

/**
 * The same prefilled-inquiry deep-link pattern already proven on
 * `discover/[code].vue`: a plain `wa.me` URL with a `text` query param, no
 * in-app chat. Opened via `LocalUriHandler` from the screen, not here.
 */
fun offeringInquiryUrl(
    offering: Offering,
    locale: AppLocale,
): String {
    val label = offering.name.of(locale)
    val text =
        if (locale == AppLocale.AR) {
            "مرحبًا، عايز أسأل عن: $label (${offering.code})"
        } else {
            "Hi, I'd like to ask about: $label (${offering.code})"
        }
    return "${SiteCopy.WHATSAPP_URL}?text=${encodeUrlQueryComponent(text)}"
}

/**
 * A minimal RFC 3986 query-component percent-encoder, kept local rather
 * than pulling in a Ktor/OkHttp dependency for one string. Encodes every
 * byte of the UTF-8 representation except the small unreserved set.
 */
private fun encodeUrlQueryComponent(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    val builder = StringBuilder()
    for (byte in value.encodeToByteArray()) {
        val char = byte.toInt().toChar()
        if (byte >= 0 && char in unreserved) {
            builder.append(char)
        } else {
            val unsigned = byte.toInt() and 0xFF
            builder.append('%')
            builder.append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return builder.toString()
}
