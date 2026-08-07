package app.kcal.domain.usecase

import app.kcal.domain.model.LossPaceOptions
import app.kcal.domain.model.StoredProfile
import kotlin.math.floor

/**
 * Derives the three offered paces from the profile itself.
 *
 * The fastest pace is the fastest one the approved guardrails actually allow: it is measured
 * by asking [CalculateDailyTargets] for the effective pace at the highest requestable rate,
 * so the 1 kg per week limit, the 1% of body weight limit, the 20% of energy expenditure
 * deficit cap, the 750 kcal cap and the minimum intake floor are all respected. The slower
 * paces are fractions of that maximum, and every value is rounded down so none of them can
 * re-trigger a guardrail.
 */
class SuggestLossPaces(private val calculateDailyTargets: CalculateDailyTargets) {

    /** Null when the profile is incomplete or already at or below the target weight. */
    operator fun invoke(profile: StoredProfile): LossPaceOptions? {
        val probe = profile.copy(requestedLossRateKgPerWeek = MAX_REQUESTABLE_RATE_KG_PER_WEEK)
        val result = calculateDailyTargets.forStoredProfile(probe)
        if (result !is DailyTargetResult.Available) return null

        val fastest = result.effectiveLossRateKgPerWeek.roundedDown()
        if (fastest <= 0.0) return null

        return LossPaceOptions(
            slowKgPerWeek = (fastest * SLOW_FRACTION).roundedDown().coerceAtLeast(RATE_STEP),
            moderateKgPerWeek = (fastest * MODERATE_FRACTION).roundedDown().coerceAtLeast(RATE_STEP),
            fastKgPerWeek = fastest,
        )
    }

    private fun Double.roundedDown(): Double = floor(this / RATE_STEP) * RATE_STEP

    private companion object {
        /** The guardrails never allow more than 1 kg per week, so this probes the maximum. */
        const val MAX_REQUESTABLE_RATE_KG_PER_WEEK = 1.0
        const val RATE_STEP = 0.01
        const val SLOW_FRACTION = 1.0 / 3.0
        const val MODERATE_FRACTION = 2.0 / 3.0
    }
}
