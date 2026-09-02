package com.wego.travelmarketplace.api

import com.wego.identity.AuthenticatedUser
import com.wego.travelmarketplace.application.ArchiveCategoryResult
import com.wego.travelmarketplace.application.ArchiveCategoryService
import com.wego.travelmarketplace.application.CategoryQueryService
import com.wego.travelmarketplace.application.CreateCategoryCommand
import com.wego.travelmarketplace.application.CreateCategoryResult
import com.wego.travelmarketplace.application.CreateCategoryService
import com.wego.travelmarketplace.application.UpdateCategoryCommand
import com.wego.travelmarketplace.application.UpdateCategoryResult
import com.wego.travelmarketplace.application.UpdateCategoryService
import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.CategoryStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/travel-marketplace/categories")
class CategoryController(
    private val createCategoryService: CreateCategoryService,
    private val updateCategoryService: UpdateCategoryService,
    private val archiveCategoryService: ArchiveCategoryService,
    private val categoryQueryService: CategoryQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('service:manage')")
    fun create(
        @Valid @RequestBody request: UpsertCategoryRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                createCategoryService.create(
                    CreateCategoryCommand(
                        code = request.code,
                        name = request.name.toDomain(),
                        description = request.description?.toDomain(),
                        displayOrder = request.displayOrder,
                        actorUserId = actorUserId,
                    ),
                )
        ) {
            is CreateCategoryResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.category.toResponse())
            CreateCategoryResult.DuplicateCode -> ResponseEntity.status(HttpStatus.CONFLICT).body(CategoryErrorResponse("duplicate_code"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('service:view')")
    fun list(
        @RequestParam(required = false) status: CategoryStatus?,
    ): List<CategoryResponse> = categoryQueryService.list(status).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<CategoryResponse> {
        val category = categoryQueryService.findById(CategoryId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(category.toResponse())
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('service:manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpsertCategoryRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                updateCategoryService.update(
                    UpdateCategoryCommand(
                        CategoryId(id),
                        request.name.toDomain(),
                        request.description?.toDomain(),
                        request.displayOrder,
                        actorUserId,
                    ),
                )
        ) {
            is UpdateCategoryResult.Updated -> ResponseEntity.ok(result.category.toResponse())
            UpdateCategoryResult.NotFound -> ResponseEntity.notFound().build()
            UpdateCategoryResult.Archived -> ResponseEntity.status(HttpStatus.CONFLICT).body(CategoryErrorResponse("archived"))
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('service:manage')")
    fun archive(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = archiveCategoryService.archive(CategoryId(id), actorUserId)) {
            is ArchiveCategoryResult.Archived -> ResponseEntity.ok(result.category.toResponse())
            ArchiveCategoryResult.NotFound -> ResponseEntity.notFound().build()
            ArchiveCategoryResult.AlreadyArchived ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(CategoryErrorResponse("already_archived"))
        }
    }
}

private fun Category.toResponse() =
    CategoryResponse(
        id = id.value,
        code = code,
        name = name.toDto(),
        description = description?.toDto(),
        displayOrder = displayOrder,
        status = status,
        createdAt = createdAt,
        archivedAt = archivedAt,
    )
