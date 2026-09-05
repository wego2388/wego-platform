package com.wego.mobile.sharmtogo.nav

sealed interface AppDestination {
    /** The route pattern registered with `NavHost`'s `composable(...)`. */
    val route: String

    data object Home : AppDestination {
        override val route: String = "home"
    }

    data object Experiences : AppDestination {
        override val route: String = "experiences?category={category}"
        const val ARG_CATEGORY = "category"

        /** The route to actually `navigate()` to — the query arg is optional. */
        fun navRoute(categoryId: String? = null): String =
            if (categoryId.isNullOrEmpty()) "experiences" else "experiences?category=$categoryId"
    }

    data object ExperienceDetail : AppDestination {
        override val route: String = "experiences/{id}"
        const val ARG_ID = "id"

        fun navRoute(id: String): String = "experiences/$id"
    }
}

/** Bottom navigation only shows the top-level destinations, not the detail screen. */
val bottomNavDestinations: List<AppDestination> = listOf(AppDestination.Home, AppDestination.Experiences)
