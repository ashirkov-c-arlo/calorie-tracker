package app.kcal.domain.usecase

import app.kcal.domain.model.WeightEntry
import app.kcal.domain.model.WeightPoint

/**
 * Turns raw weight entries into chronological points with their 7-day moving average.
 *
 * The window is a calendar window: for date `d` the trend is the arithmetic mean of the
 * entries whose local dates fall in the inclusive range `[d - 6 days, d]`. Missing dates are
 * not interpolated, and the last seven measurements are not substituted for the window.
 */
class BuildWeightTrend {

    operator fun invoke(entries: List<WeightEntry>): List<WeightPoint> {
        // ponytail: rescanning the window per point is O(n²) on the number of logged days;
        // switch to a two-pointer sum if that ever shows up in a measurement.
        val ordered = entries.sortedBy { it.localDate }
        return ordered.map { entry ->
            val windowStart = entry.localDate.minusDays(WINDOW_DAYS - 1)
            val window =
                ordered.filter { it.localDate >= windowStart && it.localDate <= entry.localDate }
            WeightPoint(
                localDate = entry.localDate,
                kg = entry.kg,
                trendKg = window.sumOf { it.kg } / window.size,
            )
        }
    }

    private companion object {
        const val WINDOW_DAYS = 7L
    }
}
