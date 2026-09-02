package com.wego.travelmarketplace.application

/**
 * A travel-marketplace-local duplicate of `com.wego.identity.application.TransactionRunner`
 * (and of `com.wego.divers.application.TransactionRunner`, which duplicates it
 * for the same reason). That type lives at `com.wego.identity.application`,
 * not its module's Modulith-public root (`com.wego.identity`), so this
 * module cannot import it — same constraint WEGO-001 hit with
 * `PermissionCode`, and WEGO-011 hit again for Divers. Promote to a shared
 * location if a fourth module ends up needing the same contract.
 */
interface TransactionRunner {
    fun <T> runInTransaction(block: () -> T): T
}
