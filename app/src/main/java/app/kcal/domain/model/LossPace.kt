package app.kcal.domain.model

/** How aggressively the user wants to lose weight. The kilograms per week are derived. */
enum class LossPace {
    SLOW,
    MODERATE,
    FAST,
}

/**
 * The three paces offered for a profile, in kilograms per week. Every value already fits
 * inside the product guardrails, so picking any of them changes the calorie target without
 * triggering a rate or deficit warning.
 */
data class LossPaceOptions(val slowKgPerWeek: Double, val moderateKgPerWeek: Double, val fastKgPerWeek: Double) {
    fun rateFor(pace: LossPace): Double = when (pace) {
        LossPace.SLOW -> slowKgPerWeek
        LossPace.MODERATE -> moderateKgPerWeek
        LossPace.FAST -> fastKgPerWeek
    }

    /** Maps a stored rate back to the closest offered pace. */
    fun paceClosestTo(rateKgPerWeek: Double): LossPace =
        LossPace.entries.minBy { pace -> kotlin.math.abs(rateFor(pace) - rateKgPerWeek) }
}
