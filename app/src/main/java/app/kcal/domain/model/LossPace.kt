package app.kcal.domain.model

import kotlin.math.abs

/** How aggressively the user wants to lose weight. The kilograms per week are derived. */
enum class LossPace {
    SLOW,
    MODERATE,
    FAST,
}

/**
 * The three paces offered for a profile, in kilograms per week. They are the user's intent:
 * the domain guardrails are applied afterwards and any difference is explained, exactly as
 * for a hand-entered rate.
 */
data class LossPaceOptions(val slowKgPerWeek: Double, val moderateKgPerWeek: Double, val fastKgPerWeek: Double) {
    fun rateFor(pace: LossPace): Double = when (pace) {
        LossPace.SLOW -> slowKgPerWeek
        LossPace.MODERATE -> moderateKgPerWeek
        LossPace.FAST -> fastKgPerWeek
    }

    /**
     * The pace that stores exactly [rateKgPerWeek], or null when the stored rate is a value
     * the offered options do not produce. A stored rate is never rewritten to fit an option.
     */
    fun paceFor(rateKgPerWeek: Double): LossPace? =
        LossPace.entries.firstOrNull { abs(rateFor(it) - rateKgPerWeek) < MATCH_TOLERANCE_KG_PER_WEEK }

    private companion object {
        const val MATCH_TOLERANCE_KG_PER_WEEK = 0.005
    }
}
