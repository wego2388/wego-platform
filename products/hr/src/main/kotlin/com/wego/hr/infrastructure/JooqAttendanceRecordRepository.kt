package com.wego.hr.infrastructure

import com.wego.generated.jooq.tables.HrAttendanceRecord.HR_ATTENDANCE_RECORD
import com.wego.generated.jooq.tables.records.HrAttendanceRecordRecord
import com.wego.hr.application.AttendanceRecordRepository
import com.wego.hr.domain.AttendanceRecord
import com.wego.hr.domain.AttendanceRecordId
import com.wego.hr.domain.AttendanceStatus
import com.wego.hr.domain.EmployeeId
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqAttendanceRecordRepository(
    private val dsl: DSLContext,
) : AttendanceRecordRepository {
    @Transactional(readOnly = true)
    override fun findByEmployeeAndDate(
        employeeId: EmployeeId,
        date: LocalDate,
    ): AttendanceRecord? {
        val record =
            dsl
                .selectFrom(HR_ATTENDANCE_RECORD)
                .where(HR_ATTENDANCE_RECORD.EMPLOYEE_ID.eq(employeeId.value))
                .and(HR_ATTENDANCE_RECORD.ATTENDANCE_DATE.eq(date))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        employeeId: EmployeeId?,
        from: LocalDate?,
        to: LocalDate?,
        limit: Int,
        offset: Int,
    ): List<AttendanceRecord> {
        var condition = DSL.noCondition()
        if (employeeId != null) condition = condition.and(HR_ATTENDANCE_RECORD.EMPLOYEE_ID.eq(employeeId.value))
        if (from != null) condition = condition.and(HR_ATTENDANCE_RECORD.ATTENDANCE_DATE.ge(from))
        if (to != null) condition = condition.and(HR_ATTENDANCE_RECORD.ATTENDANCE_DATE.le(to))
        return dsl
            .selectFrom(HR_ATTENDANCE_RECORD)
            .where(condition)
            // ID as a tie-breaker keeps offset pagination deterministic
            // across two page queries, same reasoning as JooqEmployeeRepository.
            .orderBy(HR_ATTENDANCE_RECORD.ATTENDANCE_DATE.desc(), HR_ATTENDANCE_RECORD.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(record: AttendanceRecord) {
        dsl
            .insertInto(HR_ATTENDANCE_RECORD)
            .set(HR_ATTENDANCE_RECORD.ID, record.id.value)
            .set(HR_ATTENDANCE_RECORD.EMPLOYEE_ID, record.employeeId.value)
            .set(HR_ATTENDANCE_RECORD.ATTENDANCE_DATE, record.attendanceDate)
            .set(HR_ATTENDANCE_RECORD.STATUS, record.status.name)
            .set(HR_ATTENDANCE_RECORD.CLOCK_IN, record.clockIn?.let(::toOffset))
            .set(HR_ATTENDANCE_RECORD.CLOCK_OUT, record.clockOut?.let(::toOffset))
            .set(HR_ATTENDANCE_RECORD.NOTES, record.notes)
            .set(HR_ATTENDANCE_RECORD.CREATED_BY_USER_ID, record.createdByUserId)
            .set(HR_ATTENDANCE_RECORD.CREATED_AT, toOffset(record.createdAt))
            .set(HR_ATTENDANCE_RECORD.UPDATED_AT, toOffset(record.updatedAt))
            .onConflict(HR_ATTENDANCE_RECORD.ID)
            .doUpdate()
            .set(HR_ATTENDANCE_RECORD.STATUS, record.status.name)
            .set(HR_ATTENDANCE_RECORD.CLOCK_IN, record.clockIn?.let(::toOffset))
            .set(HR_ATTENDANCE_RECORD.CLOCK_OUT, record.clockOut?.let(::toOffset))
            .set(HR_ATTENDANCE_RECORD.NOTES, record.notes)
            .set(HR_ATTENDANCE_RECORD.UPDATED_AT, toOffset(record.updatedAt))
            .execute()
    }

    private fun toDomain(record: HrAttendanceRecordRecord): AttendanceRecord =
        AttendanceRecord(
            id = AttendanceRecordId(record.id),
            employeeId = EmployeeId(record.employeeId),
            attendanceDate = record.attendanceDate,
            status = AttendanceStatus.valueOf(record.status),
            clockIn = record.clockIn?.toInstant(),
            clockOut = record.clockOut?.toInstant(),
            notes = record.notes,
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            updatedAt = record.updatedAt.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
