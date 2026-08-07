package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.repository.ProfileRepository

/**
 * Persists the calculator inputs and keeps today's target consistent with them.
 *
 * Values that would break the persisted-data invariants are rejected before anything is
 * written. The write then spans DataStore and Room, so it cannot be one transaction: the
 * user's input is stored first and today's snapshot is written or removed second, both for
 * the same local date. If the second step fails or the process dies in between,
 * [ReconcileTodayTarget] rewrites the snapshot on the next start.
 */
class SaveProfile(
    private val profileRepository: ProfileRepository,
    private val applyTodayTarget: ApplyTodayTarget,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(profile: StoredProfile): DailyTargetResult {
        if (!profile.hasValidValues) {
            return DailyTargetResult.Unavailable(DailyTargetUnavailableReason.INVALID_MEASUREMENTS)
        }
        val localDate = timeProvider.today()
        profileRepository.saveProfile(profile, localDate)
        return applyTodayTarget(profile, localDate)
    }
}
