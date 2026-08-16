package app.kcal.feature.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import app.kcal.feature.trends.components.WeightChart

@Composable
fun TrendsRoute(
    onLoggedWeightsClick: () -> Unit = {},
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    TrendsScreen(
        uiState = uiState,
        onChartClick = onLoggedWeightsClick,
        onRetry = viewModel::onRetry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    uiState: TrendsUiState,
    onChartClick: () -> Unit,
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
                    onChartClick = onChartClick,
                    modifier = Modifier.padding(contentPadding),
                )
        }
    }
}

@Composable
private fun TrendsContent(
    uiState: TrendsUiState,
    onChartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(PaddingValues(KcalSpacing.medium)),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
    ) {
        if (uiState.points.isEmpty()) {
            Text(text = stringResource(R.string.trends_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            WeightSeriesCard(uiState = uiState, onClick = onChartClick)
        }
    }
}

@Composable
private fun WeightSeriesCard(uiState: TrendsUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val unit = stringResource(uiState.unitSystem.weightUnitRes())
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
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

private fun UnitSystem.weightUnitRes(): Int = when (this) {
    UnitSystem.METRIC -> R.string.unit_kg
    UnitSystem.IMPERIAL -> R.string.unit_lb
}

@Composable
private fun TrendsPreview(themeMode: ThemeMode, uiState: TrendsUiState) {
    KcalTheme(themeMode = themeMode) {
        TrendsScreen(
            uiState = uiState,
            onChartClick = {},
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

@Preview(name = "Trends many points White", heightDp = 1000)
@Composable
private fun TrendsManyPointsWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsManyPointsPreviewState)

@Preview(name = "Trends many points Black", heightDp = 1000)
@Composable
private fun TrendsManyPointsBlackPreview() = TrendsPreview(ThemeMode.BLACK, trendsManyPointsPreviewState)

@Preview(name = "Trends imperial White", heightDp = 1000)
@Composable
private fun TrendsImperialWhitePreview() = TrendsPreview(ThemeMode.WHITE, trendsImperialPreviewState)
