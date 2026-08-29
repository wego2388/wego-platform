package com.wego.identity

import java.util.UUID

/**
 * The minimal cross-module-safe view of "who is making this request":
 * just the authenticated user's id. Placed at this module's Modulith-public
 * root (unlike the richer `com.wego.identity.application.AuthenticatedPrincipal`,
 * which is nested and therefore off-limits to other modules) so any product
 * module can attribute a write to a real user without importing identity's
 * internal domain model — the same reason `PermissionCode` was promoted out
 * of `identity` entirely and into `platform/kernel/security`.
 */
interface AuthenticatedUser {
    val userId: UUID
}
