package app.kcal.domain.model

import java.time.LocalDate

/** The target that was active on [localDate]. Past snapshots are immutable. */
data class DailyTargetSnapshot(val localDate: LocalDate, val targets: Macros, val effectiveLossRateKgPerWeek: Double)
