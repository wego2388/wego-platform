package com.wego.mobile.sharmtogo.design

import androidx.compose.ui.graphics.Color

/**
 * One accent + icon per discovery category (Sea, Desert, Transfers, City),
 * cycled by position — mirrors `web/apps/sharm-to-go-site/app/content/
 * categoryAccents.ts`'s `accentForIndex`/`toneForIndex` and this repo's own
 * `clients/sharm-to-go/design/tokens.json` `categoryAccent` map exactly, so
 * the app and the website agree on which color belongs to which category
 * rather than reinventing a second mapping.
 */
enum class StgCategoryTone(
    val accent: Color,
    val gradientEnd: Color,
    /** 24x24-viewBox stroked SVG path data, pixel-identical to `MockPhoto.vue`'s icon set. */
    val iconPath: String,
) {
    SEA(
        accent = StgColor.seaBright,
        gradientEnd = StgColor.sea,
        iconPath =
            "M2 15c1.6-1.6 3.2-1.6 4.8 0s3.2 1.6 4.8 0 3.2-1.6 4.8 0 3.2 1.6 4.8 0" +
                "M2 19c1.6-1.6 3.2-1.6 4.8 0s3.2 1.6 4.8 0 3.2-1.6 4.8 0 3.2 1.6 4.8 0",
    ),
    DESERT(
        accent = StgColor.sun,
        gradientEnd = StgColor.terracotta,
        iconPath =
            "M12 6v2M6.5 8.5l1.4 1.4M17.5 8.5l-1.4 1.4M12 10a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z" +
                "M2 20c2.5-3 5-4 7-4s3.5 1.4 5 1.4 3-1.4 5-1.4 3 1.5 3 4",
    ),
    TRANSFERS(
        accent = StgColor.sky,
        gradientEnd = StgColor.sea,
        iconPath = "M4 16V12l2-4h9l3 4v4M4 16h16M7 16a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM17 16a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z",
    ),
    CITY(
        accent = StgColor.terracotta,
        gradientEnd = StgColor.sand,
        iconPath = "M3 20V9l4-3v14M9 20V5l4-2v17M15 20V11l6-3v12M3 20h18",
    ),
    ;

    companion object {
        private val ordered = entries.toTypedArray()

        fun forIndex(index: Int): StgCategoryTone {
            val length = ordered.size
            val safeIndex = ((index % length) + length) % length
            return ordered[safeIndex]
        }
    }
}
