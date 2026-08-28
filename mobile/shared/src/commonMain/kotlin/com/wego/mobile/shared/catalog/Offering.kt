package com.wego.mobile.shared.catalog

import com.wego.mobile.shared.locale.AppLocale

/** A string with both required translations — no locale falls back to a missing value. */
data class LocalizedText(
    val en: String,
    val ar: String,
) {
    fun of(locale: AppLocale): String =
        when (locale) {
            AppLocale.EN -> en
            AppLocale.AR -> ar
        }
}

enum class CategoryId {
    SHORE_DIVING,
    BOAT_DIVING,
    MULTI_DAY,
    SIGNATURE,
    WORLD_CLASS,
    PADI_COURSES,
    WATER_SPORTS,
}

enum class Audience {
    BEGINNER,
    CERTIFIED_DIVER,
    QUALIFIED_CERTIFIED_DIVER,
    BEGINNER_COURSE,
    PROFESSIONAL_TRACK,
    GENERAL,
    PRIVATE_GROUP,
}

data class Offering(
    val code: String,
    val categoryId: CategoryId,
    val name: LocalizedText,
    val audience: Audience,
    val durationMinutes: Int? = null,
    val diveCount: Int? = null,
    val priceEur: Int,
)

/**
 * Sourced verbatim from data/catalog.dive-core.v1.json — status "approved",
 * publishable:true as of 2026-08-26 (governance decision GOV-003). Mirrors
 * `web/apps/sharm-divers-club-site/app/content/offerings.ts` exactly: one
 * catalog, two renderers. Diving + water sports only, per the owner's
 * explicit scope decisions (2026-08-27 "diving only for now", 2026-08-28
 * "add water sports"). The catalog's other newer offers (desert safari,
 * sightseeing, snorkeling, transfers) stay unused until a further scope
 * decision.
 */
object DiveCatalog {
    val offerings: List<Offering> =
        listOf(
            Offering(
                code = "SD02",
                categoryId = CategoryId.SHORE_DIVING,
                name = LocalizedText("Intro Dive — 30 minutes", "غطسة تجريبية من الشاطئ — 30 دقيقة"),
                audience = Audience.BEGINNER,
                durationMinutes = 30,
                diveCount = 1,
                priceEur = 50,
            ),
            Offering(
                code = "SD05",
                categoryId = CategoryId.SHORE_DIVING,
                name = LocalizedText("Two guided shore dives", "غطستا شاطئ للغواص المعتمد"),
                audience = Audience.CERTIFIED_DIVER,
                diveCount = 2,
                priceEur = 75,
            ),
            Offering(
                code = "BD02",
                categoryId = CategoryId.BOAT_DIVING,
                name = LocalizedText("Ras Mohammed beginner dive — 30 minutes", "رأس محمد للمبتدئ — 30 دقيقة"),
                audience = Audience.BEGINNER,
                durationMinutes = 30,
                diveCount = 1,
                priceEur = 60,
            ),
            Offering(
                code = "BD08",
                categoryId = CategoryId.BOAT_DIVING,
                name = LocalizedText("Tiran — two certified dives", "تيران — غطستان للمعتمد"),
                audience = Audience.CERTIFIED_DIVER,
                diveCount = 2,
                priceEur = 120,
            ),
            Offering(
                code = "MP01",
                categoryId = CategoryId.MULTI_DAY,
                name = LocalizedText("3 days / 6 shore dives", "3 أيام / 6 غطسات شاطئ"),
                audience = Audience.CERTIFIED_DIVER,
                diveCount = 6,
                priceEur = 199,
            ),
            Offering(
                code = "MP04",
                categoryId = CategoryId.MULTI_DAY,
                name = LocalizedText("7 days / 14 dives", "7 أيام / 14 غطسة"),
                audience = Audience.CERTIFIED_DIVER,
                diveCount = 14,
                priceEur = 590,
            ),
            Offering(
                code = "HP01",
                categoryId = CategoryId.SIGNATURE,
                name = LocalizedText("Red Sea Icons — 3 days / 6 dives", "أيقونات البحر الأحمر — 3 أيام / 6 غطسات"),
                audience = Audience.CERTIFIED_DIVER,
                diveCount = 6,
                priceEur = 430,
            ),
            Offering(
                code = "HP03",
                categoryId = CategoryId.SIGNATURE,
                name = LocalizedText("Red Sea Icons — 5 days / 10 dives", "أيقونات البحر الأحمر — 5 أيام / 10 غطسات"),
                audience = Audience.CERTIFIED_DIVER,
                diveCount = 10,
                priceEur = 575,
            ),
            Offering(
                code = "WC01",
                categoryId = CategoryId.WORLD_CLASS,
                name = LocalizedText("SS Thistlegorm — two dives", "حطام SS Thistlegorm — غطستان"),
                audience = Audience.QUALIFIED_CERTIFIED_DIVER,
                diveCount = 2,
                priceEur = 225,
            ),
            Offering(
                code = "WC02",
                categoryId = CategoryId.WORLD_CLASS,
                name = LocalizedText("Dahab Blue Hole & Canyon — two dives", "دهب Blue Hole وCanyon — غطستان"),
                audience = Audience.QUALIFIED_CERTIFIED_DIVER,
                diveCount = 2,
                priceEur = 205,
            ),
            Offering(
                code = "PC04",
                categoryId = CategoryId.PADI_COURSES,
                name = LocalizedText("PADI Open Water Diver", "PADI Open Water Diver"),
                audience = Audience.BEGINNER_COURSE,
                diveCount = 4,
                priceEur = 350,
            ),
            Offering(
                code = "PC11",
                categoryId = CategoryId.PADI_COURSES,
                name = LocalizedText("PADI Divemaster", "PADI Divemaster"),
                audience = Audience.PROFESSIONAL_TRACK,
                priceEur = 1499,
            ),
            Offering(
                code = "WS01",
                categoryId = CategoryId.WATER_SPORTS,
                name = LocalizedText("Parasailing", "باراسيلينج"),
                audience = Audience.GENERAL,
                priceEur = 30,
            ),
            Offering(
                code = "WS02",
                categoryId = CategoryId.WATER_SPORTS,
                name = LocalizedText("Banana Boat", "بانانا بوت"),
                audience = Audience.GENERAL,
                priceEur = 35,
            ),
            Offering(
                code = "WS03",
                categoryId = CategoryId.WATER_SPORTS,
                name = LocalizedText("Tube Ride", "تيوب رايد"),
                audience = Audience.GENERAL,
                priceEur = 30,
            ),
            Offering(
                code = "WS04",
                categoryId = CategoryId.WATER_SPORTS,
                name = LocalizedText("Private Speed Boat", "سبيد بوت خاص"),
                audience = Audience.PRIVATE_GROUP,
                priceEur = 60,
            ),
            Offering(
                code = "WS05",
                categoryId = CategoryId.WATER_SPORTS,
                name = LocalizedText("Glass Bottom Boat", "قارب زجاجي"),
                audience = Audience.GENERAL,
                priceEur = 20,
            ),
            Offering(
                code = "WS06",
                categoryId = CategoryId.WATER_SPORTS,
                name = LocalizedText("Submarine Tour", "رحلة الغواصة"),
                audience = Audience.GENERAL,
                priceEur = 40,
            ),
        )

    fun byCode(code: String): Offering? = offerings.find { it.code.equals(code, ignoreCase = true) }

    fun byCategory(categoryId: CategoryId): List<Offering> = offerings.filter { it.categoryId == categoryId }
}
