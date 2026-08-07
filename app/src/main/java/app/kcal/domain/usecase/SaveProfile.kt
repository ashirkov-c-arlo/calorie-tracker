package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.repository.ProfileRepository

/**
 * Persists the calculator inputs and keeps today's target consistent with them.
 *
 * Values that would break the persisted-data invariants are rejected before anything is
 * written. The write then spans Room and DataStore, so it cannot be one transaction: the
 * weight entry, the calculator inputs and today's snapshot are stored in that order, all for
 * the same local date. If a later step fails or the process dies in between, the app shell
 * rewrites today's snapshot from the stored profile on the next start.
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
