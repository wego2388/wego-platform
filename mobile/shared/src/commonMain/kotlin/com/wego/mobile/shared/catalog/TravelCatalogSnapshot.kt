package com.wego.mobile.shared.catalog

/**
 * A versioned, bundled copy of the real Sharm To Go public catalog
 * (`GET /api/v1/travel-marketplace/public/{categories,services}`), refreshed
 * on each app release rather than live-fetched — this repository's mobile
 * layer has no KMP HTTP client yet, and Phase 1 (read-only catalog, no
 * checkout) doesn't need one; see `TECHNICAL_EXECUTION_PLAN.md`'s Packet 1D
 * section for why that decision is deferred to the real booking phase.
 *
 * Empty is the real, live-verified current state (WEGO-010-A Packet 1C,
 * 2026-09-03: `GET .../public/categories` and `.../public/services` both
 * returned `[]` against the real running backend) — not a placeholder.
 * Regenerate by curling those two real endpoints against the real backend
 * once an owner has actually approved and published a service, and
 * transcribing the response verbatim, the same discipline `DiveCatalog.kt`
 * documents for its own source. Never hand-add an entry here that the real
 * backend hasn't actually published.
 */
object TravelCatalogSnapshot {
    val categories: List<TravelCategory> = emptyList()
    val services: List<TravelService> = emptyList()

    fun categoryById(id: String): TravelCategory? = categories.find { it.id == id }

    fun serviceById(id: String): TravelService? = services.find { it.id == id }

    fun servicesByCategory(categoryId: String): List<TravelService> = services.filter { it.categoryId == categoryId }
}
