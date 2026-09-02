package com.wego.identity

/**
 * A product module contributes one Spring bean of this type per API path
 * prefix it owns and wants gated behind authentication. Kernel identity code
 * must never hardcode a specific product's route pattern — the whole point
 * of WEGO-010-A Packet 0R is that a client's compiled application only ever
 * authorizes the routes its own linked product actually contributes. An
 * application with no matching product on its compile classpath contributes
 * no such bean, so its equivalent path space falls through to the default
 * `denyAll()` in `com.wego.identity.infrastructure.SecurityConfiguration`,
 * not merely `authenticated()`. Placed at this module's Modulith-public root
 * (like `AuthenticatedUser`), not under `infrastructure`, so other modules
 * may depend on it without a named-interface violation.
 */
data class AuthenticatedApiPrefix(
    val pattern: String,
)
