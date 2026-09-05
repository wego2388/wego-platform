package com.wego.transaction.infrastructure

import com.wego.transaction.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class SpringTransactionRunner(
    transactionManager: PlatformTransactionManager,
) : TransactionRunner {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun <T> runInTransaction(block: () -> T): T =
        transactionTemplate.execute { block() }
            ?: error("Transaction callback returned null unexpectedly")
}
