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
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.kcal.R
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import app.kcal.feature.entry.EntryRoute
import app.kcal.feature.entry.ManualEntryRoute
import app.kcal.feature.history.HistoryRoute
import app.kcal.feature.settings.SettingsRoute
import app.kcal.feature.today.TodayRoute
import app.kcal.feature.today.TodayScreen
import app.kcal.feature.today.todayContentPreviewState
import app.kcal.feature.trends.TrendsRoute
import app.kcal.feature.trends.TrendsScreen
import app.kcal.feature.trends.trendsManyPointsPreviewState
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
    TODAY(TodayDestination, TodayDestination::class, R.string.nav_today, Icons.Filled.Home),
    TRENDS(TrendsDestination, TrendsDestination::class, R.string.nav_trends, Icons.Filled.DateRange),
    HISTORY(HistoryDestination, HistoryDestination::class, R.string.nav_history, Icons.AutoMirrored.Filled.List),
}

@Composable
fun KcalNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    todayContent: @Composable (
        onSettingsClick: () -> Unit,
        onAddMealClick: () -> Unit,
        onEditMealClick: (Long) -> Unit,
    ) -> Unit = { onSettingsClick, onAddMealClick, onEditMealClick ->
        TodayRoute(
            onSettingsClick = onSettingsClick,
            onAddMealClick = onAddMealClick,
            onEditMealClick = onEditMealClick,
        )
    },
    entryContent: @Composable (mealId: Long?, onClose: () -> Unit, onSwitchToAuto: (() -> Unit)?) -> Unit =
        { mealId, onClose, onSwitchToAuto ->
            ManualEntryRoute(mealId = mealId, onClose = onClose, onSwitchToAuto = onSwitchToAuto)
        },
    foodTextContent: @Composable (onClose: () -> Unit, onLogManually: () -> Unit) -> Unit =
        { onClose, onLogManually ->
            EntryRoute(onClose = onClose, onLogManually = onLogManually)
        },
    settingsContent: @Composable (onBackClick: () -> Unit) -> Unit = { onBackClick ->
        SettingsRoute(onBackClick = onBackClick)
    },
    historyContent: @Composable (onEditMealClick: (Long) -> Unit) -> Unit = { onEditMealClick ->
        HistoryRoute(onEditMealClick = onEditMealClick)
    },
    trendsContent: @Composable () -> Unit = { TrendsRoute() },
) {
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
                            popUpTo(TodayDestination) { saveState = true }
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
            startDestination = TodayDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayDestination> {
                todayContent(
                    { navController.navigate(SettingsDestination) },
                    { navController.navigate(EntryDestination) },
                    { mealId -> navController.navigate(EditMealDestination(mealId)) },
                )
            }
            composable<TrendsDestination> { trendsContent() }
            composable<HistoryDestination> {
                historyContent { mealId -> navController.navigate(EditMealDestination(mealId)) }
            }
            composable<SettingsDestination> {
                settingsContent { navController.popBackStack() }
            }
            composable<EntryDestination> {
                foodTextContent(
                    { navController.popBackStack() },
                    {
                        navController.navigate(ManualEntryDestination) {
                            popUpTo(EntryDestination) { inclusive = true }
                        }
                    },
                )
            }
            composable<ManualEntryDestination> {
                entryContent(
                    null,
                    { navController.popBackStack() },
                    {
                        navController.navigate(EntryDestination) {
                            popUpTo(ManualEntryDestination) { inclusive = true }
                        }
                    },
                )
            }
            composable<EditMealDestination> { backStackEntry ->
                val destination = backStackEntry.toRoute<EditMealDestination>()
                entryContent(destination.mealId, { navController.popBackStack() }, null)
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

@Composable
private fun PreviewNavHost() {
    KcalNavHost(
        todayContent = { onSettingsClick, onAddMealClick, onEditMealClick ->
            TodayScreen(
                uiState = todayContentPreviewState,
                onSettingsClick = onSettingsClick,
                onAddMealClick = onAddMealClick,
                onEditMealClick = onEditMealClick,
                onDeleteMealClick = {},
                onRetry = {},
            )
        },
        trendsContent = {
            TrendsScreen(
                uiState = trendsManyPointsPreviewState,
                onWeightChange = {},
                onSave = {},
                onEntryClick = {},
                onLogTodayClick = {},
                onRetry = {},
            )
        },
    )
}

@Preview(name = "Navigation White")
@Composable
private fun KcalNavHostWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        PreviewNavHost()
    }
}

@Preview(name = "Navigation Black")
@Composable
private fun KcalNavHostBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        PreviewNavHost()
    }
}
