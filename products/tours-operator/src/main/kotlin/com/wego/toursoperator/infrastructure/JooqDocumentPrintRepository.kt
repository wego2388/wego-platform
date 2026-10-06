package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import com.wego.generated.jooq.tables.ToursOperatorDocumentPrint.TOURS_OPERATOR_DOCUMENT_PRINT
import com.wego.generated.jooq.tables.ToursOperatorDocumentSequence.TOURS_OPERATOR_DOCUMENT_SEQUENCE
import com.wego.toursoperator.application.DocumentLanguage
import com.wego.toursoperator.application.DocumentPrintRecord
import com.wego.toursoperator.application.DocumentPrintRepository
import com.wego.toursoperator.application.DocumentType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository("stoDocumentPrintRepositoryImpl")
class JooqDocumentPrintRepository(
    private val dsl: DSLContext,
) : DocumentPrintRepository {
    private val t = TOURS_OPERATOR_DOCUMENT_PRINT

    @Transactional
    override fun lockSubject(
        type: DocumentType,
        subjectKey: String,
    ) {
        // Transaction-scoped advisory lock: concurrent prints of one subject queue up, so versions count 1, 2, 3 …
        // and the first print allocates exactly one number.
        dsl.execute("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "tours-operator-document:${type.name}:$subjectKey")
    }

    @Transactional(readOnly = true)
    override fun findOriginal(
        type: DocumentType,
        subjectKey: String,
    ): DocumentPrintRecord? = find(type, subjectKey, t.VERSION.eq(1))

    @Transactional(readOnly = true)
    override fun findLatest(
        type: DocumentType,
        subjectKey: String,
    ): DocumentPrintRecord? = find(type, subjectKey, DSL.noCondition(), latest = true)

    @Transactional(readOnly = true)
    override fun findAllForSubject(
        type: DocumentType,
        subjectKey: String,
    ): List<DocumentPrintRecord> =
        base()
            .where(t.DOCUMENT_TYPE.eq(type.name).and(t.SUBJECT_KEY.eq(subjectKey)))
            .orderBy(t.VERSION.asc())
            .fetch()
            .map(::toDomain)

    @Transactional
    override fun allocateNumber(
        type: DocumentType,
        year: Int,
    ): String {
        val s = TOURS_OPERATOR_DOCUMENT_SEQUENCE
        val value =
            dsl
                .insertInto(s)
                .set(s.DOCUMENT_TYPE, type.name)
                .set(s.SEQUENCE_YEAR, year)
                .set(s.LAST_VALUE, 1L)
                .onConflict(s.DOCUMENT_TYPE, s.SEQUENCE_YEAR)
                .doUpdate()
                .set(s.LAST_VALUE, s.LAST_VALUE.plus(1L))
                .returning(s.LAST_VALUE)
                .fetchOne(s.LAST_VALUE)
        return "${type.numberPrefix}-$year-${checkNotNull(value).toString().padStart(NUMBER_WIDTH, '0')}"
    }

    @Transactional
    override fun append(record: DocumentPrintRecord) {
        dsl
            .insertInto(t)
            .set(t.ID, record.id)
            .set(t.DOCUMENT_TYPE, record.type.name)
            .set(t.SUBJECT_KEY, record.subjectKey)
            .set(t.DOCUMENT_NUMBER, record.number)
            .set(t.VERSION, record.version)
            .set(t.LANGUAGE, record.language.code)
            .set(t.PRINTED_BY_USER_ID, record.printedByUserId)
            .set(t.PRINTED_AT, OffsetDateTime.ofInstant(record.printedAt, ZoneOffset.UTC))
            .execute()
    }

    @Transactional(readOnly = true)
    override fun emailOf(userId: UUID): String? =
        dsl
            .select(IDENTITY_USER.EMAIL)
            .from(IDENTITY_USER)
            .where(IDENTITY_USER.ID.eq(userId))
            .fetchOne(IDENTITY_USER.EMAIL)

    private fun base() =
        dsl
            .select(t.asterisk(), IDENTITY_USER.EMAIL)
            .from(t)
            .leftJoin(IDENTITY_USER)
            .on(IDENTITY_USER.ID.eq(t.PRINTED_BY_USER_ID))

    private fun find(
        type: DocumentType,
        subjectKey: String,
        extra: org.jooq.Condition,
        latest: Boolean = false,
    ): DocumentPrintRecord? =
        base()
            .where(
                t.DOCUMENT_TYPE
                    .eq(type.name)
                    .and(t.SUBJECT_KEY.eq(subjectKey))
                    .and(extra),
            ).orderBy(if (latest) t.VERSION.desc() else t.VERSION.asc())
            .limit(1)
            .fetchOne()
            ?.let(::toDomain)

    private fun toDomain(r: org.jooq.Record) =
        DocumentPrintRecord(
            id = r.get(t.ID),
            type = DocumentType.valueOf(r.get(t.DOCUMENT_TYPE)),
            subjectKey = r.get(t.SUBJECT_KEY),
            number = r.get(t.DOCUMENT_NUMBER),
            version = r.get(t.VERSION),
            language = checkNotNull(DocumentLanguage.fromCode(r.get(t.LANGUAGE))),
            printedByUserId = r.get(t.PRINTED_BY_USER_ID),
            printedByEmail = r.get(IDENTITY_USER.EMAIL),
            printedAt = r.get(t.PRINTED_AT).toInstant(),
        )

    private companion object {
        const val NUMBER_WIDTH = 6
    }
}
