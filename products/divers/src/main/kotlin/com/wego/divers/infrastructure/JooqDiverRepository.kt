package com.wego.divers.infrastructure

import com.wego.divers.application.DiverRepository
import com.wego.divers.domain.Diver
import com.wego.divers.domain.DiverCertification
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.DiverStatus
import com.wego.generated.jooq.tables.DiversDiver.DIVERS_DIVER
import com.wego.generated.jooq.tables.DiversDiverCertification.DIVERS_DIVER_CERTIFICATION
import com.wego.generated.jooq.tables.records.DiversDiverCertificationRecord
import com.wego.generated.jooq.tables.records.DiversDiverRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqDiverRepository(
    private val dsl: DSLContext,
) : DiverRepository {
    @Transactional(readOnly = true)
    override fun findById(id: DiverId): Diver? {
        val record = dsl.selectFrom(DIVERS_DIVER).where(DIVERS_DIVER.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record, certificationsFor(id))
    }

    @Transactional(readOnly = true)
    override fun findAll(
        status: DiverStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Diver> {
        var condition = DSL.noCondition()
        if (status != null) {
            condition = condition.and(DIVERS_DIVER.STATUS.eq(status.name))
        }
        if (!search.isNullOrBlank()) {
            condition = condition.and(DIVERS_DIVER.FULL_NAME.containsIgnoreCase(search.trim()))
        }
        val records =
            dsl
                .selectFrom(DIVERS_DIVER)
                .where(condition)
                // FULL_NAME alone is not unique — ID as a tie-breaker keeps
                // offset pagination deterministic across two page queries,
                // same reasoning as JooqOfferingRepository.findAll.
                .orderBy(DIVERS_DIVER.FULL_NAME, DIVERS_DIVER.ID)
                .limit(limit)
                .offset(offset)
                .fetch()
        val idsOnPage = records.map { DiverId(it.id) }
        val certsByDiver = certificationsForMany(idsOnPage)
        return records.map { toDomain(it, certsByDiver[DiverId(it.id)] ?: emptyList()) }
    }

    @Transactional
    override fun save(diver: Diver) {
        dsl
            .insertInto(DIVERS_DIVER)
            .set(DIVERS_DIVER.ID, diver.id.value)
            .set(DIVERS_DIVER.FULL_NAME, diver.fullName)
            .set(DIVERS_DIVER.NATIONALITY, diver.nationality)
            .set(DIVERS_DIVER.PRIMARY_LANGUAGE, diver.primaryLanguage)
            .set(DIVERS_DIVER.EMAIL, diver.email)
            .set(DIVERS_DIVER.PHONE, diver.phone)
            .set(DIVERS_DIVER.EMERGENCY_CONTACT_NAME, diver.emergencyContactName)
            .set(DIVERS_DIVER.EMERGENCY_CONTACT_PHONE, diver.emergencyContactPhone)
            .set(DIVERS_DIVER.MEDICAL_NOTES, diver.medicalNotes)
            .set(DIVERS_DIVER.TOTAL_LOGGED_DIVES, diver.totalLoggedDives)
            .set(DIVERS_DIVER.MAX_DEPTH_METERS, diver.maxDepthMeters)
            .set(DIVERS_DIVER.LAST_DIVE_ON, diver.lastDiveOn)
            .set(DIVERS_DIVER.BCD_SIZE, diver.bcdSize)
            .set(DIVERS_DIVER.FIN_SIZE, diver.finSize)
            .set(DIVERS_DIVER.WETSUIT_SIZE, diver.wetsuitSize)
            .set(DIVERS_DIVER.STATUS, diver.status.name)
            .set(DIVERS_DIVER.CREATED_BY_USER_ID, diver.createdByUserId)
            .set(DIVERS_DIVER.CREATED_AT, toOffset(diver.createdAt))
            .set(DIVERS_DIVER.ARCHIVED_AT, diver.archivedAt?.let(::toOffset))
            .onConflict(DIVERS_DIVER.ID)
            .doUpdate()
            .set(DIVERS_DIVER.FULL_NAME, diver.fullName)
            .set(DIVERS_DIVER.NATIONALITY, diver.nationality)
            .set(DIVERS_DIVER.PRIMARY_LANGUAGE, diver.primaryLanguage)
            .set(DIVERS_DIVER.EMAIL, diver.email)
            .set(DIVERS_DIVER.PHONE, diver.phone)
            .set(DIVERS_DIVER.EMERGENCY_CONTACT_NAME, diver.emergencyContactName)
            .set(DIVERS_DIVER.EMERGENCY_CONTACT_PHONE, diver.emergencyContactPhone)
            .set(DIVERS_DIVER.MEDICAL_NOTES, diver.medicalNotes)
            .set(DIVERS_DIVER.TOTAL_LOGGED_DIVES, diver.totalLoggedDives)
            .set(DIVERS_DIVER.MAX_DEPTH_METERS, diver.maxDepthMeters)
            .set(DIVERS_DIVER.LAST_DIVE_ON, diver.lastDiveOn)
            .set(DIVERS_DIVER.BCD_SIZE, diver.bcdSize)
            .set(DIVERS_DIVER.FIN_SIZE, diver.finSize)
            .set(DIVERS_DIVER.WETSUIT_SIZE, diver.wetsuitSize)
            .set(DIVERS_DIVER.STATUS, diver.status.name)
            .set(DIVERS_DIVER.ARCHIVED_AT, diver.archivedAt?.let(::toOffset))
            .execute()

        // Small, fully-owned child collection: replace-in-place is simpler
        // and just as correct as a diff, and this repository has no
        // concurrent writer to race against (unlike offering capacity).
        dsl.deleteFrom(DIVERS_DIVER_CERTIFICATION).where(DIVERS_DIVER_CERTIFICATION.DIVER_ID.eq(diver.id.value)).execute()
        diver.certifications.forEach { certification ->
            dsl
                .insertInto(DIVERS_DIVER_CERTIFICATION)
                .set(DIVERS_DIVER_CERTIFICATION.ID, certification.id)
                .set(DIVERS_DIVER_CERTIFICATION.DIVER_ID, diver.id.value)
                .set(DIVERS_DIVER_CERTIFICATION.AGENCY, certification.agency)
                .set(DIVERS_DIVER_CERTIFICATION.CERTIFICATION_LEVEL, certification.level)
                .set(DIVERS_DIVER_CERTIFICATION.CERTIFICATION_NUMBER, certification.certificationNumber)
                .set(DIVERS_DIVER_CERTIFICATION.ISSUED_ON, certification.issuedOn)
                .execute()
        }
    }

    private fun certificationsFor(id: DiverId): List<DiverCertification> = certificationsForMany(listOf(id))[id] ?: emptyList()

    private fun certificationsForMany(ids: List<DiverId>): Map<DiverId, List<DiverCertification>> {
        if (ids.isEmpty()) return emptyMap()
        return dsl
            .selectFrom(DIVERS_DIVER_CERTIFICATION)
            .where(DIVERS_DIVER_CERTIFICATION.DIVER_ID.`in`(ids.map { it.value }))
            .fetch()
            .map(::toCertification)
            .groupBy { DiverId(it.first) }
            .mapValues { (_, pairs) -> pairs.map { it.second } }
    }

    private fun toCertification(record: DiversDiverCertificationRecord): Pair<UUID, DiverCertification> =
        record.diverId to
            DiverCertification(
                id = record.id,
                agency = record.agency,
                level = record.certificationLevel,
                certificationNumber = record.certificationNumber,
                issuedOn = record.issuedOn,
            )

    private fun toDomain(
        record: DiversDiverRecord,
        certifications: List<DiverCertification>,
    ): Diver =
        Diver(
            id = DiverId(record.id),
            fullName = record.fullName,
            nationality = record.nationality,
            primaryLanguage = record.primaryLanguage,
            email = record.email,
            phone = record.phone,
            emergencyContactName = record.emergencyContactName,
            emergencyContactPhone = record.emergencyContactPhone,
            medicalNotes = record.medicalNotes,
            totalLoggedDives = record.totalLoggedDives,
            maxDepthMeters = record.maxDepthMeters,
            lastDiveOn = record.lastDiveOn,
            bcdSize = record.bcdSize,
            finSize = record.finSize,
            wetsuitSize = record.wetsuitSize,
            certifications = certifications,
            status = DiverStatus.valueOf(record.status),
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            archivedAt = record.archivedAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
