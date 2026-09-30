package com.wego.toursoperator.application

import com.wego.toursoperator.domain.NotificationKind
import com.wego.toursoperator.domain.TimeSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class NotificationTemplatesTest {
    private val context =
        NotificationContext(
            reference = "STR-2027-0042",
            tourName = "Desert Quad Safari",
            tourDate = LocalDate.of(2027, 7, 1),
            timeSlot = TimeSlot.SUNSET,
            adults = 2,
            children = 1,
            totalEur = "87.50",
            myBookingUrl = "https://site.example/my-booking",
            reviewUrl = "https://reviews.example/safari",
        )

    @Test
    fun `every supported locale renders every kind with the booking facts`() {
        for (locale in NotificationTemplates.SUPPORTED_LOCALES) {
            for (kind in NotificationKind.entries) {
                val email = NotificationTemplates.render(kind, locale, context)
                assertTrue(email.subject.isNotBlank(), "$locale/$kind subject")
                when (kind) {
                    NotificationKind.BOOKING_CONFIRMED -> {
                        assertTrue(email.subject.contains("STR-2027-0042"))
                        assertTrue(email.body.contains("€87.50"), "$locale total")
                        assertTrue(email.body.contains("https://site.example/my-booking"), "$locale link")
                        assertTrue(email.body.contains("Desert Quad Safari"))
                    }
                    NotificationKind.BOOKING_CANCELLED -> assertTrue(email.body.contains("STR-2027-0042"))
                    NotificationKind.REVIEW_REQUEST -> assertTrue(email.body.contains("https://reviews.example/safari"))
                }
            }
        }
    }

    @Test
    fun `unsupported locales fall back to English`() {
        val german = NotificationTemplates.render(NotificationKind.BOOKING_CONFIRMED, "de", context)
        val english = NotificationTemplates.render(NotificationKind.BOOKING_CONFIRMED, "en", context)
        assertEquals(english, german)
    }

    @Test
    fun `arabic uses arabic wording`() {
        val email = NotificationTemplates.render(NotificationKind.BOOKING_CONFIRMED, "ar", context)
        assertTrue(email.subject.startsWith("تم تأكيد الحجز"))
        assertTrue(email.body.contains("غروب الشمس"))
    }

    @Test
    fun `a review request needs a review link`() {
        assertThrows<IllegalArgumentException> {
            NotificationTemplates.render(NotificationKind.REVIEW_REQUEST, "en", context.copy(reviewUrl = null))
        }
    }

    @Test
    fun `an email message never prints its recipient`() {
        val message = EmailMessage("guest@example.com", "Subject", "Body")
        assertTrue(!message.toString().contains("guest@example.com"))
    }
}
