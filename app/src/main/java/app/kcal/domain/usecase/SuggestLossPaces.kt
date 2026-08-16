package app.kcal.domain.usecase

import app.kcal.domain.model.DeficitBand
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.LossPaceOptions
import app.kcal.domain.model.StoredProfile

/**
 * Derives what the three offered positions mean for this profile: the estimated weekly weight
 * loss produced by the low bound, the midpoint and the high bound of the applicable
 * [DeficitBand]. The user picks a position, never a rate.
 *
 * Each estimate comes from [CalculateDailyTargets] itself, so a shown rate is exactly the one
 * the stored target will produce, caps included. An estimate is null while the profile cannot
 * yet produce an energy expenditure, and no position is offered at all when no deficit
 * applies: below the reference body mass index the goal is maintenance.
 */
class SuggestLossPaces(private val calculateDailyTargets: CalculateDailyTargets) {

    /** Null when no deficit band applies or the measurements are missing or unusable. */
    operator fun invoke(profile: StoredProfile): LossPaceOptions? {
        DeficitBand.forBody(profile.currentWeightKg, profile.heightCm, profile.activityLevel) ?: return null
        return LossPaceOptions(
            slowKgPerWeek = profile.estimateKgPerWeek(LossPace.SLOW),
            moderateKgPerWeek = profile.estimateKgPerWeek(LossPace.MODERATE),
            fastKgPerWeek = profile.estimateKgPerWeek(LossPace.FAST),
        )
    }

    private fun StoredProfile.estimateKgPerWeek(pace: LossPace): Double? =
        (calculateDailyTargets.forStoredProfile(copy(lossPace = pace)) as? DailyTargetResult.Available)
            ?.effectiveLossRateKgPerWeek
}
