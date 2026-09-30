package com.wego.toursoperator.application

/** Plain-text transactional email. */
data class EmailMessage(
    val to: String,
    val subject: String,
    val body: String,
) {
    override fun toString(): String = "EmailMessage(subject=$subject)" // never print the address
}

/** Outbound email port. Implementations throw on any delivery failure. */
fun interface EmailSender {
    fun send(message: EmailMessage)
}
