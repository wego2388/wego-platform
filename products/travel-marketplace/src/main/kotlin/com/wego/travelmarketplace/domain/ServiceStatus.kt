package com.wego.travelmarketplace.domain

/**
 * `LOCALES_AND_CONTENT.md`'s publication lifecycle, applied to a whole
 * service rather than a single translated field:
 * `DRAFT → REVIEW → APPROVED → PUBLISHED`, with `SUSPENDED` reachable from
 * `PUBLISHED` (and re-publishable from there) and `ARCHIVED` reachable from
 * any non-terminal state as this aggregate's own terminal state — see
 * [Service]'s transition methods for the exact guarded edges.
 */
enum class ServiceStatus {
    DRAFT,
    REVIEW,
    APPROVED,
    PUBLISHED,
    SUSPENDED,
    ARCHIVED,
}
