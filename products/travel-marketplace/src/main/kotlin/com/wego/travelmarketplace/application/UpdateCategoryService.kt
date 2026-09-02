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
    val name: LocalizedText,
    val description: LocalizedText?,
    val displayOrder: Int,
    val actorUserId: UUID?,
)

sealed interface UpdateCategoryResult {
    data class Updated(
        val category: Category,
    ) : UpdateCategoryResult

    data object NotFound : UpdateCategoryResult

    data object Archived : UpdateCategoryResult
}

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

            val updated = existing.withUpdatedDetails(command.name, command.description, command.displayOrder)
            categoryRepository.save(updated)
            val now = Instant.now(clock)
            auditRecorder.record(AuditAggregateType.CATEGORY, updated.id.value, "UPDATED", command.actorUserId, now)
            UpdateCategoryResult.Updated(updated)
        }
}
