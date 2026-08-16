package app.kcal.feature.trends

import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.WeightEntry
import app.kcal.domain.usecase.BuildWeightTrend
import app.kcal.domain.usecase.UnitConversions
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import java.time.LocalDate

data class LoggedWeightsUiState(
    val isLoading: Boolean = true,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val editedDate: LocalDate? = null,
    val points: PersistentList<WeightPointUiState> = persistentListOf(),
)

private val previewStart = LocalDate.of(2026, 2, 16)

internal val loggedWeightsManyPointsPreviewState = loggedWeightsPreviewState(
    kilograms = listOf(
        84.2, 84.4, 83.9, 84.0, 83.6, 83.8, 83.5, 83.1, 83.4, 83.0,
        82.7, 82.9, 82.5, 82.2, 82.4, 82.0, 81.8, 82.1, 81.6, 81.4,
        81.7, 81.2, 81.0, 81.3, 80.8, 80.6, 80.9, 80.4, 80.2, 80.5,
    ),
    skipEvery = 5,
)

internal val loggedWeightsEditingPreviewState = loggedWeightsManyPointsPreviewState.let { state ->
    val selected = state.points[state.points.size - 4]
    state.copy(editedDate = selected.localDate)
}

private fun loggedWeightsPreviewState(kilograms: List<Double>, skipEvery: Int = 0): LoggedWeightsUiState {
    val entries =
        kilograms
            .mapIndexed { index, kg -> WeightEntry(previewStart.plusDays(index.toLong()), kg) }
            .filterIndexed { index, _ -> skipEvery == 0 || index % skipEvery != 0 }
    val points =
        BuildWeightTrend()(entries).map { point ->
            WeightPointUiState(localDate = point.localDate, value = point.kg, trendValue = point.trendKg)
        }
    return LoggedWeightsUiState(
        isLoading = false,
        points = points.toPersistentList(),
    )
}
