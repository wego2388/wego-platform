package com.wego.mobile.shared.catalog

/** Mirrors the `categories` array in `web/apps/sharm-divers-club-site/app/content/locales.ts`. */
data class CategoryMeta(
    val id: CategoryId,
    val eyebrow: LocalizedText,
    val title: LocalizedText,
    val description: LocalizedText,
)

object Categories {
    val all: List<CategoryMeta> =
        listOf(
            CategoryMeta(
                id = CategoryId.SHORE_DIVING,
                eyebrow = LocalizedText("From the beach", "من الشاطئ"),
                title = LocalizedText("Shore diving", "غطس الشاطئ"),
                description =
                    LocalizedText(
                        "Intro dives for first-timers and guided shore dives for certified divers, right from Sharm.",
                        "غطسات تجريبية للمبتدئين وغطسات شاطئ موجهة للغواصين المعتمدين، من شرم مباشرة.",
                    ),
            ),
            CategoryMeta(
                id = CategoryId.BOAT_DIVING,
                eyebrow = LocalizedText("By boat", "بالقارب"),
                title = LocalizedText("Boat diving", "غطس القارب"),
                description =
                    LocalizedText(
                        "Day trips to Ras Mohammed and Tiran, for beginners and certified divers alike.",
                        "رحلات يوم كامل لرأس محمد وتيران، للمبتدئين والغواصين المعتمدين.",
                    ),
            ),
            CategoryMeta(
                id = CategoryId.MULTI_DAY,
                eyebrow = LocalizedText("Several days", "عدة أيام"),
                title = LocalizedText("Multi-day packages", "باقات متعددة الأيام"),
                description =
                    LocalizedText(
                        "3 to 7 day dive packages combining shore and boat days for divers with more time.",
                        "باقات من 3 إلى 7 أيام تجمع بين أيام الشاطئ والقارب لمن عنده وقت أطول.",
                    ),
            ),
            CategoryMeta(
                id = CategoryId.SIGNATURE,
                eyebrow = LocalizedText("The full route", "المسار الكامل"),
                title = LocalizedText("Signature packages", "الباقات المميزة"),
                description =
                    LocalizedText(
                        "\"Red Sea Icons\" — a proposed multi-day route across the region's best-known sites.",
                        "\"أيقونات البحر الأحمر\" — مسار مقترح متعدد الأيام يغطي أشهر مواقع المنطقة.",
                    ),
            ),
            CategoryMeta(
                id = CategoryId.WORLD_CLASS,
                eyebrow = LocalizedText("The highlights", "أهم المواقع"),
                title = LocalizedText("World-class sites", "مواقع عالمية"),
                description =
                    LocalizedText(
                        "SS Thistlegorm, Dahab's Blue Hole & Canyon, Tiran drift diving, Ras Mohammed's Shark & Yolanda.",
                        "حطام SS Thistlegorm، وBlue Hole وCanyon في دهب، وDrift Diving في تيران، وShark & Yolanda في رأس محمد.",
                    ),
            ),
            CategoryMeta(
                id = CategoryId.PADI_COURSES,
                eyebrow = LocalizedText("Get certified", "احصل على شهادة"),
                title = LocalizedText("PADI courses", "دورات PADI"),
                description =
                    LocalizedText(
                        "Open Water through Divemaster, plus Nitrox and Emergency First Response.",
                        "من Open Water لحد Divemaster، بالإضافة لـNitrox وEmergency First Response.",
                    ),
            ),
            CategoryMeta(
                id = CategoryId.WATER_SPORTS,
                eyebrow = LocalizedText("Above the water", "فوق سطح الماء"),
                title = LocalizedText("Water sports", "الرياضات المائية"),
                description =
                    LocalizedText(
                        "Parasailing, banana boat, glass-bottom boat and more — real Red Sea water sports for the whole group.",
                        "باراسيلينج، بانانا بوت، قارب زجاجي وأكتر — رياضات مائية حقيقية من البحر الأحمر لكل المجموعة.",
                    ),
            ),
        )

    fun byId(id: CategoryId): CategoryMeta = all.first { it.id == id }
}
