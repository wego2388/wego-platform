package com.wego.identity

/**
 * A product module contributes one Spring bean of this type per API path
 * prefix that must be reachable without authentication — e.g. a public
 * marketplace catalog read. Sibling to [AuthenticatedApiPrefix]; see that
 * type's doc comment for the same isolation rationale. Every public prefix
 * is registered in `SecurityConfiguration` before the general authenticated
 * rules, so a product's own narrower public route tree takes precedence
 * over that same product's broader authenticated route tree, rather than
 * being shadowed by it.
 */
data class PublicApiPrefix(
    val pattern: String,
)
