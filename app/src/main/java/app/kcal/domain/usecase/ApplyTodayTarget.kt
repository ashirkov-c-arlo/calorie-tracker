package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.repository.DailyTargetRepository

/**
 * Makes today's stored target consistent with [profile]: it writes the recalculated
 * snapshot, and removes a now-stale one when the profile no longer yields a target. Past
 * snapshots are never touched.
 */
class ApplyTodayTarget(
    private val dailyTargetRepository: DailyTargetRepository,
    private val calculateDailyTargets: CalculateDailyTargets,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(profile: StoredProfile): DailyTargetResult {
        val result = calculateDailyTargets.forStoredProfile(profile)
        val today = timeProvider.today()
        when (result) {
            is DailyTargetResult.Available ->
                dailyTargetRepository.upsert(
                    DailyTargetSnapshot(
                        localDate = today,
                        targets = result.targets,
                        effectiveLossRateKgPerWeek = result.effectiveLossRateKgPerWeek,
                    ),
                )

            is DailyTargetResult.Unavailable -> dailyTargetRepository.delete(today)
        }
        return result
    }

    suspend fun todaySnapshotExists(): Boolean = dailyTargetRepository.find(timeProvider.today()) != null
}
