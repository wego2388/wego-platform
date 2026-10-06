package com.wego.toursoperator.application

import java.util.UUID

/** Append-only register of printed documents; intentionally no update or delete. */
interface DocumentPrintRepository {
    /** Serialises concurrent prints of the same subject for the rest of the transaction. */
    fun lockSubject(
        type: DocumentType,
        subjectKey: String,
    )

    fun findOriginal(
        type: DocumentType,
        subjectKey: String,
    ): DocumentPrintRecord?

    fun findLatest(
        type: DocumentType,
        subjectKey: String,
    ): DocumentPrintRecord?

    /** The next number of [type] for [year], e.g. RCT-2026-000007. Increments under a row lock; call inside the printing transaction. */
    fun allocateNumber(
        type: DocumentType,
        year: Int,
    ): String

    fun append(record: DocumentPrintRecord)

    fun findAllForSubject(
        type: DocumentType,
        subjectKey: String,
    ): List<DocumentPrintRecord>

    fun emailOf(userId: UUID): String?
}
