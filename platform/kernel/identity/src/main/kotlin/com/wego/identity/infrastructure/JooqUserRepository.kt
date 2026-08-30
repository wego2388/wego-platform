package com.wego.identity.infrastructure

import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import com.wego.generated.jooq.tables.IdentityUserRole.IDENTITY_USER_ROLE
import com.wego.generated.jooq.tables.records.IdentityUserRecord
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.HashedPassword
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqUserRepository(
    private val dsl: DSLContext,
) : UserRepository {
    @Transactional(readOnly = true)
    override fun findByEmail(email: EmailAddress): User? {
        val record =
            dsl
                .selectFrom(IDENTITY_USER)
                .where(IDENTITY_USER.EMAIL.eq(email.value))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByEmailForUpdate(email: EmailAddress): User? {
        val record =
            dsl
                .selectFrom(IDENTITY_USER)
                .where(IDENTITY_USER.EMAIL.eq(email.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findById(id: UserId): User? {
        val record =
            dsl
                .selectFrom(IDENTITY_USER)
                .where(IDENTITY_USER.ID.eq(id.value))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: UserId): User? {
        val record =
            dsl
                .selectFrom(IDENTITY_USER)
                .where(IDENTITY_USER.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(): List<User> =
        dsl
            .selectFrom(IDENTITY_USER)
            .orderBy(IDENTITY_USER.CREATED_AT.desc(), IDENTITY_USER.ID)
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun existsAny(): Boolean = dsl.fetchExists(dsl.selectFrom(IDENTITY_USER))

    @Transactional
    override fun lockBootstrap() {
        // pg_advisory_xact_lock releases automatically at transaction end
        // (commit or rollback) — no matching unlock call is needed. The key
        // is an arbitrary but stable identifier for "identity bootstrap";
        // it only needs to be unique among this application's advisory lock
        // uses, of which this is currently the only one.
        dsl.query("SELECT pg_advisory_xact_lock(?)", BOOTSTRAP_LOCK_KEY).execute()
    }

    @Transactional
    override fun save(user: User) {
        dsl
            .insertInto(IDENTITY_USER)
            .set(IDENTITY_USER.ID, user.id.value)
            .set(IDENTITY_USER.EMAIL, user.email.value)
            .set(IDENTITY_USER.PASSWORD_HASH, user.passwordHash.value)
            .set(IDENTITY_USER.STATUS, user.status.name)
            .set(IDENTITY_USER.CREATED_AT, toOffset(user.createdAt))
            .set(IDENTITY_USER.FAILED_LOGIN_COUNT, user.failedLoginCount)
            .set(IDENTITY_USER.LOCKED_UNTIL, user.lockedUntil?.let(::toOffset))
            .onConflict(IDENTITY_USER.ID)
            .doUpdate()
            .set(IDENTITY_USER.PASSWORD_HASH, user.passwordHash.value)
            .set(IDENTITY_USER.STATUS, user.status.name)
            .set(IDENTITY_USER.FAILED_LOGIN_COUNT, user.failedLoginCount)
            .set(IDENTITY_USER.LOCKED_UNTIL, user.lockedUntil?.let(::toOffset))
            .execute()

        dsl
            .deleteFrom(IDENTITY_USER_ROLE)
            .where(IDENTITY_USER_ROLE.USER_ID.eq(user.id.value))
            .execute()

        if (user.roles.isNotEmpty()) {
            val insert = dsl.insertInto(IDENTITY_USER_ROLE, IDENTITY_USER_ROLE.USER_ID, IDENTITY_USER_ROLE.ROLE_CODE)
            user.roles.forEach { role -> insert.values(user.id.value, role.value) }
            insert.execute()
        }
    }

    private fun toDomain(record: IdentityUserRecord): User {
        val userId = UserId(record.id)
        val roles =
            dsl
                .select(IDENTITY_USER_ROLE.ROLE_CODE)
                .from(IDENTITY_USER_ROLE)
                .where(IDENTITY_USER_ROLE.USER_ID.eq(userId.value))
                .fetch(IDENTITY_USER_ROLE.ROLE_CODE)
                .map { RoleCode.of(it) }
                .toSet()

        return User(
            id = userId,
            email = EmailAddress.of(record.email),
            passwordHash = HashedPassword.of(record.passwordHash),
            status = UserStatus.valueOf(record.status),
            roles = roles,
            createdAt = record.createdAt.toInstant(),
            failedLoginCount = record.failedLoginCount,
            lockedUntil = record.lockedUntil?.toInstant(),
        )
    }

    private fun toOffset(instant: java.time.Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)

    companion object {
        private const val BOOTSTRAP_LOCK_KEY = 729_401_001L
    }
}
