package app.kcal.domain.usecase

import app.kcal.domain.model.LossPaceOptions
import app.kcal.domain.model.StoredProfile
import kotlin.math.round

/**
 * Derives the three offered paces from the current body weight alone.
 *
 * Each pace is a share of body weight per week, which is the same quantity the approved
 * `1% of body weight` guardrail uses, so the fastest option never exceeds it. The remaining
 * guardrails, the 20% of energy expenditure deficit cap and the 750 kcal cap, are
 * deliberately *not* pre-applied: the selected pace is stored as the
 * user's intent and [CalculateDailyTargets] reports the effective pace together with the
 * reason it differs.
 *
 * Depending only on body weight also keeps the options available while the target itself is
 * unavailable, for example below the minimum age, so no fabricated rate is ever needed.
 */
class SuggestLossPaces {

    /** Null only while the current weight is missing or unusable. */
    operator fun invoke(profile: StoredProfile): LossPaceOptions? {
        val weightKg = profile.currentWeightKg ?: return null
        if (!weightKg.isFinite() || weightKg <= 0.0) return null
        return LossPaceOptions(
            slowKgPerWeek = weightKg.share(SLOW_FRACTION),
            moderateKgPerWeek = weightKg.share(MODERATE_FRACTION),
            fastKgPerWeek = weightKg.share(FAST_FRACTION),
        )
    }

    private fun Double.share(fraction: Double): Double =
        (round(this * fraction / RATE_STEP) * RATE_STEP).coerceAtLeast(RATE_STEP)

    private companion object {
        /** Weekly shares of body weight; the approved guardrail allows at most 1%. */
        const val SLOW_FRACTION = 0.0025
        const val MODERATE_FRACTION = 0.005
        const val FAST_FRACTION = 0.0075
        const val RATE_STEP = 0.01
    }
}
