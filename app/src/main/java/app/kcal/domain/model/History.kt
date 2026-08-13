package app.kcal.domain.model

import java.time.LocalDate

/**
 * A logged day with the target that was saved for it. [target] is null when no snapshot
 * exists for that date; history never falls back to the current settings.
 */
data class HistoryDay(
    val localDate: LocalDate,
    val consumed: MacroTotals,
    val target: Macros?,
    val meals: List<MealEntry>,
)

/** An ISO-8601 week (Monday start) covering the logged days between [start] and [end]. */
data class HistoryWeek(
    val weekBasedYear: Int,
    val weekOfYear: Int,
    val start: LocalDate,
    val end: LocalDate,
    val consumed: MacroTotals,
    val days: List<HistoryDay>,
)
