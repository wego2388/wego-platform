package com.wego.divers.infrastructure

import com.wego.divers.application.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

// Named distinctly from com.wego.identity.infrastructure.SpringTransactionRunner:
// Spring's default component-scan bean naming uses the simple class name, and
// two beans named "springTransactionRunner" from different modules collide at
// context startup.
@Component
class DiversSpringTransactionRunner(
    transactionManager: PlatformTransactionManager,
) : TransactionRunner {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun <T> runInTransaction(block: () -> T): T =
        transactionTemplate.execute { block() }
            ?: error("Transaction callback returned null unexpectedly")
}
