package com.wego.travelmarketplace.infrastructure

import com.wego.travelmarketplace.application.EmailMessage
import com.wego.travelmarketplace.application.EmailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.time.Duration

/**
 * SMTP adapter, provider-agnostic: any SMTP relay (a transactional provider or
 * a mailbox host) is configured through travel-marketplace.notifications.smtp.* (see the bean configuration).
 * Replies go to [replyTo] so "reply to this email" in the templates is true.
 */
class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    private val from: String,
    private val replyTo: String?,
) : EmailSender {
    override fun send(message: EmailMessage) {
        val mail = SimpleMailMessage()
        mail.from = from
        replyTo?.takeIf { it.isNotBlank() }?.let { mail.replyTo = it }
        mail.setTo(message.to)
        mail.subject = message.subject
        mail.text = message.body
        mailSender.send(mail)
    }

    companion object {
        private val TIMEOUT_KEYS =
            listOf("connectiontimeout", "timeout", "writetimeout").flatMap { key ->
                listOf("mail.smtp.$key", "mail.smtps.$key")
            }

        /**
         * JavaMail waits forever by default. The dispatcher sends while holding
         * a row lease, so a stalled relay must fail fast and a send must end
         * well inside the lease (twice this timeout at least). Explicitly configured values win.
         */
        fun applyDefaultTimeouts(
            sender: JavaMailSenderImpl,
            timeout: Duration,
        ) {
            val properties = sender.javaMailProperties
            TIMEOUT_KEYS.forEach { key -> properties.putIfAbsent(key, timeout.toMillis().toString()) }
        }
    }
}
