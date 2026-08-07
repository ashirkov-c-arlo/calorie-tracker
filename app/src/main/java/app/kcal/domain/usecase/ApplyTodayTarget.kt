package app.kcal.domain.usecase

import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.repository.DailyTargetRepository
import java.time.LocalDate

/**
 * Makes the target stored for [localDate] consistent with [profile]: it writes the
 * recalculated snapshot, and removes a now-stale one when the profile yields no target.
 * The caller passes the date, so one logical operation cannot straddle midnight.
 */
class ApplyTodayTarget(
    private val dailyTargetRepository: DailyTargetRepository,
    private val calculateDailyTargets: CalculateDailyTargets,
) {
    suspend operator fun invoke(profile: StoredProfile, localDate: LocalDate): DailyTargetResult {
        val result = calculateDailyTargets.forStoredProfile(profile)
        when (result) {
            is DailyTargetResult.Available ->
                dailyTargetRepository.upsert(
                    DailyTargetSnapshot(
                        localDate = localDate,
                        targets = result.targets,
                        effectiveLossRateKgPerWeek = result.effectiveLossRateKgPerWeek,
                    ),
                )

            is DailyTargetResult.Unavailable -> dailyTargetRepository.delete(localDate)
        }
        return result
    }
}
