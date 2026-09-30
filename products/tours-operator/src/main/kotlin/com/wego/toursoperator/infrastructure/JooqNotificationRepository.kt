package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorNotification.TOURS_OPERATOR_NOTIFICATION
import com.wego.generated.jooq.tables.records.ToursOperatorNotificationRecord
import com.wego.toursoperator.application.NotificationListItem
import com.wego.toursoperator.application.NotificationRepository
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.CustomerNotification
import com.wego.toursoperator.domain.NotificationId
import com.wego.toursoperator.domain.NotificationKind
import com.wego.toursoperator.domain.NotificationStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository("stoNotificationRepositoryImpl")
class JooqNotificationRepository(
    private val dsl: DSLContext,
) : NotificationRepository {
    @Transactional
    override fun enqueueOnce(
        bookingId: BookingId,
        kind: NotificationKind,
        availableAt: Instant,
        now: Instant,
    ): Boolean {
        val t = TOURS_OPERATOR_NOTIFICATION
        return dsl
            .insertInto(t)
            .set(t.ID, UUID.randomUUID())
            .set(t.BOOKING_ID, bookingId.value)
            .set(t.KIND, kind.name)
            .set(t.STATUS, NotificationStatus.PENDING.name)
            .set(t.ATTEMPT_COUNT, 0)
            .set(t.AVAILABLE_AT, toOffset(availableAt))
            .set(t.CREATED_AT, toOffset(now))
            .onConflict(t.BOOKING_ID, t.KIND)
            .doNothing()
            .execute() == 1
    }

    @Transactional
    override fun claimNextDue(now: Instant): CustomerNotification? {
        val t = TOURS_OPERATOR_NOTIFICATION
        return dsl
            .selectFrom(t)
            .where(t.STATUS.eq(NotificationStatus.PENDING.name))
            .and(t.AVAILABLE_AT.le(toOffset(now)))
            .orderBy(t.AVAILABLE_AT.asc(), t.ID.asc())
            .limit(1)
            .forUpdate()
            .skipLocked()
            .fetchOne()
            ?.let(::toDomain)
    }

    @Transactional
    override fun findByIdForUpdate(id: NotificationId): CustomerNotification? {
        val t = TOURS_OPERATOR_NOTIFICATION
        return dsl
            .selectFrom(t)
            .where(t.ID.eq(id.value))
            .forUpdate()
            .fetchOne()
            ?.let(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun list(
        status: NotificationStatus?,
        limit: Int,
        offset: Int,
    ): List<NotificationListItem> {
        val t = TOURS_OPERATOR_NOTIFICATION
        val b = TOURS_OPERATOR_BOOKING
        val condition =
            status?.let { t.STATUS.eq(it.name) } ?: org.jooq.impl.DSL
                .noCondition()
        return dsl
            .select(t.asterisk(), b.REFERENCE)
            .from(t)
            .join(b)
            .on(b.ID.eq(t.BOOKING_ID))
            .where(condition)
            .orderBy(t.CREATED_AT.desc(), t.ID.desc())
            .limit(limit)
            .offset(offset)
            .fetch { record ->
                NotificationListItem(
                    notification = toDomain(record.into(t)),
                    bookingReference = checkNotNull(record[b.REFERENCE]),
                )
            }
    }

    @Transactional
    override fun save(notification: CustomerNotification) {
        val t = TOURS_OPERATOR_NOTIFICATION
        dsl
            .update(t)
            .set(t.STATUS, notification.status.name)
            .set(t.ATTEMPT_COUNT, notification.attemptCount)
            .set(t.AVAILABLE_AT, toOffset(notification.availableAt))
            .set(t.SENT_AT, notification.sentAt?.let(::toOffset))
            .set(t.LAST_ERROR, notification.lastError)
            .set(t.RESEND_COUNT, notification.resendCount)
            .set(t.LAST_RESENT_BY_USER_ID, notification.lastResentByUserId)
            .set(t.LAST_RESENT_AT, notification.lastResentAt?.let(::toOffset))
            .where(t.ID.eq(notification.id.value))
            .execute()
    }

    private fun toDomain(r: ToursOperatorNotificationRecord): CustomerNotification =
        CustomerNotification(
            id = NotificationId(r.id),
            bookingId = BookingId(r.bookingId),
            kind = NotificationKind.valueOf(r.kind),
            status = NotificationStatus.valueOf(r.status),
            attemptCount = r.attemptCount,
            availableAt = r.availableAt.toInstant(),
            createdAt = r.createdAt.toInstant(),
            sentAt = r.sentAt?.toInstant(),
            lastError = r.lastError,
            resendCount = r.resendCount,
            lastResentByUserId = r.lastResentByUserId,
            lastResentAt = r.lastResentAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
