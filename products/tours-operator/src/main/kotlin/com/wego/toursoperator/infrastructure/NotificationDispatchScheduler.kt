package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.DispatchNotificationsService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Sends due customer notifications. Disabled unless
 * tours-operator.notifications.enabled=true, so an environment without SMTP
 * settings keeps rows PENDING (visible to staff) instead of burning retries.
 * Rows are claimed with SKIP LOCKED, so running on several instances is safe.
 */
@Component
@ConditionalOnProperty(prefix = "tours-operator.notifications", name = ["enabled"], havingValue = "true")
class NotificationDispatchScheduler(
    private val dispatchNotificationsService: DispatchNotificationsService,
) {
    private val log = LoggerFactory.getLogger(NotificationDispatchScheduler::class.java)

    @Scheduled(fixedDelayString = "\${tours-operator.notifications.interval-ms:30000}")
    fun run() {
        try {
            dispatchNotificationsService.dispatchDue(BATCH_LIMIT)
        } catch (ex: Exception) {
            // Never rethrow: a scheduler exception would stop future runs.
            log.error("NotificationDispatchScheduler: run failed: {}", ex.javaClass.simpleName)
        }
    }

    private companion object {
        const val BATCH_LIMIT = 50
    }
}
