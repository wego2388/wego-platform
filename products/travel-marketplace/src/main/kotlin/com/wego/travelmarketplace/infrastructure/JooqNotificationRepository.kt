package com.wego.travelmarketplace.infrastructure

import com.wego.generated.jooq.tables.TravelRequest.TRAVEL_REQUEST
import com.wego.generated.jooq.tables.TravelRequestNotification.TRAVEL_REQUEST_NOTIFICATION
import com.wego.generated.jooq.tables.records.TravelRequestNotificationRecord
import com.wego.travelmarketplace.application.NotificationListItem
import com.wego.travelmarketplace.application.NotificationRepository
import com.wego.travelmarketplace.domain.NotificationId
import com.wego.travelmarketplace.domain.NotificationKind
import com.wego.travelmarketplace.domain.NotificationStatus
import com.wego.travelmarketplace.domain.RequestNotification
import com.wego.travelmarketplace.domain.TravelRequestId
import org.jooq.DSLContext
import org.jooq.UpdateConditionStep
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqNotificationRepository(
    private val dsl: DSLContext,
) : NotificationRepository {
    @Transactional
    override fun enqueueOnce(
        requestId: TravelRequestId,
        kind: NotificationKind,
        availableAt: Instant,
        now: Instant,
    ): Boolean {
        val t = TRAVEL_REQUEST_NOTIFICATION
        return dsl
            .insertInto(t)
            .set(t.ID, UUID.randomUUID())
            .set(t.REQUEST_ID, requestId.value)
            .set(t.KIND, kind.name)
            .set(t.STATUS, NotificationStatus.PENDING.name)
            .set(t.ATTEMPT_COUNT, 0)
            .set(t.AVAILABLE_AT, toOffset(availableAt))
            .set(t.CREATED_AT, toOffset(now))
            .onConflict(t.REQUEST_ID, t.KIND)
            .doNothing()
            .execute() == 1
    }

    @Transactional(readOnly = true)
    override fun exists(
        requestId: TravelRequestId,
        kind: NotificationKind,
    ): Boolean {
        val t = TRAVEL_REQUEST_NOTIFICATION
        return dsl.fetchExists(t, t.REQUEST_ID.eq(requestId.value).and(t.KIND.eq(kind.name)))
    }

    @Transactional
    override fun claimNextDue(now: Instant): RequestNotification? {
        val t = TRAVEL_REQUEST_NOTIFICATION
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
    override fun findByIdForUpdate(id: NotificationId): RequestNotification? {
        val t = TRAVEL_REQUEST_NOTIFICATION
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
        val t = TRAVEL_REQUEST_NOTIFICATION
        val r = TRAVEL_REQUEST
        val condition = status?.let { t.STATUS.eq(it.name) } ?: DSL.noCondition()
        return dsl
            .select(t.asterisk(), r.REFERENCE)
            .from(t)
            .join(r)
            .on(r.ID.eq(t.REQUEST_ID))
            .where(condition)
            .orderBy(t.CREATED_AT.desc(), t.ID.desc())
            .limit(limit)
            .offset(offset)
            .fetch { record ->
                NotificationListItem(
                    notification = toDomain(record.into(t)),
                    requestReference = checkNotNull(record[r.REFERENCE]),
                )
            }
    }

    @Transactional
    override fun save(notification: RequestNotification) {
        update(notification).execute()
    }

    @Transactional
    override fun recordOutcome(
        notification: RequestNotification,
        expectedAttemptCount: Int,
    ): Boolean {
        val t = TRAVEL_REQUEST_NOTIFICATION
        return update(notification)
            .and(t.STATUS.eq(NotificationStatus.PENDING.name))
            .and(t.ATTEMPT_COUNT.eq(expectedAttemptCount))
            // A staff resend resets the attempt count; without this a stale
            // outcome from before the resend could match a later claim.
            .and(t.RESEND_COUNT.eq(notification.resendCount))
            .execute() == 1
    }

    private fun update(notification: RequestNotification): UpdateConditionStep<TravelRequestNotificationRecord> {
        val t = TRAVEL_REQUEST_NOTIFICATION
        return dsl
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
    }

    private fun toDomain(r: TravelRequestNotificationRecord): RequestNotification =
        RequestNotification(
            id = NotificationId(r.id),
            requestId = TravelRequestId(r.requestId),
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
