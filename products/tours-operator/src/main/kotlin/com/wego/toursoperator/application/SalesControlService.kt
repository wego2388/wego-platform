package com.wego.toursoperator.application

import com.wego.toursoperator.domain.SalesControl
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class UpdateSalesControlCommand(
    val bookingsPaused: Boolean,
    val paymentsPaused: Boolean,
    val reason: String?,
    val actorUserId: UUID,
)

class SalesControlService(
    private val repository: SalesControlRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(SalesControlService::class.java)

    fun current(): SalesControl = repository.current()

    fun update(command: UpdateSalesControlCommand): SalesControl =
        transactionRunner.runInTransaction {
            val control =
                SalesControl(
                    bookingsPaused = command.bookingsPaused,
                    paymentsPaused = command.paymentsPaused,
                    reason = command.reason?.trim()?.takeIf { it.isNotEmpty() },
                    updatedByUserId = command.actorUserId,
                    updatedAt = Instant.now(clock),
                )
            repository.save(control)
            // The staff note is deliberately not logged.
            log.warn(
                "Sales control changed: bookingsPaused={} paymentsPaused={} by user={}",
                control.bookingsPaused,
                control.paymentsPaused,
                command.actorUserId,
            )
            control
        }
}
