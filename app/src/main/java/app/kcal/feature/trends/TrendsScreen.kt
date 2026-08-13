package app.kcal.feature.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import app.kcal.core.ui.currentLocale
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.feature.profile.components.DecimalField
import app.kcal.feature.trends.components.WeightChart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TrendsRoute(viewModel: TrendsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    TrendsScreen(
        uiState = uiState,
        onWeightChange = viewModel::onWeightChange,
        onSave = viewModel::onSave,
        onEntryClick = viewModel::onEntryClick,
        onLogTodayClick = viewModel::onLogTodayClick,
        onRetry = viewModel::onRetry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    uiState: TrendsUiState,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onEntryClick: (LocalDate) -> Unit,
    onLogTodayClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.trends_title)) }) },
    ) { contentPadding ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))

            uiState.hasError ->
                ErrorScreen(
                    message = stringResource(R.string.trends_load_failed),
                    onRetry = onRetry,
                    modifier = Modifier.padding(contentPadding),
                )

            else ->
                TrendsContent(
                    uiState = uiState,
                    onWeightChange = onWeightChange,
                    onSave = onSave,
                    onEntryClick = onEntryClick,
                    onLogTodayClick = onLogTodayClick,
                    modifier = Modifier.padding(contentPadding),
                )
        }
    }
}

@Composable
private fun TrendsContent(
    uiState: TrendsUiState,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onEntryClick: (LocalDate) -> Unit,
    onLogTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // The editor is the first card, so selecting a day far down the list has to bring it back.
    LaunchedEffect(uiState.editedDate) { scrollState.animateScrollTo(0) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(PaddingValues(KcalSpacing.medium)),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
    ) {
        WeightInputCard(
            uiState = uiState,
            onWeightChange = onWeightChange,
            onSave = onSave,
            onLogTodayClick = onLogTodayClick,
        )
        if (uiState.points.isEmpty()) {
            Text(text = stringResource(R.string.trends_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            WeightSeriesCard(uiState = uiState)
            LoggedWeightsCard(uiState = uiState, onEntryClick = onEntryClick)
        }
    }
}

@Composable
private fun WeightInputCard(
    uiState: TrendsUiState,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onLogTodayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.small),
        ) {
            uiState.editedDate?.let { editedDate ->
                Text(text = fullDate(editedDate), style = MaterialTheme.typography.titleMedium)
            }
            DecimalField(
                label = stringResource(R.string.trends_log_title),
                value = uiState.weightInput,
                unitLabel = stringResource(uiState.unitSystem.weightUnitRes()),
                error = uiState.inputError,
                onValueChange = onWeightChange,
            )
            if (uiState.saveFailed) {
                Text(
                    text = stringResource(R.string.error_save_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onSave, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.action_save))
            }
            if (!uiState.isEditingToday) {
                TextButton(onClick = onLogTodayClick, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.trends_log_today))
                }
            }
        }
    }
}

@Composable
private fun WeightSeriesCard(uiState: TrendsUiState, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val unit = stringResource(uiState.unitSystem.weightUnitRes())
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.small),
        ) {
            Text(text = stringResource(R.string.trends_chart_title), style = MaterialTheme.typography.titleMedium)
            WeightChart(points = uiState.points)
            uiState.latest?.let { latest ->
                Text(
                    text = stringResource(R.string.trends_latest, DecimalText.format(latest.value, locale), unit),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                    stringResource(R.string.trends_average, DecimalText.format(latest.trendValue, locale), unit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(R.string.trends_chart_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Any logged day can be selected here, which is how a wrong past entry is corrected. */
@Composable
private fun LoggedWeightsCard(
    uiState: TrendsUiState,
    onEntryClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val unit = stringResource(uiState.unitSystem.weightUnitRes())
    val editLabel = stringResource(R.string.trends_edit_entry_content_description)
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.extraSmall),
        ) {
            Text(text = stringResource(R.string.trends_entries_title), style = MaterialTheme.typography.titleMedium)
            uiState.points.asReversed().forEachIndexed { index, point ->
                if (index > 0) HorizontalDivider()
                val isSelected = point.localDate == uiState.editedDate
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = KcalSpacing.minTouchTarget)
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(onClickLabel = editLabel) { onEntryClick(point.localDate) }
                        .semantics { selected = isSelected }
                        .padding(horizontal = KcalSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small),
                ) {
                    Text(
                        text = mediumDate(point.localDate),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                        stringResource(R.string.trends_entry_value, DecimalText.format(point.value, locale), unit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun fullDate(localDate: LocalDate): String {
    val locale = currentLocale()
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    return formatter.format(localDate)
}

@Composable
private fun mediumDate(localDate: LocalDate): String {
    val locale = currentLocale()
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    return formatter.format(localDate)
}

private fun UnitSystem.weightUnitRes(): Int = when (this) {
    UnitSystem.METRIC -> R.string.unit_kg
    UnitSystem.IMPERIAL -> R.string.unit_lb
}

@Composable
private fun TrendsPreview(themeMode: ThemeMode, uiState: TrendsUiState) {
    KcalTheme(themeMode = themeMode) {
        TrendsScreen(
            uiState = uiState,
            onWeightChange = {},
            onSave = {},
            onEntryClick = {},
            onLogTodayClick = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Trends loading White")
@Composable
private fun TrendsLoadingWhitePreview() = TrendsPreview(ThemeMode.WHITE, TrendsUiState())

@Preview(name = "Trends loading Black")
@Composable
private fun TrendsLoadingBlackPreview() = TrendsPreview(ThemeMode.BLACK, TrendsUiState())

@Preview(name = "Trends empty White")
@Composable
private fun TrendsEmptyWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsEmptyPreviewState)

@Preview(name = "Trends empty Black")
@Composable
private fun TrendsEmptyBlackPreview() = TrendsPreview(ThemeMode.BLACK, trendsEmptyPreviewState)

@Preview(name = "Trends error White")
@Composable
private fun TrendsErrorWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsErrorPreviewState)

@Preview(name = "Trends error Black")
@Composable
private fun TrendsErrorBlackPreview() = TrendsPreview(ThemeMode.BLACK, trendsErrorPreviewState)

@Preview(name = "Trends invalid input White")
@Composable
private fun TrendsInvalidInputPreview() = TrendsPreview(ThemeMode.WHITE, trendsInvalidInputPreviewState)

@Preview(name = "Trends one point White", heightDp = 1000)
@Composable
private fun TrendsSinglePointWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsSinglePointPreviewState)

@Preview(name = "Trends one point Black", heightDp = 1000)
@Composable
private fun TrendsSinglePointBlackPreview() = TrendsPreview(ThemeMode.BLACK, trendsSinglePointPreviewState)

@Preview(name = "Trends two points White", heightDp = 1000)
@Composable
private fun TrendsTwoPointsWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsTwoPointsPreviewState)

@Preview(name = "Trends two points Black", heightDp = 1000)
@Composable
private fun TrendsTwoPointsBlackPreview() = TrendsPreview(ThemeMode.BLACK, trendsTwoPointsPreviewState)

@Preview(name = "Trends many points White", heightDp = 2400)
@Composable
private fun TrendsManyPointsWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsManyPointsPreviewState)

@Preview(name = "Trends many points Black", heightDp = 2400)
@Composable
private fun TrendsManyPointsBlackPreview() = TrendsPreview(ThemeMode.BLACK, trendsManyPointsPreviewState)

@Preview(name = "Trends editing a past day White", heightDp = 2400)
@Composable
private fun TrendsEditingPastWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsEditingPastPreviewState)

@Preview(name = "Trends imperial White", heightDp = 2400)
@Composable
private fun TrendsImperialWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsImperialPreviewState)
