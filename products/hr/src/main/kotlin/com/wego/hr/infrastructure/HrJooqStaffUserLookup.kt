package com.wego.hr.infrastructure

import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import com.wego.hr.application.StaffUserLookup
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Named distinctly from com.wego.divers.infrastructure.JooqStaffUserLookup:
// Spring's default component-scan bean naming uses the simple class name,
// and two beans named "jooqStaffUserLookup" from different modules collide
// at context startup — same reasoning DiversSpringTransactionRunner's own
// doc comment already established for this exact class-naming pattern.
@Component
class HrJooqStaffUserLookup(
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
