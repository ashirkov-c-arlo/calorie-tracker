package app.kcal.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kcal.R
import app.kcal.core.common.DecimalText
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.ui.ErrorScreen
import app.kcal.core.ui.LoadingScreen
import app.kcal.core.ui.currentLocale
import app.kcal.domain.model.MacroTotals
import app.kcal.domain.model.ThemeMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryRoute(onEditMealClick: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        uiState = uiState,
        onDayClick = viewModel::onDayClick,
        onEditMealClick = onEditMealClick,
        onDeleteMealClick = viewModel::onDeleteMeal,
        onRetry = viewModel::onRetry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onDayClick: (LocalDate) -> Unit,
    onEditMealClick: (Long) -> Unit,
    onDeleteMealClick: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.history_title)) }) },
    ) { contentPadding ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))

            uiState.hasError ->
                ErrorScreen(
                    message = stringResource(R.string.history_load_failed),
                    onRetry = onRetry,
                    modifier = Modifier.padding(contentPadding),
                )

            uiState.weeks.isEmpty() ->
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(contentPadding)
                        .padding(KcalSpacing.medium),
                )

            else ->
                HistoryContent(
                    uiState = uiState,
                    onDayClick = onDayClick,
                    onEditMealClick = onEditMealClick,
                    onDeleteMealClick = onDeleteMealClick,
                    modifier = Modifier.padding(contentPadding),
                )
        }
    }
}

@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    onDayClick: (LocalDate) -> Unit,
    onEditMealClick: (Long) -> Unit,
    onDeleteMealClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(KcalSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
    ) {
        uiState.weeks.forEach { week ->
            item(key = "week-${week.start.toEpochDay()}") { WeekHeader(week = week) }
            items(items = week.days, key = { day -> day.localDate.toEpochDay() }) { day ->
                DayCard(
                    day = day,
                    onClick = { onDayClick(day.localDate) },
                    onEditMealClick = onEditMealClick,
                    onDeleteMealClick = onDeleteMealClick,
                )
            }
        }
    }
}

@Composable
private fun WeekHeader(week: HistoryWeekUiState, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.extraSmall),
    ) {
        Text(
            text =
            stringResource(
                R.string.history_week_header,
                week.weekOfYear,
                dateFormatter.format(week.start),
                dateFormatter.format(week.end),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = consumedSummary(week.consumed), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DayCard(
    day: HistoryDayUiState,
    onClick: () -> Unit,
    onEditMealClick: (Long) -> Unit,
    onDeleteMealClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val dayFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.extraSmall),
        ) {
            Text(text = dayFormatter.format(day.localDate), style = MaterialTheme.typography.titleSmall)
            if (day.target == null) {
                Text(
                    text = stringResource(R.string.history_day_no_target),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text =
                    stringResource(
                        R.string.nutrient_progress_kcal,
                        DecimalText.formatLong(day.consumed.kcal, locale),
                        DecimalText.formatInt(day.target.kcal, locale),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            day.kcalFraction?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            Text(text = consumedSummary(day.consumed), style = MaterialTheme.typography.bodySmall)
            if (day.isExpanded) {
                day.meals.forEach { meal ->
                    HorizontalDivider()
                    MealRow(
                        meal = meal,
                        onEditClick = { onEditMealClick(meal.id) },
                        onDeleteClick = { onDeleteMealClick(meal.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MealRow(
    meal: HistoryMealUiState,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            meal.itemNames.forEach { name ->
                Text(text = name, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(R.string.meal_kcal, DecimalText.formatLong(meal.totals.kcal, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@Composable
private fun consumedSummary(consumed: MacroTotals): String {
    val locale = currentLocale()
    return stringResource(
        R.string.consumed_summary,
        DecimalText.formatLong(consumed.kcal, locale),
        DecimalText.format(consumed.proteinG, locale),
        DecimalText.format(consumed.fatG, locale),
        DecimalText.format(consumed.carbsG, locale),
    )
}

@Composable
private fun HistoryPreview(themeMode: ThemeMode, uiState: HistoryUiState) {
    KcalTheme(themeMode = themeMode) {
        HistoryScreen(
            uiState = uiState,
            onDayClick = {},
            onEditMealClick = {},
            onDeleteMealClick = {},
            onRetry = {},
        )
    }
}

@Preview(name = "History loading White")
@Composable
private fun HistoryLoadingWhitePreview() = HistoryPreview(ThemeMode.WHITE, HistoryUiState())

@Preview(name = "History loading Black")
@Composable
private fun HistoryLoadingBlackPreview() = HistoryPreview(ThemeMode.BLACK, HistoryUiState())

@Preview(name = "History empty White")
@Composable
private fun HistoryEmptyWhitePreview() = HistoryPreview(ThemeMode.WHITE, historyEmptyPreviewState)

@Preview(name = "History empty Black")
@Composable
private fun HistoryEmptyBlackPreview() = HistoryPreview(ThemeMode.BLACK, historyEmptyPreviewState)

@Preview(name = "History error White")
@Composable
private fun HistoryErrorWhitePreview() = HistoryPreview(ThemeMode.WHITE, historyErrorPreviewState)

@Preview(name = "History error Black")
@Composable
private fun HistoryErrorBlackPreview() = HistoryPreview(ThemeMode.BLACK, historyErrorPreviewState)

@Preview(name = "History content White")
@Composable
private fun HistoryContentWhitePreview() = HistoryPreview(ThemeMode.WHITE, historyContentPreviewState)

@Preview(name = "History content Black")
@Composable
private fun HistoryContentBlackPreview() = HistoryPreview(ThemeMode.BLACK, historyContentPreviewState)

@Preview(name = "History expanded day White")
@Composable
private fun HistoryExpandedWhitePreview() = HistoryPreview(ThemeMode.WHITE, historyExpandedPreviewState)

@Preview(name = "History expanded day Black")
@Composable
private fun HistoryExpandedBlackPreview() = HistoryPreview(ThemeMode.BLACK, historyExpandedPreviewState)
