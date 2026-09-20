package com.wego.travelmarketplace.domain

/**
 * A required bilingual text field — both variants non-blank. Matches this
 * client's real, current locale support: `client.manifest.json` lists only
 * `ar`/`en` today (see `LOCALES_AND_CONTENT.md`), and that document's
 * translation lifecycle (`DRAFT → MACHINE_ASSISTED → HUMAN_REVIEWED →
 * APPROVED → PUBLISHED`, staleness tracking per source change) is a real,
 * separate sub-system deliberately deferred past this phase — see the
 * WEGO-010-A Packet 1A board entry's scope note. This phase's bar is the
 * simpler one `SERVICE_CONTENT_TEMPLATE.md` itself states: a service cannot
 * publish while either language is missing, full stop.
 */
data class LocalizedText(
    val en: String,
    val ar: String,
) {
    init {
        require(en.isNotBlank()) { "English text must not be blank" }
        require(ar.isNotBlank()) { "Arabic text must not be blank" }
    }
}
