package app.kcal.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.kcal.R
import app.kcal.feature.history.HistoryScreen
import app.kcal.feature.settings.SettingsScreen
import app.kcal.feature.today.TodayScreen
import app.kcal.feature.trends.TrendsScreen
import kotlin.reflect.KClass

/**
 * Bottom navigation destinations. Icons are Material 3 placeholders until the visual
 * references arrive (plan stage 8).
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    TODAY(TodayRoute, TodayRoute::class, R.string.nav_today, Icons.Filled.Home),
    TRENDS(TrendsRoute, TrendsRoute::class, R.string.nav_trends, Icons.Filled.DateRange),
    HISTORY(HistoryRoute, HistoryRoute::class, R.string.nav_history, Icons.AutoMirrored.Filled.List),
}

@Composable
fun KcalNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (currentDestination.isTopLevel()) {
                KcalBottomBar(
                    currentDestination = currentDestination,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(TodayRoute) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> {
                TodayScreen(onSettingsClick = { navController.navigate(SettingsRoute) })
            }
            composable<TrendsRoute> { TrendsScreen() }
            composable<HistoryRoute> { HistoryScreen() }
            composable<SettingsRoute> {
                SettingsScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun KcalBottomBar(currentDestination: NavDestination?, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = currentDestination?.hasRoute(destination.routeClass) == true,
                onClick = { onSelect(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = label) },
                label = { Text(text = label) },
            )
        }
    }
}

private fun NavDestination?.isTopLevel(): Boolean =
    this != null && TopLevelDestination.entries.any { hasRoute(it.routeClass) }
