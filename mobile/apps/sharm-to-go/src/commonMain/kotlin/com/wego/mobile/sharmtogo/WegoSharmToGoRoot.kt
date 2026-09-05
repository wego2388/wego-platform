package com.wego.mobile.sharmtogo

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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.shared.locale.LocaleStore
import com.wego.mobile.shared.locale.NoOpLocaleStore
import com.wego.mobile.sharmtogo.content.SiteCopy
import com.wego.mobile.sharmtogo.nav.AppDestination
import com.wego.mobile.sharmtogo.nav.bottomNavDestinations
import com.wego.mobile.sharmtogo.state.AppLocaleState
import com.wego.mobile.sharmtogo.theme.StgTheme
import com.wego.mobile.sharmtogo.ui.screens.ExperienceDetailScreen
import com.wego.mobile.sharmtogo.ui.screens.ExperiencesScreen
import com.wego.mobile.sharmtogo.ui.screens.HomeScreen

@Composable
@Suppress("FunctionName")
fun WegoSharmToGoRoot(localeStore: LocaleStore = NoOpLocaleStore) {
    val localeState = remember { AppLocaleState(store = localeStore) }
    StgTheme(locale = localeState.locale, useDarkColors = isSystemInDarkTheme()) {
        Surface {
            WegoSharmToGoApp(localeState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
private fun WegoSharmToGoApp(localeState: AppLocaleState) {
    val navController = rememberNavController()
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
                    onBrowseAll = { navController.navigate(AppDestination.Experiences.navRoute()) },
                )
            }
            composable(
                route = AppDestination.Experiences.route,
                arguments =
                    listOf(
                        navArgument(AppDestination.Experiences.ARG_CATEGORY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) { entry ->
                val categoryArg = entry.arguments?.read { getStringOrNull(AppDestination.Experiences.ARG_CATEGORY) }.orEmpty()
                ExperiencesScreen(
                    locale = locale,
                    initialCategoryId = categoryArg.ifEmpty { null },
                    onServiceClick = { service -> navController.navigate(AppDestination.ExperienceDetail.navRoute(service.id)) },
                )
            }
            composable(
                route = AppDestination.ExperienceDetail.route,
                arguments = listOf(navArgument(AppDestination.ExperienceDetail.ARG_ID) { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.read { getStringOrNull(AppDestination.ExperienceDetail.ARG_ID) }.orEmpty()
                ExperienceDetailScreen(
                    id = id,
                    locale = locale,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun navRouteFor(destination: AppDestination): String =
    when (destination) {
        AppDestination.Experiences -> AppDestination.Experiences.navRoute()
        else -> destination.route
    }

private fun navLabel(
    destination: AppDestination,
    locale: AppLocale,
): String =
    when (destination) {
        AppDestination.Home -> SiteCopy.Nav.home.of(locale)
        AppDestination.Experiences -> SiteCopy.Nav.experiences.of(locale)
        else -> ""
    }

private fun navTitle(
    route: String?,
    locale: AppLocale,
): String =
    when (route) {
        AppDestination.Home.route -> SiteCopy.Nav.home.of(locale)
        AppDestination.Experiences.route -> SiteCopy.Nav.experiences.of(locale)
        else -> SiteCopy.Nav.home.of(locale)
    }
