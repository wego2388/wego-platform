package com.wego.toursoperator.application

import com.wego.toursoperator.domain.CostBasis
import com.wego.toursoperator.domain.CostCategory
import com.wego.toursoperator.domain.CostComponent
import com.wego.toursoperator.domain.FinanceAmount
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.TourId
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Outcome of an OPS2-F write. The kind maps to an HTTP status in the API layer. */
sealed class FinanceResult<out T> {
    data class Ok<T>(
        val value: T,
        /** False when an idempotent retry returned what was already written. */
        val created: Boolean = true,
    ) : FinanceResult<T>()

    data class Failed(
        val kind: FailureKind,
        val code: String,
        val details: Map<String, String> = emptyMap(),
    ) : FinanceResult<Nothing>()
}

enum class FailureKind { NOT_FOUND, INVALID, CONFLICT, FORBIDDEN }

internal fun invalid(code: String) = FinanceResult.Failed(FailureKind.INVALID, code)

internal fun conflict(
    code: String,
    details: Map<String, String> = emptyMap(),
) = FinanceResult.Failed(FailureKind.CONFLICT, code, details)

internal fun forbidden(code: String) = FinanceResult.Failed(FailureKind.FORBIDDEN, code)

internal fun notFound(code: String = "not_found") = FinanceResult.Failed(FailureKind.NOT_FOUND, code)

data class CostInput(
    val tourId: UUID?,
    val driverId: UUID?,
    val category: CostCategory,
    val label: String,
    val basis: CostBasis,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    val childAmount: BigDecimal?,
    val supplierId: UUID?,
    val validFrom: LocalDate,
    val validUntil: LocalDate?,
    val note: String?,
    /** Idempotency key (review M3): a retry with the same key and payload returns what was created. */
    val clientRequestId: UUID = UUID.randomUUID(),
)

/**
 * Effective-dated tour costs and driver trip rates (`tours-operator.cost:manage`).
 * A component is never edited: "change" ends it the day before the new value
 * takes effect and adds a new component naming the one it replaces; "end"
 * stops it after a given day. Ending is guarded by a row lock and by the
 * database (a row can be ended once).
 */
class CostService(
    private val costs: CostComponentRepository,
    private val tours: TourRepository,
    private val drivers: DriverRepository,
    private val suppliers: SupplierRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun list(
        tourId: UUID?,
        driverId: UUID?,
        includeEnded: Boolean,
    ): List<CostComponent> =
        costs
            .findAll()
            .filter { tourId == null || it.tourId?.value == tourId }
            .filter { driverId == null || it.driverId == driverId }
            .filter { includeEnded || !it.ended }
            .sortedWith(
                compareBy({ it.tourId?.value?.toString() ?: "" }, { it.driverId?.toString() ?: "" }, { it.category }, { it.validFrom }),
            )

    fun create(
        input: CostInput,
        actorUserId: UUID,
    ): FinanceResult<CostComponent> =
        transactionRunner.runInTransaction {
            replay(input, actorUserId, replaces = null)?.let { return@runInTransaction it }
            val component =
                when (val built = build(input, actorUserId, replaces = null)) {
                    is FinanceResult.Ok -> built.value
                    is FinanceResult.Failed -> return@runInTransaction built
                }
            duplicate(component, except = null)?.let { return@runInTransaction it }
            costs.append(component)
            FinanceResult.Ok(component)
        }

    /** An idempotent retry: same actor and key. Same payload returns the component (200); a different one is refused. */
    private fun replay(
        input: CostInput,
        actorUserId: UUID,
        replaces: UUID?,
    ): FinanceResult<CostComponent>? {
        val existing = costs.findByRequest(actorUserId, input.clientRequestId) ?: return null
        val same =
            existing.tourId?.value == input.tourId &&
                existing.driverId == input.driverId &&
                existing.category == input.category &&
                existing.label == input.label.trim() &&
                existing.currency == input.currency &&
                existing.amount.compareTo(input.amount) == 0 &&
                existing.validFrom == input.validFrom &&
                existing.replacesComponentId == replaces
        return if (same) FinanceResult.Ok(existing, created = false) else conflict("idempotency_key_reused")
    }

    /** Review M3: an identical open component over overlapping days is refused (use "change from a date" instead). */
    private fun duplicate(
        component: CostComponent,
        except: UUID?,
    ): FinanceResult.Failed? {
        costs.lockOwner(component.tourId?.value ?: checkNotNull(component.driverId))
        val clash = costs.findAll().firstOrNull { !it.ended && it.id != except && it.duplicates(component) }
        return clash?.let { conflict("cost_component_duplicate", mapOf("existingId" to it.id.toString())) }
    }

    /** End [id] on the day before [input].validFrom and add the new value from that day. */
    fun replace(
        id: UUID,
        input: CostInput,
        actorUserId: UUID,
    ): FinanceResult<CostComponent> =
        transactionRunner.runInTransaction {
            replay(input, actorUserId, replaces = id)?.let { return@runInTransaction it }
            val old = costs.findByIdForUpdate(id) ?: return@runInTransaction notFound()
            if (old.ended) return@runInTransaction conflict("cost_already_ended")
            if (old.tourId?.value != input.tourId || old.driverId != input.driverId || old.category != input.category) {
                return@runInTransaction invalid("cost_owner_mismatch")
            }
            if (input.validFrom.isBefore(old.validFrom)) return@runInTransaction invalid("effective_before_current")
            if (old.validUntil != null &&
                input.validFrom.isAfter(old.validUntil)
            ) {
                return@runInTransaction invalid("effective_after_current")
            }
            val successor =
                when (val built = build(input.copy(validUntil = old.validUntil), actorUserId, replaces = old.id)) {
                    is FinanceResult.Ok -> built.value
                    is FinanceResult.Failed -> return@runInTransaction built
                }
            duplicate(successor, except = old.id)?.let { return@runInTransaction it }
            costs.end(old.id, input.validFrom.minusDays(1), actorUserId, Instant.now(clock))
            costs.append(successor)
            FinanceResult.Ok(successor)
        }

    /** Stop [id] after [lastDay] (inclusive). lastDay = validFrom − 1 withdraws a component that never took effect. */
    fun end(
        id: UUID,
        lastDay: LocalDate,
        actorUserId: UUID,
    ): FinanceResult<CostComponent> =
        transactionRunner.runInTransaction {
            val old = costs.findByIdForUpdate(id) ?: return@runInTransaction notFound()
            if (old.ended) return@runInTransaction conflict("cost_already_ended")
            if (lastDay.isBefore(old.validFrom.minusDays(1))) return@runInTransaction invalid("end_before_start")
            if (old.validUntil != null && lastDay.isAfter(old.validUntil)) return@runInTransaction invalid("end_after_current")
            val now = Instant.now(clock)
            costs.end(old.id, lastDay, actorUserId, now)
            FinanceResult.Ok(old.copy(validUntil = lastDay, endedAt = now, endedByUserId = actorUserId))
        }

    private fun build(
        input: CostInput,
        actorUserId: UUID,
        replaces: UUID?,
    ): FinanceResult<CostComponent> {
        if ((input.tourId == null) == (input.driverId == null)) return invalid("cost_owner_required")
        if ((input.category == CostCategory.DRIVER) != (input.driverId != null)) return invalid("driver_cost_needs_driver")
        if (input.category in setOf(CostCategory.FIXED, CostCategory.DRIVER) && input.basis != CostBasis.PER_DEPARTURE) {
            return invalid("basis_must_be_per_departure")
        }
        if (input.childAmount != null && input.basis != CostBasis.PER_PERSON) return invalid("child_amount_per_person_only")
        if (input.supplierId != null && input.category != CostCategory.SUPPLIER) return invalid("supplier_only_for_supplier_cost")
        if (input.validUntil != null && input.validUntil.isBefore(input.validFrom)) return invalid("valid_until_before_valid_from")
        input.tourId?.let { if (tours.findById(TourId(it)) == null) return invalid("tour_not_found") }
        input.driverId?.let { if (drivers.findById(it) == null) return invalid("driver_not_found") }
        input.supplierId?.let { if (suppliers.findById(it) == null) return invalid("supplier_not_found") }
        return FinanceResult.Ok(
            CostComponent(
                id = UUID.randomUUID(),
                tourId = input.tourId?.let(::TourId),
                driverId = input.driverId,
                category = input.category,
                label = checkNotNull(FinanceAmount.text(input.label, "label", 80, required = true)),
                basis = input.basis,
                currency = input.currency,
                amount = FinanceAmount.nonNegative(input.amount),
                childAmount = input.childAmount?.let(FinanceAmount::nonNegative),
                supplierId = input.supplierId,
                validFrom = input.validFrom,
                validUntil = input.validUntil,
                replacesComponentId = replaces,
                note = FinanceAmount.text(input.note, "note", 500),
                createdByUserId = actorUserId,
                createdAt = Instant.now(clock),
                clientRequestId = input.clientRequestId,
            ),
        )
    }
}
