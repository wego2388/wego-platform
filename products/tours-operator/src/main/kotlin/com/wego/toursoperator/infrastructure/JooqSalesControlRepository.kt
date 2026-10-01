package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorSalesControl.TOURS_OPERATOR_SALES_CONTROL
import com.wego.toursoperator.application.SalesControlRepository
import com.wego.toursoperator.domain.SalesControl
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository("stoSalesControlRepositoryImpl")
class JooqSalesControlRepository(
    private val dsl: DSLContext,
) : SalesControlRepository {
    private val t = TOURS_OPERATOR_SALES_CONTROL

    @Transactional(readOnly = true)
    override fun current(): SalesControl =
        dsl
            .selectFrom(t)
            .where(t.ID.eq(SINGLETON_ID))
            .fetchOne()
            ?.let {
                SalesControl(
                    bookingsPaused = it.bookingsPaused,
                    paymentsPaused = it.paymentsPaused,
                    reason = it.reason,
                    updatedByUserId = it.updatedByUserId,
                    updatedAt = it.updatedAt.toInstant(),
                )
            } ?: SalesControl.OPEN

    @Transactional
    override fun save(control: SalesControl) {
        val updatedAt = OffsetDateTime.ofInstant(requireNotNull(control.updatedAt), ZoneOffset.UTC)
        dsl
            .insertInto(t)
            .set(t.ID, SINGLETON_ID)
            .set(t.BOOKINGS_PAUSED, control.bookingsPaused)
            .set(t.PAYMENTS_PAUSED, control.paymentsPaused)
            .set(t.REASON, control.reason)
            .set(t.UPDATED_BY_USER_ID, control.updatedByUserId)
            .set(t.UPDATED_AT, updatedAt)
            .onConflict(t.ID)
            .doUpdate()
            .set(t.BOOKINGS_PAUSED, control.bookingsPaused)
            .set(t.PAYMENTS_PAUSED, control.paymentsPaused)
            .set(t.REASON, control.reason)
            .set(t.UPDATED_BY_USER_ID, control.updatedByUserId)
            .set(t.UPDATED_AT, updatedAt)
            .execute()
    }

    private companion object {
        const val SINGLETON_ID: Short = 1
    }
}
