package app.kcal.feature.trends.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.kcal.R
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.ui.currentLocale
import app.kcal.domain.model.ThemeMode
import app.kcal.feature.trends.WeightPointUiState
import app.kcal.feature.trends.trendsManyPointsPreviewState
import app.kcal.feature.trends.trendsSinglePointPreviewState
import app.kcal.feature.trends.trendsTwoPointsPreviewState
import kotlinx.collections.immutable.PersistentList
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Raw weight points plus their 7-day moving average, drawn on a Canvas. Dates are placed on
 * the calendar axis, so a gap between entries stays a visible gap. Values are already in the
 * displayed unit system; nothing here converts or aggregates.
 */
@Composable
fun WeightChart(points: PersistentList<WeightPointUiState>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val locale = currentLocale()
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val description =
        stringResource(
            R.string.trends_chart_content_description,
            dateFormatter.format(points.first().localDate),
            dateFormatter.format(points.last().localDate),
        )
    val pointColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trendColor = MaterialTheme.colorScheme.onSurface
    val minimum = points.minOf { minOf(it.value, it.trendValue) }
    val maximum = points.maxOf { maxOf(it.value, it.trendValue) }
    val firstDay = points.first().localDate.toEpochDay()
    val lastDay = points.last().localDate.toEpochDay()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(KcalSpacing.chartHeight)
            .semantics { contentDescription = description },
    ) {
        val radius = KcalSpacing.chartPointRadius.toPx()
        val horizontalSpan = size.width - 2 * radius
        val verticalSpan = size.height - 2 * radius
        val valueSpan = maximum - minimum
        val daySpan = lastDay - firstDay

        fun offset(point: WeightPointUiState, value: Double): Offset {
            val x =
                if (daySpan == 0L) {
                    size.width / 2f
                } else {
                    radius + horizontalSpan * (point.localDate.toEpochDay() - firstDay) / daySpan
                }
            val y =
                if (valueSpan == 0.0) {
                    size.height / 2f
                } else {
                    radius + verticalSpan * ((maximum - value) / valueSpan).toFloat()
                }
            return Offset(x, y)
        }

        val trend = Path()
        points.forEachIndexed { index, point ->
            val trendOffset = offset(point, point.trendValue)
            if (index == 0) trend.moveTo(trendOffset.x, trendOffset.y) else trend.lineTo(trendOffset.x, trendOffset.y)
        }
        drawPath(
            path = trend,
            color = trendColor,
            style = Stroke(width = KcalSpacing.chartLineWidth.toPx(), cap = StrokeCap.Round),
        )
        points.forEach { point ->
            drawCircle(color = pointColor, radius = radius, center = offset(point, point.value))
        }
    }
}

@Composable
private fun WeightChartPreview(themeMode: ThemeMode, points: PersistentList<WeightPointUiState>) {
    KcalTheme(themeMode = themeMode) {
        Surface {
            WeightChart(points = points, modifier = Modifier.padding(KcalSpacing.medium))
        }
    }
}

@Preview(name = "Weight chart White", widthDp = 360, heightDp = 200)
@Composable
private fun WeightChartWhitePreview() = WeightChartPreview(ThemeMode.WHITE, trendsManyPointsPreviewState.points)

@Preview(name = "Weight chart Black", widthDp = 360, heightDp = 200)
@Composable
private fun WeightChartBlackPreview() = WeightChartPreview(ThemeMode.BLACK, trendsManyPointsPreviewState.points)

@Preview(name = "Weight chart one point White", widthDp = 360, heightDp = 200)
@Composable
private fun WeightChartSinglePointPreview() = WeightChartPreview(ThemeMode.WHITE, trendsSinglePointPreviewState.points)

@Preview(name = "Weight chart two points Black", widthDp = 360, heightDp = 200)
@Composable
private fun WeightChartTwoPointsPreview() = WeightChartPreview(ThemeMode.BLACK, trendsTwoPointsPreviewState.points)
