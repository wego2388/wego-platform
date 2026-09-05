package com.wego.payroll.application

import com.wego.payroll.domain.PayrollRun
import com.wego.payroll.domain.PayrollRunId
import com.wego.payroll.domain.PayrollRunStatus

class PayrollRunQueryService(
    private val payrollRunRepository: PayrollRunRepository,
) {
    fun findById(id: PayrollRunId): PayrollRun? = payrollRunRepository.findById(id)

    fun list(
        status: PayrollRunStatus?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<PayrollRun> =
        payrollRunRepository.findAll(
            status,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
