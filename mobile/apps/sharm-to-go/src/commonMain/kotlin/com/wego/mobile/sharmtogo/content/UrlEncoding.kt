package com.wego.mobile.sharmtogo.content

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

/**
 * Minimal RFC 3986 percent-encoder for one query component, kept local rather
 * than adding an HTTP-client dependency for a single string — same approach
 * `WhatsAppInquiry.kt` uses in the Sharm Divers Club app.
 */
fun percentEncode(value: String): String {
    val builder = StringBuilder()
    for (byte in value.encodeToByteArray()) {
        val char = byte.toInt().toChar()
        if (byte >= 0 && char in UNRESERVED) {
            builder.append(char)
        } else {
            builder.append('%')
            builder.append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
    return builder.toString()
}
