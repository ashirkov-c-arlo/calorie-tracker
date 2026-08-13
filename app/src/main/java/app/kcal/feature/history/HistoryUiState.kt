package app.kcal.feature.history

import app.kcal.core.ui.MacroProgressUiState
import app.kcal.core.ui.macroProgress
import app.kcal.domain.model.MacroTotals
import app.kcal.domain.model.Macros
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.time.LocalDate

/** Deleting a past meal must not hide the history, so its failure is a one-shot event. */
sealed interface HistoryEvent {
    data object DeleteFailed : HistoryEvent
}

data class HistoryMealUiState(val id: Long, val itemNames: PersistentList<String>, val totals: MacroTotals)

/**
 * A logged day. [progress] is built from the target snapshot saved for that date and is null
 * when no snapshot exists, so history never borrows the current target.
 */
data class HistoryDayUiState(
    val localDate: LocalDate,
    val consumed: MacroTotals,
    val progress: MacroProgressUiState?,
    val isExpanded: Boolean,
    val meals: PersistentList<HistoryMealUiState>,
)

data class HistoryWeekUiState(
    val weekOfYear: Int,
    val start: LocalDate,
    val end: LocalDate,
    val consumed: MacroTotals,
    val days: PersistentList<HistoryDayUiState>,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val weeks: PersistentList<HistoryWeekUiState> = persistentListOf(),
)

internal val historyEmptyPreviewState = HistoryUiState(isLoading = false)

internal val historyErrorPreviewState = HistoryUiState(isLoading = false, hasError = true)

/** Declared before the preview states below: top-level properties initialize in file order. */
private val previewTarget = Macros(kcal = 2_050, proteinG = 105.0, fatG = 56.9, carbsG = 280.0)

internal val historyContentPreviewState = HistoryUiState(
    isLoading = false,
    weeks =
    persistentListOf(
        HistoryWeekUiState(
            weekOfYear = 12,
            start = LocalDate.of(2026, 3, 16),
            end = LocalDate.of(2026, 3, 22),
            consumed = totals(kcal = 3_910, protein = 214.0, fat = 122.0, carbs = 407.0),
            days =
            persistentListOf(
                previewDay(
                    localDate = LocalDate.of(2026, 3, 17),
                    consumed = totals(kcal = 1_960, protein = 108.0, fat = 61.0, carbs = 205.0),
                    isExpanded = false,
                ),
                previewDay(
                    localDate = LocalDate.of(2026, 3, 16),
                    consumed = totals(kcal = 1_950, protein = 106.0, fat = 61.0, carbs = 202.0),
                    isExpanded = false,
                ),
            ),
        ),
        HistoryWeekUiState(
            weekOfYear = 11,
            start = LocalDate.of(2026, 3, 9),
            end = LocalDate.of(2026, 3, 15),
            consumed = totals(kcal = 815, protein = 54.0, fat = 31.0, carbs = 79.0),
            days =
            persistentListOf(
                previewDay(
                    localDate = LocalDate.of(2026, 3, 15),
                    consumed = totals(kcal = 815, protein = 54.0, fat = 31.0, carbs = 79.0),
                    isExpanded = false,
                    target = null,
                ),
            ),
        ),
    ),
)

internal val historyExpandedPreviewState = historyContentPreviewState.copy(
    weeks =
    historyContentPreviewState.weeks.replacingAt(
        0,
        historyContentPreviewState.weeks[0].let { week ->
            week.copy(days = week.days.replacingAt(0, week.days[0].copy(isExpanded = true)))
        },
    ),
)

private fun totals(kcal: Long, protein: Double, fat: Double, carbs: Double): MacroTotals =
    MacroTotals.from(Macros(kcal = kcal.toInt(), proteinG = protein, fatG = fat, carbsG = carbs))

private fun previewDay(
    localDate: LocalDate,
    consumed: MacroTotals,
    isExpanded: Boolean,
    target: Macros? = previewTarget,
): HistoryDayUiState = HistoryDayUiState(
    localDate = localDate,
    consumed = consumed,
    progress = target?.let { macroProgress(consumed, it) },
    isExpanded = isExpanded,
    meals =
    persistentListOf(
        HistoryMealUiState(
            id = 1,
            itemNames = persistentListOf("Oatmeal", "Banana"),
            totals = totals(kcal = 420, protein = 15.0, fat = 9.0, carbs = 70.0),
        ),
        HistoryMealUiState(
            id = 2,
            itemNames = persistentListOf("Chicken omelette"),
            totals = totals(kcal = 395, protein = 39.0, fat = 22.0, carbs = 9.0),
        ),
    ),
)
