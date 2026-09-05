package com.wego.hr.application

import java.util.UUID

/**
 * The minimal cross-module check this application needs against
 * `com.wego.identity`: whether a given user id is a real, active staff
 * account — a module-local copy of `com.wego.divers.application.StaffUserLookup`'s
 * exact reasoning (that module's own doc comment explains why this isn't a
 * dependency on `com.wego.identity.application`/`.domain` directly).
 */
interface StaffUserLookup {
    fun isActiveStaffUser(userId: UUID): Boolean
}
