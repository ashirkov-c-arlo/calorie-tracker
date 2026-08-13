package app.kcal.domain.model

import java.time.LocalDate

/** One morning measurement in canonical kilograms. There is exactly one entry per date. */
data class WeightEntry(val localDate: LocalDate, val kg: Double)

/**
 * A logged weight together with its calendar-window trend, both in kilograms. [trendKg] is
 * the mean of the entries inside `[localDate - 6 days, localDate]`; missing dates are never
 * interpolated.
 */
data class WeightPoint(val localDate: LocalDate, val kg: Double, val trendKg: Double)
