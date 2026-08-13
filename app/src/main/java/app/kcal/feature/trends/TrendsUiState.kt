package app.kcal.feature.trends

import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.WeightEntry
import app.kcal.domain.usecase.BuildWeightTrend
import app.kcal.domain.usecase.UnitConversions
import app.kcal.feature.profile.ProfileFieldError
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import java.time.LocalDate

/** One chart point, already converted to the displayed unit system. */
data class WeightPointUiState(val localDate: LocalDate, val value: Double, val trendValue: Double)

/**
 * Trends state. [points] are oldest first, so the newest entry is the last one. Values are in
 * the displayed unit system while storage stays in kilograms.
 */
data class TrendsUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val weightInput: String = "",
    val inputError: ProfileFieldError? = null,
    val saveFailed: Boolean = false,
    val points: PersistentList<WeightPointUiState> = persistentListOf(),
) {
    val latest: WeightPointUiState? get() = points.lastOrNull()
}

internal val trendsEmptyPreviewState = TrendsUiState(isLoading = false)

internal val trendsErrorPreviewState = TrendsUiState(isLoading = false, hasError = true)

internal val trendsInvalidInputPreviewState = TrendsUiState(
    isLoading = false,
    weightInput = "8oo",
    inputError = ProfileFieldError.INVALID_NUMBER,
)

/** Declared before the preview states below: top-level properties initialize in file order. */
private val previewStart = LocalDate.of(2026, 2, 16)

internal val trendsSinglePointPreviewState = previewState(listOf(82.4))

internal val trendsTwoPointsPreviewState = previewState(listOf(82.4, 82.0))

/** A month of entries with deliberate gaps, so the calendar window is visible in previews. */
internal val trendsManyPointsPreviewState = previewState(
    kilograms =
    listOf(
        84.2, 84.4, 83.9, 84.0, 83.6, 83.8, 83.5, 83.1, 83.4, 83.0,
        82.7, 82.9, 82.5, 82.2, 82.4, 82.0, 81.8, 82.1, 81.6, 81.4,
        81.7, 81.2, 81.0, 81.3, 80.8, 80.6, 80.9, 80.4, 80.2, 80.5,
    ),
    skipEvery = 5,
)

internal val trendsImperialPreviewState = trendsManyPointsPreviewState.let { state ->
    state.copy(
        unitSystem = UnitSystem.IMPERIAL,
        weightInput = "177.5",
        points = state.points.map { it.inPounds() }.toPersistentList(),
    )
}

private fun WeightPointUiState.inPounds(): WeightPointUiState = copy(
    value = UnitConversions.kilogramsToPounds(value),
    trendValue = UnitConversions.kilogramsToPounds(trendValue),
)

/** Preview points come from the real use case, so previews cannot drift from the trend rule. */
private fun previewState(kilograms: List<Double>, skipEvery: Int = 0): TrendsUiState {
    val entries =
        kilograms
            .mapIndexed { index, kg -> WeightEntry(previewStart.plusDays(index.toLong()), kg) }
            .filterIndexed { index, _ -> skipEvery == 0 || index % skipEvery != 0 }
    val points =
        BuildWeightTrend()(entries).map { point ->
            WeightPointUiState(localDate = point.localDate, value = point.kg, trendValue = point.trendKg)
        }
    return TrendsUiState(
        isLoading = false,
        weightInput = points.last().value.toString(),
        points = points.toPersistentList(),
    )
}
