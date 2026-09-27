package com.wego.toursoperator.application

/**
 * Local copy of the TransactionRunner contract — same reasoning as in
 * products/divers: the platform's SpringTransactionRunner lives inside
 * com.wego.identity.infrastructure, which is not a Modulith-public
 * boundary this module can import. Promote to platform/ if a third module
 * needs the same contract.
 */
interface TransactionRunner {
    fun <T> runInTransaction(block: () -> T): T
}
