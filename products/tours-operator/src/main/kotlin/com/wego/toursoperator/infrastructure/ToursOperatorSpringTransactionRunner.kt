package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component("toursOperatorSpringTransactionRunner")
class ToursOperatorSpringTransactionRunner(
    private val transactionTemplate: TransactionTemplate,
) : TransactionRunner {
    override fun <T> runInTransaction(block: () -> T): T =
        transactionTemplate.execute { block() }
            ?: error("TransactionTemplate returned null — check transaction configuration")
}
