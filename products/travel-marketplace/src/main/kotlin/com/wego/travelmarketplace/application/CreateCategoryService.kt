package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.LocalizedText
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CreateCategoryCommand(
    val code: String,
    val name: LocalizedText,
    val description: LocalizedText?,
    val displayOrder: Int,
    val actorUserId: UUID?,
)

sealed interface CreateCategoryResult {
    data class Created(
        val category: Category,
    ) : CreateCategoryResult

    data object DuplicateCode : CreateCategoryResult
}

class CreateCategoryService(
    private val categoryRepository: CategoryRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(command: CreateCategoryCommand): CreateCategoryResult =
        transactionRunner.runInTransaction {
            if (categoryRepository.findByCode(command.code) != null) return@runInTransaction CreateCategoryResult.DuplicateCode

            val now = Instant.now(clock)
            val category =
                Category.create(
                    id = CategoryId.generate(),
                    code = command.code,
                    name = command.name,
                    description = command.description,
                    displayOrder = command.displayOrder,
                    now = now,
                )
            categoryRepository.save(category)
            auditRecorder.record(AuditAggregateType.CATEGORY, category.id.value, "CREATED", command.actorUserId, now)
            CreateCategoryResult.Created(category)
        }
}
