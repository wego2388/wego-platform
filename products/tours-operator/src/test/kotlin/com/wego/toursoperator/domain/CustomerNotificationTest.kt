package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CustomerNotificationTest {
    private val now = Instant.parse("2026-10-01T08:00:00Z")

    private fun pending() = CustomerNotification.queue(BookingId(UUID.randomUUID()), NotificationKind.BOOKING_CONFIRMED, now, now)

    @Test
    fun `sending records the time and one attempt`() {
        val n = pending()
        n.markSent(now)
        assertEquals(NotificationStatus.SENT, n.status)
        assertEquals(now, n.sentAt)
        assertEquals(1, n.attemptCount)
    }

    @Test
    fun `failures back off exponentially and stop at max attempts`() {
        val n = pending()
        n.markAttemptFailed("MailSendException", now, maxAttempts = 3)
        assertEquals(NotificationStatus.PENDING, n.status)
        assertEquals(now.plus(Duration.ofMinutes(1)), n.availableAt)
        n.markAttemptFailed("MailSendException", now, maxAttempts = 3)
        assertEquals(now.plus(Duration.ofMinutes(2)), n.availableAt)
        n.markAttemptFailed("MailSendException", now, maxAttempts = 3)
        assertEquals(NotificationStatus.FAILED, n.status)
        assertEquals(3, n.attemptCount)
        assertEquals("MailSendException", n.lastError)
    }

    @Test
    fun `backoff is capped at one hour`() {
        assertEquals(Duration.ofMinutes(1), CustomerNotification.backoffAfter(1))
        assertEquals(Duration.ofMinutes(32), CustomerNotification.backoffAfter(6))
        assertEquals(Duration.ofMinutes(60), CustomerNotification.backoffAfter(20))
    }

    @Test
    fun `only a pending notification can be delivered`() {
        val n = pending()
        n.markSkipped("no_customer_email")
        assertThrows<IllegalArgumentException> { n.markSent(now) }
        assertThrows<IllegalArgumentException> { n.markAttemptFailed("x", now, 3) }
    }

    @Test
    fun `requeue resets delivery state even after SENT`() {
        val n = pending()
        n.markSent(now)
        val later = now.plusSeconds(600)
        val staff = UUID.randomUUID()
        n.requeue(later, staff)
        assertEquals(NotificationStatus.PENDING, n.status)
        assertEquals(1, n.resendCount)
        assertEquals(staff, n.lastResentByUserId)
        assertEquals(later, n.lastResentAt)
        assertEquals(0, n.attemptCount)
        assertEquals(later, n.availableAt)
        assertNull(n.sentAt)
        assertNull(n.lastError)
    }

    @Test
    fun `stored reasons are truncated`() {
        val n = pending()
        n.markSkipped("x".repeat(500))
        assertEquals(CustomerNotification.MAX_ERROR_LENGTH, n.lastError!!.length)
    }
}
