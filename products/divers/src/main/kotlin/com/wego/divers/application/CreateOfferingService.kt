package com.wego.divers.application

import com.wego.divers.domain.Money
import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateOfferingCommand(
    val offeringType: OfferingType,
    val title: String,
    val description: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val capacity: Int?,
    val pricingBasis: PricingBasis,
    val unitPrice: Money,
    val createdByUserId: UUID?,
    val correlationId: UUID?,
)

class CreateOfferingService(
    private val offeringRepository: OfferingRepository,
    private val offeringAuditRecorder: OfferingAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(command: CreateOfferingCommand): Offering =
        transactionRunner.runInTransaction {
            val now = Instant.now(clock)
            val offering =
                Offering.create(
                    id = OfferingId.generate(),
                    offeringType = command.offeringType,
                    title = command.title,
                    description = command.description,
                    startsOn = command.startsOn,
                    endsOn = command.endsOn,
                    capacity = command.capacity,
                    pricingBasis = command.pricingBasis,
                    unitPrice = command.unitPrice,
                    createdByUserId = command.createdByUserId,
                    now = now,
                )
            offeringRepository.save(offering)
            offeringAuditRecorder.recordOfferingCreated(offering.id, command.createdByUserId, now, command.correlationId)
            offering
        }
}
