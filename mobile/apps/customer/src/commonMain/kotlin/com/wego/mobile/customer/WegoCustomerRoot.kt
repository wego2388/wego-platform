package com.wego.mobile.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.savedstate.read
import com.wego.mobile.customer.content.SiteCopy
import com.wego.mobile.customer.nav.AppDestination
import com.wego.mobile.customer.nav.bottomNavDestinations
import com.wego.mobile.customer.state.AppLocaleState
import com.wego.mobile.customer.theme.SdcTheme
import com.wego.mobile.customer.ui.screens.AboutScreen
import com.wego.mobile.customer.ui.screens.ContactScreen
import com.wego.mobile.customer.ui.screens.DiscoverScreen
import com.wego.mobile.customer.ui.screens.FaqScreen
import com.wego.mobile.customer.ui.screens.HomeScreen
import com.wego.mobile.customer.ui.screens.OfferingDetailScreen
import com.wego.mobile.shared.catalog.CategoryId
import com.wego.mobile.shared.experience.ExperienceProfile
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.shared.locale.LocaleStore
import com.wego.mobile.shared.locale.NoOpLocaleStore

@Composable
@Suppress("FunctionName")
fun WegoCustomerRoot(
    experienceProfile: ExperienceProfile = ExperienceProfile.STANDARD,
    localeStore: LocaleStore = NoOpLocaleStore,
) {
    val localeState = remember { AppLocaleState(store = localeStore) }
    SdcTheme(locale = localeState.locale, useDarkColors = isSystemInDarkTheme()) {
        Surface {
            WegoCustomerApp(localeState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
private fun WegoCustomerApp(localeState: AppLocaleState) {
    val navController = rememberNavController()
    val uriHandler = LocalUriHandler.current
    val locale = localeState.locale
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(navTitle(currentRoute, locale)) },
                actions = {
                    TextButton(onClick = { localeState.toggle() }) {
                        Text(if (locale == AppLocale.EN) "AR" else "EN")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                for (destination in bottomNavDestinations) {
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(navRouteFor(destination)) {
                                    popUpTo(AppDestination.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        ),
                            )
                        },
                        label = { Text(navLabel(destination, locale)) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    locale = locale,
                    onBrowseAll = { navController.navigate(AppDestination.Discover.navRoute()) },
                    onCategoryClick = { categoryId ->
                        navController.navigate(AppDestination.Discover.navRoute(categoryId))
                    },
                    onOpenWhatsApp = { uriHandler.openUri(SiteCopy.WHATSAPP_URL) },
                    onOpenTripAdvisor = { uriHandler.openUri(SiteCopy.TRIPADVISOR_URL) },
                )
            }
            composable(
                route = AppDestination.Discover.route,
                arguments =
                    listOf(
                        navArgument(AppDestination.Discover.ARG_CATEGORY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { entry ->
                val categoryArg = entry.arguments?.read { getStringOrNull(AppDestination.Discover.ARG_CATEGORY) }.orEmpty()
                val categoryId = CategoryId.entries.firstOrNull { it.name == categoryArg }
                DiscoverScreen(
                    locale = locale,
                    initialCategory = categoryId,
                    onOfferingClick = { offering -> navController.navigate(AppDestination.OfferingDetail.navRoute(offering.code)) },
                )
            }
            composable(
                route = AppDestination.OfferingDetail.route,
                arguments = listOf(navArgument(AppDestination.OfferingDetail.ARG_CODE) { type = NavType.StringType }),
                deepLinks =
                    listOf(
                        navDeepLink { uriPattern = "sharmdiversclub://discover/{code}" },
                        navDeepLink { uriPattern = "https://sharmdiversclub.com/discover/{code}" },
                    ),
            ) { entry ->
                val code = entry.arguments?.read { getStringOrNull(AppDestination.OfferingDetail.ARG_CODE) }.orEmpty()
                OfferingDetailScreen(
                    code = code,
                    locale = locale,
                    onBack = { navController.popBackStack() },
                    onOfferingClick = { offering ->
                        navController.navigate(AppDestination.OfferingDetail.navRoute(offering.code)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenUrl = { url -> uriHandler.openUri(url) },
                )
            }
            composable(AppDestination.About.route) {
                AboutScreen(locale = locale)
            }
            composable(AppDestination.Faq.route) {
                FaqScreen(
                    locale = locale,
                    onOpenWhatsApp = { uriHandler.openUri(SiteCopy.WHATSAPP_URL) },
                )
            }
            composable(AppDestination.Contact.route) {
                ContactScreen(
                    locale = locale,
                    onOpenWhatsApp = { uriHandler.openUri(SiteCopy.WHATSAPP_URL) },
                )
            }
        }
    }
}

private fun navRouteFor(destination: AppDestination): String =
    when (destination) {
        AppDestination.Discover -> AppDestination.Discover.navRoute()
        else -> destination.route
    }

private fun navLabel(
    destination: AppDestination,
    locale: AppLocale,
): String =
    when (destination) {
        AppDestination.Home -> SiteCopy.Nav.home.of(locale)
        AppDestination.Discover -> SiteCopy.Nav.discover.of(locale)
        AppDestination.About -> SiteCopy.Nav.about.of(locale)
        AppDestination.Faq -> SiteCopy.Nav.faq.of(locale)
        AppDestination.Contact -> SiteCopy.Nav.contact.of(locale)
        else -> ""
    }

private fun navTitle(
    route: String?,
    locale: AppLocale,
): String =
    when (route) {
        AppDestination.Home.route -> SiteCopy.Nav.home.of(locale)
        AppDestination.Discover.route -> SiteCopy.Nav.discover.of(locale)
        AppDestination.About.route -> SiteCopy.Nav.about.of(locale)
        AppDestination.Faq.route -> SiteCopy.Nav.faq.of(locale)
        AppDestination.Contact.route -> SiteCopy.Nav.contact.of(locale)
        else -> "Sharm Divers Club"
    }
