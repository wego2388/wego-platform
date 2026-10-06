package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.AssignCommand
import com.wego.toursoperator.application.AssignResult
import com.wego.toursoperator.application.AssignmentService
import com.wego.toursoperator.application.OpsRegistryService
import com.wego.toursoperator.application.RegistryResult
import com.wego.toursoperator.domain.TourSlotId
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.CacheControl
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
import java.time.LocalDate
import java.util.UUID

private fun actor(authentication: Authentication): UUID = (authentication.principal as AuthenticatedUser).userId

/** Registry responses carry phone numbers, so they are never cached. */
private fun <T : Any> noStore(body: T): ResponseEntity<Any> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)

private fun <T> registryResponse(
    result: RegistryResult<T>,
    created: Boolean,
    map: (T) -> Any,
): ResponseEntity<Any> =
    when (result) {
        is RegistryResult.Ok ->
            ResponseEntity
                .status(if (created) HttpStatus.CREATED else HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(map(result.value))
        RegistryResult.NotFound -> ResponseEntity.notFound().build()
        is RegistryResult.RevisionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(RevisionConflictBody(currentRevision = result.currentRevision))
        is RegistryResult.Refused ->
            ResponseEntity
                .status(if (result.code.endsWith("_taken")) HttpStatus.CONFLICT else HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse(result.code))
    }

private fun missingRevision(): ResponseEntity<Any> = ResponseEntity.badRequest().body(ValidationErrorResponse(message = "expectedRevision: required when updating"))

/**
 * WEGO-016-OPS2-E supplier registry. Phones are visible only with
 * `tours-operator.supplier:manage` (and on the ops documents). No delete: set `active=false`.
 * Agreed supplier prices are not here (OPS2-F).
 */
@Validated
@RestController("toursOperatorSupplierController")
@RequestMapping("/api/v1/tours-operator/staff/suppliers")
class SupplierController(
    private val registry: OpsRegistryService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('tours-operator.supplier:manage')")
    fun list(
        @RequestParam(required = false) active: Boolean?,
    ): ResponseEntity<Any> = noStore(registry.listSuppliers(active).map { it.toResponse() })

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('tours-operator.supplier:manage')")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> = registry.supplier(id)?.let { noStore(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @PostMapping
    @PreAuthorize("hasAuthority('tours-operator.supplier:manage')")
    fun create(
        @Valid @RequestBody request: SupplierRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = registryResponse(registry.createSupplier(request.toInput(), actor(authentication)), true) { it.toResponse() }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tours-operator.supplier:manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SupplierRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val revision = request.expectedRevision ?: return missingRevision()
        return registryResponse(registry.updateSupplier(id, request.toInput(), revision, actor(authentication)), false) { it.toResponse() }
    }
}

/** Drivers (work phone + licence expiry only) and vehicles: `tours-operator.fleet:manage`. */
@Validated
@RestController("toursOperatorFleetController")
@RequestMapping("/api/v1/tours-operator/staff")
class FleetController(
    private val registry: OpsRegistryService,
) {
    @GetMapping("/drivers")
    @PreAuthorize("hasAuthority('tours-operator.fleet:manage')")
    fun drivers(
        @RequestParam(required = false) active: Boolean?,
    ): ResponseEntity<Any> = noStore(registry.listDrivers(active).map { it.toResponse() })

    @GetMapping("/drivers/{id}")
    @PreAuthorize("hasAuthority('tours-operator.fleet:manage')")
    fun driver(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> = registry.driver(id)?.let { noStore(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @PostMapping("/drivers")
    @PreAuthorize("hasAuthority('tours-operator.fleet:manage')")
    fun createDriver(
        @Valid @RequestBody request: DriverRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = registryResponse(registry.createDriver(request.toInput(), actor(authentication)), true) { it.toResponse() }

    @PutMapping("/drivers/{id}")
    @PreAuthorize("hasAuthority('tours-operator.fleet:manage')")
    fun updateDriver(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DriverRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val revision = request.expectedRevision ?: return missingRevision()
        return registryResponse(registry.updateDriver(id, request.toInput(), revision, actor(authentication)), false) { it.toResponse() }
    }

    @GetMapping("/vehicles")
    @PreAuthorize("hasAuthority('tours-operator.fleet:manage')")
    fun vehicles(
        @RequestParam(required = false) active: Boolean?,
    ): ResponseEntity<Any> = noStore(registry.listVehicles(active).map { it.toResponse() })

    @GetMapping("/vehicles/{id}")
    @PreAuthorize("hasAuthority('tours-operator.fleet:manage')")
    fun vehicle(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> = registry.vehicle(id)?.let { noStore(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @PostMapping("/vehicles")
    @PreAuthorize("hasAuthority('tours-operator.fleet:manage')")
    fun createVehicle(
        @Valid @RequestBody request: VehicleRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = registryResponse(registry.createVehicle(request.toInput(), actor(authentication)), true) { it.toResponse() }

    @PutMapping("/vehicles/{id}")
    @PreAuthorize("hasAuthority('tours-operator.fleet:manage')")
    fun updateVehicle(
        @PathVariable id: UUID,
        @Valid @RequestBody request: VehicleRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val revision = request.expectedRevision ?: return missingRevision()
        return registryResponse(registry.updateVehicle(id, request.toInput(), revision, actor(authentication)), false) { it.toResponse() }
    }
}

/**
 * Daily assignment of driver, vehicle and suppliers to a departure.
 * `tours-operator.assignment:manage` sees names only, never phones.
 */
@Validated
@RestController("toursOperatorAssignmentController")
@RequestMapping("/api/v1/tours-operator/staff")
class AssignmentController(
    private val assignments: AssignmentService,
) {
    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('tours-operator.assignment:manage')")
    fun day(
        @RequestParam date: LocalDate,
    ): ResponseEntity<Any> = noStore(assignments.forDay(date).map { it.toResponse() })

    @GetMapping("/assignment-options")
    @PreAuthorize("hasAuthority('tours-operator.assignment:manage')")
    fun options(
        @RequestParam date: LocalDate,
    ): ResponseEntity<Any> = noStore(assignments.options(date).toResponse())

    @GetMapping("/slots/{slotId}/assignment")
    @PreAuthorize("hasAuthority('tours-operator.assignment:manage')")
    fun get(
        @PathVariable slotId: UUID,
    ): ResponseEntity<Any> = assignments.forSlot(TourSlotId(slotId))?.let { noStore(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @GetMapping("/slots/{slotId}/assignment/history")
    @PreAuthorize("hasAuthority('tours-operator.assignment:manage')")
    fun history(
        @PathVariable slotId: UUID,
    ): ResponseEntity<Any> = assignments.history(TourSlotId(slotId))?.let { entries -> noStore(entries.map { it.toResponse() }) } ?: ResponseEntity.notFound().build()

    @PutMapping("/slots/{slotId}/assignment")
    @PreAuthorize("hasAuthority('tours-operator.assignment:manage')")
    fun assign(
        @PathVariable slotId: UUID,
        @Valid @RequestBody request: AssignmentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> =
        respond(
            assignments.assign(
                AssignCommand(TourSlotId(slotId), request.driverId, request.vehicleId, request.supplierIds, request.expectedRevision, actor(authentication)),
            ),
        )

    @DeleteMapping("/slots/{slotId}/assignment")
    @PreAuthorize("hasAuthority('tours-operator.assignment:manage')")
    fun clear(
        @PathVariable slotId: UUID,
        @RequestParam @Min(1) expectedRevision: Int,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(assignments.clear(TourSlotId(slotId), expectedRevision, actor(authentication)))

    private fun respond(result: AssignResult): ResponseEntity<Any> =
        when (result) {
            is AssignResult.Saved -> noStore(result.view.toResponse())
            AssignResult.NotFound -> ResponseEntity.notFound().build()
            is AssignResult.RevisionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(RevisionConflictBody(currentRevision = result.currentRevision))
            is AssignResult.Conflict ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(AssignmentConflictBody("assignment_conflict", result.conflicts.map { it.toResponse() }))
            is AssignResult.Refused ->
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(AssignmentRefusalBody(result.code, result.issues.map { it.toResponse() }))
        }
}
