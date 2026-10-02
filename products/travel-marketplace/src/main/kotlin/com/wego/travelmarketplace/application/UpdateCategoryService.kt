package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.LocalizedText
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class UpdateCategoryCommand(
    val categoryId: CategoryId,
    // Not applied — `code` is immutable after creation (it may already be
    // referenced by URLs/exports). Carried here only so the service can
    // detect and reject a caller silently attempting to change it, rather
    // than the API accepting a different code in the request body and
    // quietly keeping the original — a real gap an independent review
    // caught live (a PUT with a changed `code` returned 200 with the
    // original code, which reads as success while doing something other
    // than what was asked).
    val requestedCode: String,
    val name: LocalizedText,
    val description: LocalizedText?,
    val displayOrder: Int,
    val actorUserId: UUID?,
    val expectedVersion: Int,
)

sealed interface UpdateCategoryResult {
    data class Updated(
        val category: Category,
    ) : UpdateCategoryResult

    data object NotFound : UpdateCategoryResult

    data object Archived : UpdateCategoryResult

    data object CodeImmutable : UpdateCategoryResult

    data class VersionConflict(
        val currentVersion: Int,
    ) : UpdateCategoryResult
}

/** See [com.wego.travelmarketplace.application.UpdateServiceService]'s own class doc for why [UpdateCategoryCommand.expectedVersion] exists and why the check here is race-free. */
class UpdateCategoryService(
    private val categoryRepository: CategoryRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun update(command: UpdateCategoryCommand): UpdateCategoryResult =
        transactionRunner.runInTransaction {
            val existing =
                categoryRepository.findByIdForUpdate(command.categoryId) ?: return@runInTransaction UpdateCategoryResult.NotFound
            if (!existing.isActive) return@runInTransaction UpdateCategoryResult.Archived
            if (existing.version != command.expectedVersion) {
                return@runInTransaction UpdateCategoryResult.VersionConflict(existing.version)
            }
            if (command.requestedCode != existing.code) return@runInTransaction UpdateCategoryResult.CodeImmutable

            val updated = existing.withUpdatedDetails(command.name, command.description, command.displayOrder)
            categoryRepository.save(updated)
            val now = Instant.now(clock)
            auditRecorder.record(AuditAggregateType.CATEGORY, updated.id.value, "UPDATED", command.actorUserId, now)
            UpdateCategoryResult.Updated(updated)
        }
}
