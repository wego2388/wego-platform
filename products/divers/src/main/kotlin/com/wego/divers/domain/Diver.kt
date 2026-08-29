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
    emergencyContactName: String?,
    emergencyContactPhone: String?,
    medicalNotes: String?,
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

    /**
     * Redacted (set to null) at archive time — see [archive]. This is this
     * project's retention/deletion answer for sensitive diver PII that has
     * no other lifecycle: once the relationship with a diver has ended,
     * their emergency contact and medical notes stop being retained
     * indefinitely rather than sitting in the database forever with no
     * documented handling policy.
     */
    var emergencyContactName: String? = emergencyContactName
        private set

    var emergencyContactPhone: String? = emergencyContactPhone
        private set

    var medicalNotes: String? = medicalNotes
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

    /**
     * Terminal: an already-archived profile cannot be archived again. Also
     * redacts emergency-contact and medical-notes PII — see the fields'
     * own doc comment for why.
     */
    fun archive(now: Instant) {
        require(status == DiverStatus.ACTIVE) { "Only an active diver profile can be archived" }
        status = DiverStatus.ARCHIVED
        archivedAt = now
        emergencyContactName = null
        emergencyContactPhone = null
        medicalNotes = null
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
