package com.wego.mobile.customer.nav

import com.wego.mobile.shared.catalog.CategoryId

sealed interface AppDestination {
    /** The route pattern registered with `NavHost`'s `composable(...)`. */
    val route: String

    data object Home : AppDestination {
        override val route: String = "home"
    }

    data object Discover : AppDestination {
        override val route: String = "discover?category={category}"
        const val ARG_CATEGORY = "category"

        /** The route to actually `navigate()` to — the query arg is optional. */
        fun navRoute(categoryId: CategoryId? = null): String =
            if (categoryId == null) "discover" else "discover?category=${categoryId.name}"
    }

    data object About : AppDestination {
        override val route: String = "about"
    }

    data object Faq : AppDestination {
        override val route: String = "faq"
    }

    data object Contact : AppDestination {
        override val route: String = "contact"
    }

    data object OfferingDetail : AppDestination {
        override val route: String = "offering/{code}"
        const val ARG_CODE = "code"

        fun navRoute(code: String): String = "offering/$code"
    }

    data object DiveSites : AppDestination {
        override val route: String = "dive-sites"
    }

    data object DiveSiteDetail : AppDestination {
        override val route: String = "dive-sites/{slug}"
        const val ARG_SLUG = "slug"

        fun navRoute(slug: String): String = "dive-sites/$slug"
    }

    data object PackageBuilder : AppDestination {
        override val route: String = "package-builder"
    }
}

/** Bottom navigation only shows the five top-level destinations, not the detail screen. */
val bottomNavDestinations: List<AppDestination> =
    listOf(AppDestination.Home, AppDestination.Discover, AppDestination.Faq, AppDestination.Contact, AppDestination.About)
