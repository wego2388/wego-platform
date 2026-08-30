package com.wego.mobile.shared.catalog

/**
 * Mirrors `web/apps/sharm-divers-club-site/app/content/diveSites.ts` — the
 * same 4 real named sites, derived from the catalog's own already-approved
 * offering names, one catalog two renderers (same discipline as
 * `DiveCatalog`/`Categories`). Blurbs stay limited to verifiable public
 * geography/history about the place itself — never invented depth,
 * visibility or marine-life detail, since no source-verified data exists
 * for those and this project's no-fabrication rule carves out no exception
 * for "obviously safe" invented numbers. Source and reviewer recorded in
 * web/apps/sharm-divers-club-site/app/content/DIVE_SITE_SOURCES.md.
 */
data class DiveSite(
    val slug: String,
    val name: LocalizedText,
    val blurb: LocalizedText,
    val offeringCodes: List<String>,
)

object DiveSites {
    val all: List<DiveSite> =
        listOf(
            DiveSite(
                slug = "ras-mohammed",
                name = LocalizedText("Ras Mohammed", "رأس محمد"),
                blurb =
                    LocalizedText(
                        "Ras Mohammed National Park sits at the southern tip of the Sinai Peninsula, where the " +
                            "Gulf of Suez meets the Gulf of Aqaba.",
                        "محمية رأس محمد الطبيعية تقع في أقصى جنوب شبه جزيرة سيناء، حيث يلتقي خليج السويس بخليج " +
                            "العقبة.",
                    ),
                offeringCodes = listOf("BD02"),
            ),
            DiveSite(
                slug = "tiran",
                name = LocalizedText("Tiran", "تيران"),
                blurb =
                    LocalizedText(
                        "Tiran Island sits in the Strait of Tiran, between the Sinai Peninsula and Saudi Arabia, " +
                            "at the mouth of the Gulf of Aqaba.",
                        "جزيرة تيران تقع في مضيق تيران، بين شبه جزيرة سيناء والسعودية، عند مدخل خليج العقبة.",
                    ),
                offeringCodes = listOf("BD08"),
            ),
            DiveSite(
                slug = "thistlegorm",
                name = LocalizedText("SS Thistlegorm", "SS Thistlegorm"),
                blurb =
                    LocalizedText(
                        "SS Thistlegorm is a British WWII cargo ship that sank in the northern Red Sea in 1941 " +
                            "— named one of the world's top ten wreck dives by The Times.",
                        "SS Thistlegorm سفينة شحن بريطانية غرقت في شمال البحر الأحمر عام 1941 خلال الحرب العالمية " +
                            "الثانية — اختارتها صحيفة The Times كواحدة من أفضل 10 مواقع غوص حطام في العالم.",
                    ),
                offeringCodes = listOf("WC01"),
            ),
            DiveSite(
                slug = "dahab-blue-hole-canyon",
                name = LocalizedText("Dahab Blue Hole & Canyon", "دهب Blue Hole وCanyon"),
                blurb =
                    LocalizedText(
                        "The Blue Hole and Canyon are well-known dive sites on Sinai's Red Sea coast, near " +
                            "Dahab.",
                        "Blue Hole وCanyon من أشهر مواقع الغوص على ساحل البحر الأحمر في سيناء، بالقرب من دهب.",
                    ),
                offeringCodes = listOf("WC02"),
            ),
        )

    fun bySlug(slug: String): DiveSite? = all.find { it.slug == slug }
}

fun DiveSite.offerings(): List<Offering> = DiveCatalog.offerings.filter { it.code in offeringCodes }
