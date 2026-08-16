package app.kcal.feature.today

import app.kcal.core.ui.MacroProgressUiState
import app.kcal.core.ui.macroProgress
import app.kcal.domain.model.MacroTotals
import app.kcal.domain.model.Macros
import app.kcal.domain.model.UnitSystem
import app.kcal.feature.profile.ProfileFieldError
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.time.LocalDate

/**
 * One journal row. [summary] is the confirmed one-line meal name; when it is null the row falls
 * back to [itemNames], which is what meals logged before summaries existed have.
 */
data class TodayMealUiState(
    val id: Long,
    val itemNames: PersistentList<String>,
    val totals: MacroTotals,
    val summary: String? = null,
)

data class TodayUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val consumed: MacroTotals = MacroTotals.ZERO,
    val progress: MacroProgressUiState? = null,
    val meals: PersistentList<TodayMealUiState> = persistentListOf(),
    val selectedDate: LocalDate = LocalDate.now(),
    val isToday: Boolean = true,
    /** Last 5 days ending with today for the day strip. */
    val dayStrip: PersistentList<DayStripItem> = persistentListOf(),
    /** Weight input state - only shown at the start of the day when no weight logged yet. */
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val weightInput: String = "",
    val weightInputError: ProfileFieldError? = null,
    val isWeightSaving: Boolean = false,
    val showWeightInput: Boolean = false,
)

data class DayStripItem(val date: LocalDate, val dayOfMonth: Int, val monthAbbr: String, val isSelected: Boolean)

private val previewDate = LocalDate.of(2026, 3, 18)

private val previewDayStrip = persistentListOf(
    DayStripItem(previewDate.minusDays(4), previewDate.minusDays(4).dayOfMonth, "Mar", false),
    DayStripItem(previewDate.minusDays(3), previewDate.minusDays(3).dayOfMonth, "Mar", false),
    DayStripItem(previewDate.minusDays(2), previewDate.minusDays(2).dayOfMonth, "Mar", false),
    DayStripItem(previewDate.minusDays(1), previewDate.minusDays(1).dayOfMonth, "Mar", false),
    DayStripItem(previewDate, previewDate.dayOfMonth, "Mar", true),
)

internal val todayEmptyPreviewState = TodayUiState(
    isLoading = false,
    progress = previewProgress(consumed = MacroTotals.ZERO),
    selectedDate = previewDate,
    dayStrip = previewDayStrip,
)

internal val todayNoTargetPreviewState = TodayUiState(
    isLoading = false,
    selectedDate = previewDate,
    dayStrip = previewDayStrip,
)

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
            summary = "oatmeal with a banana",
        ),
        TodayMealUiState(
            id = 2,
            itemNames = persistentListOf("Chicken omelette"),
            totals = MacroTotals.from(Macros(kcal = 395, proteinG = 39.0, fatG = 22.0, carbsG = 9.0)),
        ),
    ),
    selectedDate = previewDate,
    dayStrip = previewDayStrip,
)

internal val todayWithWeightInputPreviewState = TodayUiState(
    isLoading = false,
    progress = previewProgress(consumed = MacroTotals.ZERO),
    selectedDate = previewDate,
    dayStrip = previewDayStrip,
    showWeightInput = true,
    unitSystem = UnitSystem.METRIC,
    weightInput = "82.5",
)

internal val todayErrorPreviewState = TodayUiState(
    isLoading = false,
    hasError = true,
    selectedDate = previewDate,
    dayStrip = previewDayStrip,
)

private fun previewProgress(consumed: MacroTotals): MacroProgressUiState = macroProgress(
    consumed = consumed,
    target = Macros(kcal = 2050, proteinG = 105.0, fatG = 56.9, carbsG = 280.0),
)
