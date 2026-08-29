package com.wego.divers.infrastructure

import com.wego.divers.application.StaffUserLookup
import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class JooqStaffUserLookup(
    private val dsl: DSLContext,
) : StaffUserLookup {
    @Transactional(readOnly = true)
    override fun isActiveStaffUser(userId: UUID): Boolean =
        dsl.fetchExists(
            dsl
                .selectFrom(IDENTITY_USER)
                .where(IDENTITY_USER.ID.eq(userId))
                .and(IDENTITY_USER.STATUS.eq("ACTIVE")),
        )
}
