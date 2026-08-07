package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.repository.ProfileRepository
import javax.inject.Inject

/**
 * Persists the calculator inputs and keeps today's target snapshot in sync.
 *
 * The write spans DataStore and Room, so it cannot be one transaction. It is idempotent
 * instead: repeating it stores the same preferences, upserts the same weight entry and
 * replaces only today's snapshot. Past snapshots are never touched.
 */
class SaveProfile @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val dailyTargetRepository: DailyTargetRepository,
    private val calculateDailyTargets: CalculateDailyTargets,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(profile: StoredProfile): DailyTargetResult {
        profileRepository.saveProfile(profile)
        return refreshTodayTarget(profile)
    }

    /**
     * Recalculates today's snapshot from [profile]. Nothing is written while the target is
     * unavailable, so an incomplete or out-of-scope profile never produces a stored goal.
     */
    suspend fun refreshTodayTarget(profile: StoredProfile): DailyTargetResult {
        val result = calculateDailyTargets.forStoredProfile(profile)
        if (result is DailyTargetResult.Available) {
            dailyTargetRepository.upsert(
                DailyTargetSnapshot(
                    localDate = timeProvider.today(),
                    targets = result.targets,
                    effectiveLossRateKgPerWeek = result.effectiveLossRateKgPerWeek,
                ),
            )
        }
        return result
    }
}
