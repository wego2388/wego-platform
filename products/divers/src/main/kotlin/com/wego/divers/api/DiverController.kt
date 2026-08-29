package com.wego.divers.api

import com.wego.divers.application.ArchiveDiverResult
import com.wego.divers.application.ArchiveDiverService
import com.wego.divers.application.CreateDiverCommand
import com.wego.divers.application.CreateDiverService
import com.wego.divers.application.DiverQueryService
import com.wego.divers.application.UpdateDiverCommand
import com.wego.divers.application.UpdateDiverResult
import com.wego.divers.application.UpdateDiverService
import com.wego.divers.domain.Diver
import com.wego.divers.domain.DiverCertification
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.DiverStatus
import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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
@RequestMapping("/api/v1/divers/divers")
class DiverController(
    private val createDiverService: CreateDiverService,
    private val updateDiverService: UpdateDiverService,
    private val archiveDiverService: ArchiveDiverService,
    private val diverQueryService: DiverQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('diver:manage')")
    fun create(
        @Valid @RequestBody request: UpsertDiverRequest,
        authentication: Authentication,
    ): ResponseEntity<DiverResponse> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        val diver =
            createDiverService.create(
                CreateDiverCommand(
                    fullName = request.fullName,
                    nationality = request.nationality,
                    primaryLanguage = request.primaryLanguage,
                    email = request.email,
                    phone = request.phone,
                    emergencyContactName = request.emergencyContactName,
                    emergencyContactPhone = request.emergencyContactPhone,
                    medicalNotes = request.medicalNotes,
                    totalLoggedDives = request.totalLoggedDives,
                    maxDepthMeters = request.maxDepthMeters,
                    lastDiveOn = request.lastDiveOn,
                    bcdSize = request.bcdSize,
                    finSize = request.finSize,
                    wetsuitSize = request.wetsuitSize,
                    certifications = request.certifications.map { it.toDomain() },
                    createdByUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                ),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(diver.toResponse())
    }

    @GetMapping
    @PreAuthorize("hasAuthority('diver:view')")
    fun list(
        @RequestParam(required = false) status: DiverStatus?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<DiverSummaryResponse> = diverQueryService.list(status, search, page, size).map { it.toSummaryResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('diver:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<DiverResponse> {
        val diver = diverQueryService.findById(DiverId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(diver.toResponse())
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('diver:manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpsertDiverRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                updateDiverService.update(
                    UpdateDiverCommand(
                        diverId = DiverId(id),
                        fullName = request.fullName,
                        nationality = request.nationality,
                        primaryLanguage = request.primaryLanguage,
                        email = request.email,
                        phone = request.phone,
                        emergencyContactName = request.emergencyContactName,
                        emergencyContactPhone = request.emergencyContactPhone,
                        medicalNotes = request.medicalNotes,
                        totalLoggedDives = request.totalLoggedDives,
                        maxDepthMeters = request.maxDepthMeters,
                        lastDiveOn = request.lastDiveOn,
                        bcdSize = request.bcdSize,
                        finSize = request.finSize,
                        wetsuitSize = request.wetsuitSize,
                        certifications = request.certifications.map { it.toDomain() },
                        actorUserId = actorUserId,
                        correlationId = CorrelationContext.currentCorrelationId(),
                    ),
                )
        ) {
            is UpdateDiverResult.Updated -> ResponseEntity.ok(result.diver.toResponse())
            UpdateDiverResult.NotFound -> ResponseEntity.notFound().build()
            UpdateDiverResult.Archived -> ResponseEntity.status(HttpStatus.CONFLICT).body(DiverErrorResponse("archived"))
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('diver:manage')")
    fun archive(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = archiveDiverService.archive(DiverId(id), actorUserId, CorrelationContext.currentCorrelationId())) {
            is ArchiveDiverResult.Archived -> ResponseEntity.ok(result.diver.toResponse())
            ArchiveDiverResult.NotFound -> ResponseEntity.notFound().build()
            ArchiveDiverResult.AlreadyArchived -> ResponseEntity.status(HttpStatus.CONFLICT).body(DiverErrorResponse("already_archived"))
        }
    }
}

private fun DiverCertificationDto.toDomain(): DiverCertification =
    DiverCertification(
        id = id ?: UUID.randomUUID(),
        agency = agency,
        level = level,
        certificationNumber = certificationNumber,
        issuedOn = issuedOn,
    )

private fun DiverCertification.toDto(): DiverCertificationDto =
    DiverCertificationDto(
        id = id,
        agency = agency,
        level = level,
        certificationNumber = certificationNumber,
        issuedOn = issuedOn,
    )

private fun Diver.toSummaryResponse() =
    DiverSummaryResponse(
        id = id.value,
        fullName = fullName,
        nationality = nationality,
        primaryLanguage = primaryLanguage,
        totalLoggedDives = totalLoggedDives,
        maxDepthMeters = maxDepthMeters,
        lastDiveOn = lastDiveOn,
        certifications = certifications.map { DiverCertificationSummaryDto(agency = it.agency, level = it.level, issuedOn = it.issuedOn) },
        status = status,
        createdAt = createdAt,
        archivedAt = archivedAt,
    )

private fun Diver.toResponse() =
    DiverResponse(
        id = id.value,
        fullName = fullName,
        nationality = nationality,
        primaryLanguage = primaryLanguage,
        email = email,
        phone = phone,
        emergencyContactName = emergencyContactName,
        emergencyContactPhone = emergencyContactPhone,
        medicalNotes = medicalNotes,
        totalLoggedDives = totalLoggedDives,
        maxDepthMeters = maxDepthMeters,
        lastDiveOn = lastDiveOn,
        bcdSize = bcdSize,
        finSize = finSize,
        wetsuitSize = wetsuitSize,
        certifications = certifications.map { it.toDto() },
        status = status,
        createdAt = createdAt,
        archivedAt = archivedAt,
    )
