package com.wego.transaction

/**
 * Establishes the single transaction boundary for a use case. Individual
 * repository methods keep their own `@Transactional` annotations (default
 * `REQUIRED` propagation), so calls made inside [runInTransaction] join this
 * one transaction instead of each committing independently — a partial
 * failure between, say, a user update and a session insert rolls both back
 * together instead of leaving one committed and the other not.
 *
 * Promoted here from two identical per-module duplicates
 * (`com.wego.identity.application.TransactionRunner`,
 * `com.wego.divers.application.TransactionRunner`) once a third module
 * (`com.wego.hr`) needed the same contract — the exact threshold those two
 * duplicates' own doc comments named in advance.
 */
interface TransactionRunner {
    fun <T> runInTransaction(block: () -> T): T
}
