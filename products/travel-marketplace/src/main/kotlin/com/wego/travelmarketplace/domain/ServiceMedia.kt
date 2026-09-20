package com.wego.travelmarketplace.domain

import java.util.UUID

/**
 * One published photo/asset attached to a [Service], with the rights
 * evidence `SERVICE_CONTENT_TEMPLATE.md` requires before publication —
 * "Photo asset IDs and rights evidence" is a mandatory field on that
 * template, not optional metadata. `assetReference` is an opaque pointer
 * (an id or URL into whatever asset store eventually holds the file); no
 * upload/storage system is part of this phase.
 */
data class ServiceMedia(
    val id: UUID,
    val assetReference: String,
    val rightsEvidence: String,
    val locale: String,
) {
    init {
        require(assetReference.isNotBlank()) { "Asset reference must not be blank" }
        require(rightsEvidence.isNotBlank()) { "Rights evidence must not be blank" }
        require(locale.isNotBlank()) { "Locale must not be blank" }
    }
}
