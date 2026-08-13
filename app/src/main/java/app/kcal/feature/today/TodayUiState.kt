package app.kcal.feature.today

import app.kcal.domain.model.MacroTotals
import app.kcal.domain.model.Macros
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/** Progress fractions are prepared outside Compose so the screen remains presentation-only. */
data class TodayMacroProgressUiState(
    val consumed: MacroTotals,
    val target: Macros,
    val kcalFraction: Float,
    val proteinFraction: Float,
    val fatFraction: Float,
    val carbsFraction: Float,
)

data class TodayMealUiState(val id: Long, val itemNames: PersistentList<String>, val totals: MacroTotals)

data class TodayUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val consumed: MacroTotals = MacroTotals.ZERO,
    val progress: TodayMacroProgressUiState? = null,
    val meals: PersistentList<TodayMealUiState> = persistentListOf(),
)

internal val todayEmptyPreviewState = TodayUiState(
    isLoading = false,
    progress = previewProgress(consumed = MacroTotals.ZERO),
)

internal val todayNoTargetPreviewState = TodayUiState(isLoading = false)

internal val todayContentPreviewState = TodayUiState(
    isLoading = false,
    consumed = MacroTotals.from(Macros(kcal = 815, proteinG = 54.0, fatG = 31.0, carbsG = 79.0)),
    progress =
    previewProgress(
        consumed = MacroTotals.from(Macros(kcal = 815, proteinG = 54.0, fatG = 31.0, carbsG = 79.0)),
    ),
    meals =
    persistentListOf(
        TodayMealUiState(
            id = 1,
            itemNames = persistentListOf("Oatmeal", "Banana"),
            totals = MacroTotals.from(Macros(kcal = 420, proteinG = 15.0, fatG = 9.0, carbsG = 70.0)),
        ),
        TodayMealUiState(
            id = 2,
            itemNames = persistentListOf("Chicken omelette"),
            totals = MacroTotals.from(Macros(kcal = 395, proteinG = 39.0, fatG = 22.0, carbsG = 9.0)),
        ),
    ),
)

internal val todayErrorPreviewState = TodayUiState(isLoading = false, hasError = true)

private fun previewProgress(consumed: MacroTotals): TodayMacroProgressUiState {
    val target = Macros(kcal = 2050, proteinG = 105.0, fatG = 56.9, carbsG = 280.0)
    return TodayMacroProgressUiState(
        consumed = consumed,
        target = target,
        kcalFraction = consumed.kcal.toFloat() / target.kcal,
        proteinFraction = consumed.proteinG.toFloat() / target.proteinG.toFloat(),
        fatFraction = consumed.fatG.toFloat() / target.fatG.toFloat(),
        carbsFraction = consumed.carbsG.toFloat() / target.carbsG.toFloat(),
    )
}
