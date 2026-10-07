package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorFxRate.TOURS_OPERATOR_FX_RATE
import com.wego.generated.jooq.tables.records.ToursOperatorFxRateRecord
import com.wego.toursoperator.application.FxRateRepository
import com.wego.toursoperator.domain.FxRate
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository("stoFxRateRepositoryImpl")
class JooqFxRateRepository(
    private val dsl: DSLContext,
) : FxRateRepository {
    private val t = TOURS_OPERATOR_FX_RATE

    @Transactional(readOnly = true)
    override fun latestFor(date: LocalDate): FxRate? =
        dsl
            .selectFrom(t)
            .where(t.RATE_DATE.eq(date))
            .orderBy(t.SET_AT.desc(), t.ID.desc())
            .limit(1)
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun latestBefore(date: LocalDate): FxRate? =
        dsl
            .selectFrom(t)
            .where(t.RATE_DATE.lt(date))
            .orderBy(t.RATE_DATE.desc(), t.SET_AT.desc(), t.ID.desc())
            .limit(1)
            .fetchOne()
            ?.let(::toDomain)

    @Transactional
    override fun append(rate: FxRate) {
        dsl
            .insertInto(t)
            .set(t.ID, rate.id)
            .set(t.RATE_DATE, rate.rateDate)
            .set(t.EGP_PER_EUR, rate.egpPerEur)
            .set(t.SET_BY_USER_ID, rate.setByUserId)
            .set(t.SET_AT, OffsetDateTime.ofInstant(rate.setAt, ZoneOffset.UTC))
            .execute()
    }

    @Transactional(readOnly = true)
    override fun history(limit: Int): List<FxRate> =
        dsl
            .selectFrom(t)
            .orderBy(t.SET_AT.desc(), t.ID.desc())
            .limit(limit)
            .fetch()
            .map(::toDomain)

    private fun toDomain(record: ToursOperatorFxRateRecord) =
        FxRate(
            id = record.id,
            rateDate = record.rateDate,
            egpPerEur = record.egpPerEur,
            setByUserId = record.setByUserId,
            setAt = record.setAt.toInstant(),
        )
}
