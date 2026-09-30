package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.EmailMessage
import com.wego.toursoperator.application.EmailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.time.Duration

/**
 * SMTP adapter, provider-agnostic: any SMTP relay (a transactional provider or
 * a mailbox host) is configured through the standard spring.mail.* settings.
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
         * a row lock and a DB connection, so a stalled relay must fail fast
         * instead of freezing delivery. Explicitly configured values win.
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
