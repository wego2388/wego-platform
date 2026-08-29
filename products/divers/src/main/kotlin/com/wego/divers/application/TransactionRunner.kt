package com.wego.divers.application

/**
 * A divers-local duplicate of `com.wego.identity.application.TransactionRunner`.
 * That type lives at `com.wego.identity.application`, not its module's
 * Modulith-public root (`com.wego.identity`), so this module cannot import
 * it — same constraint WEGO-001 hit with `PermissionCode`. Promote to a
 * shared location if a third module ends up needing the same contract.
 */
interface TransactionRunner {
    fun <T> runInTransaction(block: () -> T): T
}
