package app.kcal.feature.today

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kcal.R
import app.kcal.core.common.DecimalText
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.ui.ErrorScreen
import app.kcal.core.ui.LoadingScreen
import app.kcal.core.ui.MacroProgressLines
import app.kcal.core.ui.MacroProgressUiState
import app.kcal.core.ui.currentLocale
import app.kcal.domain.model.MacroTotals
import app.kcal.domain.model.ThemeMode
import java.time.LocalDate

@Composable
fun TodayRoute(
    onSettingsClick: () -> Unit,
    onAddMealClick: () -> Unit,
    onEditMealClick: (Long) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    TodayScreen(
        uiState = uiState,
        onSettingsClick = onSettingsClick,
        onAddMealClick = onAddMealClick,
        onEditMealClick = onEditMealClick,
        onDeleteMealClick = viewModel::onDeleteMeal,
        onSelectDate = viewModel::onSelectDate,
        onPageChanged = viewModel::onSelectDate,
        onRetry = viewModel::onRetry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onSettingsClick: () -> Unit,
    onAddMealClick: () -> Unit,
    onEditMealClick: (Long) -> Unit,
    onDeleteMealClick: (Long) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onPageChanged: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.today_title)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_open_content_description),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && !uiState.hasError && uiState.isToday) {
                FloatingActionButton(onClick = onAddMealClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.today_add_meal_content_description),
                    )
                }
            }
        },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            if (uiState.dayStrip.isNotEmpty()) {
                DayStrip(
                    items = uiState.dayStrip,
                    onDayClick = onSelectDate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KcalSpacing.medium, vertical = KcalSpacing.small),
                )
            }

            when {
                uiState.isLoading -> LoadingScreen()

                uiState.hasError -> ErrorScreen(
                    message = stringResource(R.string.today_load_failed),
                    onRetry = onRetry,
                )

                else -> DayPager(
                    uiState = uiState,
                    onEditMealClick = onEditMealClick,
                    onDeleteMealClick = onDeleteMealClick,
                    onPageChanged = onPageChanged,
                )
            }
        }
    }
}

@Composable
private fun DayStrip(items: List<DayStripItem>, onDayClick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            DayChip(day = item, onClick = { onDayClick(item.date) })
        }
    }
}

@Composable
private fun DayChip(day: DayStripItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (day.isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (day.isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = KcalSpacing.medium, vertical = KcalSpacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
            )
            Text(
                text = day.monthAbbr,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
            )
        }
    }
}

@Composable
private fun DayPager(
    uiState: TodayUiState,
    onEditMealClick: (Long) -> Unit,
    onDeleteMealClick: (Long) -> Unit,
    onPageChanged: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = 31
    val todayIndex = pageCount - 1
    val selectedIndex = uiState.dayStrip.indexOfFirst { it.isSelected }
        .let { if (it < 0) todayIndex else todayIndex - (uiState.dayStrip.size - 1 - it) }

    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val daysFromToday = todayIndex - page
            val date = uiState.dayStrip.lastOrNull()?.date?.minusDays(daysFromToday.toLong())
            if (date != null && date != uiState.selectedDate) {
                onPageChanged(date)
            }
        }
    }

    LaunchedEffect(uiState.selectedDate) {
        val todayDate = uiState.dayStrip.lastOrNull()?.date ?: return@LaunchedEffect
        val daysFromToday = todayDate.toEpochDay() - uiState.selectedDate.toEpochDay()
        val targetPage = (todayIndex - daysFromToday.toInt()).coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { _ ->
        TodayContent(
            uiState = uiState,
            onEditMealClick = onEditMealClick,
            onDeleteMealClick = onDeleteMealClick,
        )
    }
}

@Composable
private fun TodayContent(
    uiState: TodayUiState,
    onEditMealClick: (Long) -> Unit,
    onDeleteMealClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = KcalSpacing.medium,
            top = KcalSpacing.medium,
            end = KcalSpacing.medium,
            bottom = KcalSpacing.medium + KcalSpacing.extraLarge + KcalSpacing.extraLarge,
        ),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
    ) {
        item { DailyProgressCard(consumed = uiState.consumed, progress = uiState.progress) }
        if (uiState.meals.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.today_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            items(items = uiState.meals, key = TodayMealUiState::id) { meal ->
                MealCard(
                    meal = meal,
                    onEditClick = { onEditMealClick(meal.id) },
                    onDeleteClick = { onDeleteMealClick(meal.id) },
                    showActions = uiState.isToday,
                )
            }
        }
    }
}

@Composable
private fun DailyProgressCard(consumed: MacroTotals, progress: MacroProgressUiState?, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.small),
        ) {
            Text(text = stringResource(R.string.today_progress_title), style = MaterialTheme.typography.titleMedium)
            if (progress == null) {
                Text(
                    text = stringResource(
                        R.string.consumed_summary,
                        DecimalText.formatLong(consumed.kcal, locale),
                        DecimalText.format(consumed.proteinG, locale),
                        DecimalText.format(consumed.fatG, locale),
                        DecimalText.format(consumed.carbsG, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.today_target_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MacroProgressLines(progress = progress)
            }
        }
    }
}

@Composable
private fun MealCard(
    meal: TodayMealUiState,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    showActions: Boolean,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.extraSmall),
        ) {
            Text(
                text = meal.summary ?: meal.itemNames.joinToString(stringResource(R.string.meal_items_separator)),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.consumed_summary,
                    DecimalText.formatLong(meal.totals.kcal, locale),
                    DecimalText.format(meal.totals.proteinG, locale),
                    DecimalText.format(meal.totals.fatG, locale),
                    DecimalText.format(meal.totals.carbsG, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showActions) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.meal_edit_content_description),
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.meal_delete_content_description),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayPreview(themeMode: ThemeMode, uiState: TodayUiState) {
    KcalTheme(themeMode = themeMode) {
        TodayScreen(
            uiState = uiState,
            onSettingsClick = {},
            onAddMealClick = {},
            onEditMealClick = {},
            onDeleteMealClick = {},
            onSelectDate = {},
            onPageChanged = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Day strip White", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Day strip Black", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DayStripPreview() {
    KcalTheme(themeMode = ThemeMode.SYSTEM) {
        DayStrip(
            items = todayContentPreviewState.dayStrip,
            onDayClick = {},
            modifier = Modifier.padding(KcalSpacing.medium),
        )
    }
}

@Preview(name = "Daily progress White", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Daily progress Black", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyProgressCardPreview() {
    KcalTheme(themeMode = ThemeMode.SYSTEM) {
        DailyProgressCard(
            consumed = todayContentPreviewState.consumed,
            progress = todayContentPreviewState.progress,
            modifier = Modifier.padding(KcalSpacing.medium),
        )
    }
}

@Preview(name = "Meal card White", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Meal card Black", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MealCardPreview() {
    KcalTheme(themeMode = ThemeMode.SYSTEM) {
        MealCard(
            meal = todayContentPreviewState.meals.first(),
            onEditClick = {},
            onDeleteClick = {},
            showActions = true,
            modifier = Modifier.padding(KcalSpacing.medium),
        )
    }
}

@Preview(name = "Today loading White")
@Composable
private fun TodayLoadingWhitePreview() = TodayPreview(ThemeMode.WHITE, TodayUiState())

@Preview(name = "Today loading Black")
@Composable
private fun TodayLoadingBlackPreview() = TodayPreview(ThemeMode.BLACK, TodayUiState())

@Preview(name = "Today empty White")
@Composable
private fun TodayEmptyWhitePreview() = TodayPreview(ThemeMode.WHITE, todayEmptyPreviewState)

@Preview(name = "Today empty Black")
@Composable
private fun TodayEmptyBlackPreview() = TodayPreview(ThemeMode.BLACK, todayEmptyPreviewState)

@Preview(name = "Today no target White")
@Composable
private fun TodayNoTargetWhitePreview() = TodayPreview(ThemeMode.WHITE, todayNoTargetPreviewState)

@Preview(name = "Today no target Black")
@Composable
private fun TodayNoTargetBlackPreview() = TodayPreview(ThemeMode.BLACK, todayNoTargetPreviewState)

@Preview(name = "Today error White")
@Composable
private fun TodayErrorWhitePreview() = TodayPreview(ThemeMode.WHITE, todayErrorPreviewState)

@Preview(name = "Today error Black")
@Composable
private fun TodayErrorBlackPreview() = TodayPreview(ThemeMode.BLACK, todayErrorPreviewState)

@Preview(name = "Today content White")
@Composable
private fun TodayContentWhitePreview() = TodayPreview(ThemeMode.WHITE, todayContentPreviewState)

@Preview(name = "Today content Black")
@Composable
private fun TodayContentBlackPreview() = TodayPreview(ThemeMode.BLACK, todayContentPreviewState)
