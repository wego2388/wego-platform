package com.wego.divers.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class Diver(
    val id: DiverId,
    val fullName: String,
    val nationality: String?,
    val primaryLanguage: String?,
    val email: String?,
    val phone: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val medicalNotes: String?,
    val totalLoggedDives: Int,
    val maxDepthMeters: BigDecimal?,
    val lastDiveOn: LocalDate?,
    val bcdSize: String?,
    val finSize: String?,
    val wetsuitSize: String?,
    val certifications: List<DiverCertification>,
    status: DiverStatus,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    archivedAt: Instant?,
) {
    var status: DiverStatus = status
        private set

    var archivedAt: Instant? = archivedAt
        private set

    init {
        require(fullName.isNotBlank()) { "Diver full name must not be blank" }
        // isNullOrBlank, not != null: a blank-but-present string must not
        // satisfy "at least one contact is present" — mirrors CustomerContact.
        require(!email.isNullOrBlank() || !phone.isNullOrBlank()) { "Diver contact must include an email or a phone number" }
        require(totalLoggedDives >= 0) { "Total logged dives must not be negative" }
        require(maxDepthMeters == null || maxDepthMeters >= BigDecimal.ZERO) { "Max depth must not be negative when present" }
        require((status == DiverStatus.ARCHIVED) == (archivedAt != null)) {
            "archivedAt must be set if and only if the diver profile is archived"
        }
    }

    val isActive: Boolean get() = status == DiverStatus.ACTIVE

    /** Terminal: an already-archived profile cannot be archived again. */
    fun archive(now: Instant) {
        require(status == DiverStatus.ACTIVE) { "Only an active diver profile can be archived" }
        status = DiverStatus.ARCHIVED
        archivedAt = now
    }

    /** Every field is otherwise immutable — an edit is a new value carrying the same identity, status, and creation metadata forward. */
    fun withUpdatedProfile(
        fullName: String,
        nationality: String?,
        primaryLanguage: String?,
        email: String?,
        phone: String?,
        emergencyContactName: String?,
        emergencyContactPhone: String?,
        medicalNotes: String?,
        totalLoggedDives: Int,
        maxDepthMeters: BigDecimal?,
        lastDiveOn: LocalDate?,
        bcdSize: String?,
        finSize: String?,
        wetsuitSize: String?,
        certifications: List<DiverCertification>,
    ): Diver =
        Diver(
            id = id,
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
            certifications = certifications,
            status = status,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            archivedAt = archivedAt,
        )

    companion object {
        fun create(
            id: DiverId,
            fullName: String,
            nationality: String?,
            primaryLanguage: String?,
            email: String?,
            phone: String?,
            emergencyContactName: String?,
            emergencyContactPhone: String?,
            medicalNotes: String?,
            totalLoggedDives: Int,
            maxDepthMeters: BigDecimal?,
            lastDiveOn: LocalDate?,
            bcdSize: String?,
            finSize: String?,
            wetsuitSize: String?,
            certifications: List<DiverCertification>,
            createdByUserId: UUID?,
            now: Instant,
        ): Diver =
            Diver(
                id = id,
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
                certifications = certifications,
                status = DiverStatus.ACTIVE,
                createdByUserId = createdByUserId,
                createdAt = now,
                archivedAt = null,
            )
    }
}
