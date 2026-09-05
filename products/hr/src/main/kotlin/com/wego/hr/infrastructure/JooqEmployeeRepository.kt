package com.wego.hr.infrastructure

import com.wego.generated.jooq.tables.HrEmployee.HR_EMPLOYEE
import com.wego.generated.jooq.tables.records.HrEmployeeRecord
import com.wego.hr.application.EmployeeRepository
import com.wego.hr.domain.Employee
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.EmployeeStatus
import com.wego.hr.domain.Money
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqEmployeeRepository(
    private val dsl: DSLContext,
) : EmployeeRepository {
    @Transactional(readOnly = true)
    override fun findById(id: EmployeeId): Employee? {
        val record = dsl.selectFrom(HR_EMPLOYEE).where(HR_EMPLOYEE.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: EmployeeId): Employee? {
        val record =
            dsl
                .selectFrom(HR_EMPLOYEE)
                .where(HR_EMPLOYEE.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        status: EmployeeStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Employee> {
        var condition = DSL.noCondition()
        if (status != null) {
            condition = condition.and(HR_EMPLOYEE.STATUS.eq(status.name))
        }
        if (!search.isNullOrBlank()) {
            condition = condition.and(HR_EMPLOYEE.FULL_NAME.containsIgnoreCase(search.trim()))
        }
        return dsl
            .selectFrom(HR_EMPLOYEE)
            .where(condition)
            // FULL_NAME alone is not unique — ID as a tie-breaker keeps offset
            // pagination deterministic across two page queries, same
            // reasoning as JooqOfferingRepository/JooqDiverRepository.findAll.
            .orderBy(HR_EMPLOYEE.FULL_NAME, HR_EMPLOYEE.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun countByStatus(status: EmployeeStatus): Int =
        dsl.fetchCount(dsl.selectFrom(HR_EMPLOYEE).where(HR_EMPLOYEE.STATUS.eq(status.name)))

    @Transactional
    override fun save(employee: Employee) {
        dsl
            .insertInto(HR_EMPLOYEE)
            .set(HR_EMPLOYEE.ID, employee.id.value)
            .set(HR_EMPLOYEE.FULL_NAME, employee.fullName)
            .set(HR_EMPLOYEE.POSITION, employee.position)
            .set(HR_EMPLOYEE.DEPARTMENT, employee.department)
            .set(HR_EMPLOYEE.HIRE_DATE, employee.hireDate)
            .set(HR_EMPLOYEE.EMAIL, employee.email)
            .set(HR_EMPLOYEE.PHONE, employee.phone)
            .set(HR_EMPLOYEE.BASE_SALARY_AMOUNT, employee.baseSalary?.amount)
            .set(HR_EMPLOYEE.BASE_SALARY_CURRENCY_CODE, employee.baseSalary?.currencyCode)
            .set(HR_EMPLOYEE.LINKED_USER_ID, employee.linkedUserId)
            .set(HR_EMPLOYEE.STATUS, employee.status.name)
            .set(HR_EMPLOYEE.CREATED_BY_USER_ID, employee.createdByUserId)
            .set(HR_EMPLOYEE.CREATED_AT, toOffset(employee.createdAt))
            .set(HR_EMPLOYEE.TERMINATED_AT, employee.terminatedAt?.let(::toOffset))
            .onConflict(HR_EMPLOYEE.ID)
            .doUpdate()
            .set(HR_EMPLOYEE.FULL_NAME, employee.fullName)
            .set(HR_EMPLOYEE.POSITION, employee.position)
            .set(HR_EMPLOYEE.DEPARTMENT, employee.department)
            .set(HR_EMPLOYEE.EMAIL, employee.email)
            .set(HR_EMPLOYEE.PHONE, employee.phone)
            .set(HR_EMPLOYEE.BASE_SALARY_AMOUNT, employee.baseSalary?.amount)
            .set(HR_EMPLOYEE.BASE_SALARY_CURRENCY_CODE, employee.baseSalary?.currencyCode)
            .set(HR_EMPLOYEE.LINKED_USER_ID, employee.linkedUserId)
            .set(HR_EMPLOYEE.STATUS, employee.status.name)
            .set(HR_EMPLOYEE.TERMINATED_AT, employee.terminatedAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: HrEmployeeRecord): Employee =
        Employee(
            id = EmployeeId(record.id),
            fullName = record.fullName,
            position = record.position,
            department = record.department,
            hireDate = record.hireDate,
            email = record.email,
            phone = record.phone,
            baseSalary =
                if (record.baseSalaryAmount != null && record.baseSalaryCurrencyCode != null) {
                    Money(record.baseSalaryAmount, record.baseSalaryCurrencyCode)
                } else {
                    null
                },
            linkedUserId = record.linkedUserId,
            status = EmployeeStatus.valueOf(record.status),
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            terminatedAt = record.terminatedAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
