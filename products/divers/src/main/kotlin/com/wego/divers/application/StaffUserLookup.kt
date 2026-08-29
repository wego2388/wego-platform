package com.wego.divers.application

import java.util.UUID

/**
 * The minimal cross-module check this application actually needs against
 * `com.wego.identity`: whether a given user id is a real, active staff
 * account. Deliberately not a dependency on `com.wego.identity.application`
 * or `.domain` — those subpackages are internal to that module (Spring
 * Modulith's own `ModuleArchitectureTest` enforces this), so the
 * infrastructure implementation reads the `identity_user` table directly
 * via jOOQ generated code instead, the same real foreign-key relationship
 * `V7__divers_course_enrollment.sql`'s `instructor_user_id` column already
 * declares at the schema level.
 */
interface StaffUserLookup {
    fun isActiveStaffUser(userId: UUID): Boolean
}
