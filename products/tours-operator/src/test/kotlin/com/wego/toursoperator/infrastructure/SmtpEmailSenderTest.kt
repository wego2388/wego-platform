package com.wego.toursoperator.infrastructure

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import com.wego.toursoperator.application.EmailMessage
import com.wego.toursoperator.application.NotificationContext
import com.wego.toursoperator.application.NotificationTemplates
import com.wego.toursoperator.domain.NotificationKind
import com.wego.toursoperator.domain.TimeSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.time.LocalDate

/** Real SMTP round trip against an in-process GreenMail server (no Docker, no provider). */
class SmtpEmailSenderTest {
    companion object {
        @JvmField
        @RegisterExtension
        val greenMail = GreenMailExtension(ServerSetupTest.SMTP)
    }

    private fun sender(replyTo: String?) =
        SmtpEmailSender(
            JavaMailSenderImpl().apply {
                host = "127.0.0.1"
                port = ServerSetupTest.SMTP.port
                defaultEncoding = "UTF-8"
            },
            from = "bookings@safari.example",
            replyTo = replyTo,
        )

    @Test
    fun `delivers a rendered Arabic confirmation over SMTP with from and reply-to`() {
        val rendered =
            NotificationTemplates.render(
                NotificationKind.BOOKING_CONFIRMED,
                "ar",
                NotificationContext(
                    reference = "STR-2027-0042",
                    tourName = "Desert Quad Safari",
                    tourDate = LocalDate.of(2027, 7, 1),
                    timeSlot = TimeSlot.MORNING,
                    adults = 2,
                    children = 0,
                    totalEur = "70.00",
                    myBookingUrl = "https://site.example/my-booking",
                    reviewUrl = null,
                ),
            )

        sender(replyTo = "help@safari.example").send(EmailMessage("guest@example.com", rendered.subject, rendered.body))

        val received = greenMail.receivedMessages.single()
        assertEquals("guest@example.com", received.allRecipients.single().toString())
        assertEquals("bookings@safari.example", received.from.single().toString())
        assertEquals("help@safari.example", received.replyTo.single().toString())
        assertEquals(rendered.subject, received.subject)
        assertTrue(GreenMailUtil.getBody(received).isNotBlank())
        assertTrue(received.content.toString().contains("STR-2027-0042"))
        assertTrue(received.content.toString().contains("تم استلام الدفع"))
    }

    @Test
    fun `reply-to defaults to the sender when not configured`() {
        sender(replyTo = null).send(EmailMessage("guest@example.com", "Subject", "Body"))
        assertEquals(
            "bookings@safari.example",
            greenMail.receivedMessages
                .single()
                .replyTo
                .single()
                .toString(),
        )
    }

    @Test
    fun `a relay that accepts but never answers fails fast instead of hanging`() {
        java.net.ServerSocket(0).use { stalled ->
            // Accepts TCP connections and then never speaks SMTP.
            val acceptor =
                Thread { runCatching { while (true) stalled.accept() } }.apply {
                    isDaemon = true
                    start()
                }
            val mail =
                JavaMailSenderImpl().apply {
                    host = "127.0.0.1"
                    port = stalled.localPort
                }
            SmtpEmailSender.applyDefaultTimeouts(mail, java.time.Duration.ofMillis(500))
            val started = System.nanoTime()
            org.junit.jupiter.api.assertThrows<org.springframework.mail.MailException> {
                SmtpEmailSender(mail, "bookings@safari.example", null).send(EmailMessage("guest@example.com", "S", "B"))
            }
            val elapsed = java.time.Duration.ofNanos(System.nanoTime() - started)
            assertTrue(elapsed < java.time.Duration.ofSeconds(5), "took $elapsed")
            acceptor.interrupt()
        }
    }

    @Test
    fun `explicitly configured timeouts are not overridden`() {
        val mail = JavaMailSenderImpl()
        mail.javaMailProperties["mail.smtp.timeout"] = "1234"
        SmtpEmailSender.applyDefaultTimeouts(mail, java.time.Duration.ofSeconds(15))
        assertEquals("1234", mail.javaMailProperties["mail.smtp.timeout"])
        assertEquals("15000", mail.javaMailProperties["mail.smtp.connectiontimeout"])
    }
}
