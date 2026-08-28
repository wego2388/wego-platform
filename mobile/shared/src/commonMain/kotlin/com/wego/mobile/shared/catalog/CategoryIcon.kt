package com.wego.mobile.shared.catalog

/**
 * Raw SVG path data per category — framework-agnostic on purpose. Mirrors
 * `categoryIcon()` in `web/apps/sharm-divers-club-site/app/content/offerings.ts`
 * exactly, so the web and mobile icon sets stay pixel-identical. Rendering
 * (parsing this into a Compose `Path`/`ImageVector`) is a UI-layer concern —
 * see `mobile/apps/customer`'s screens, not this module.
 */
private val categoryIconPaths: Map<CategoryId, String> =
    mapOf(
        CategoryId.SHORE_DIVING to "M2 15c2-2 4-2 6 0s4 2 6 0 4-2 6 0 M2 10c2-2 4-2 6 0s4 2 6 0 4-2 6 0",
        CategoryId.BOAT_DIVING to "M4 15h16l-2 5H6l-2-5Zm8-11v11M8 8l4-4 4 4",
        CategoryId.MULTI_DAY to "M4 5h16v15H4V5Zm0 5h16M8 3v4M16 3v4",
        CategoryId.SIGNATURE to "M12 3l2.2 6.8H21l-5.6 4.2L17.6 21 12 16.8 6.4 21l2.2-7-5.6-4.2h6.8L12 3Z",
        CategoryId.WORLD_CLASS to
            "M12 3v4m0 14v-4m-9-7h4m10 0h4M6.3 6.3l2.8 2.8m5.8 5.8 2.8 2.8m0-11.4-2.8 2.8m-5.8 5.8-2.8 2.8M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z",
        CategoryId.PADI_COURSES to "M12 3 3 7l9 4 9-4-9-4Zm-6 6v6l6 3 6-3V9M6 13v4M18 13v4",
        CategoryId.WATER_SPORTS to "M3 18c1.5-1.5 3-1.5 4.5 0s3 1.5 4.5 0 3-1.5 4.5 0 3-1.5 4.5 0M12 3v9m0 0-3-3m3 3 3-3",
    )

fun CategoryId.iconPath(): String = categoryIconPaths.getValue(this)
