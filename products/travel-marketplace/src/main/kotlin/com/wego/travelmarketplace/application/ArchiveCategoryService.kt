package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface ArchiveCategoryResult {
    data class Archived(
        val category: Category,
    ) : ArchiveCategoryResult

    data object NotFound : ArchiveCategoryResult

    data object AlreadyArchived : ArchiveCategoryResult
}

class ArchiveCategoryService(
    private val categoryRepository: CategoryRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun archive(
        categoryId: CategoryId,
        actorUserId: UUID?,
    ): ArchiveCategoryResult =
        transactionRunner.runInTransaction {
            val category = categoryRepository.findByIdForUpdate(categoryId) ?: return@runInTransaction ArchiveCategoryResult.NotFound
            if (!category.isActive) return@runInTransaction ArchiveCategoryResult.AlreadyArchived

            val now = Instant.now(clock)
            category.archive(now)
            categoryRepository.save(category)
            auditRecorder.record(AuditAggregateType.CATEGORY, category.id.value, "ARCHIVED", actorUserId, now)
            ArchiveCategoryResult.Archived(category)
        }
}
