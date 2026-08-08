package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.repository.ProfileRepository

/**
 * Persists the calculator inputs and reports the resulting estimate.
 *
 * Values that would break the persisted-data invariants are rejected before anything is
 * written. The weight entry is written before the atomic preferences edit, both for the same
 * local date, so an interruption can only lose the edit itself and never pair new settings
 * with a stale weight.
 *
 * Today's target snapshot is deliberately **not** written here: the app shell owns that
 * write and performs it for every stored profile, which keeps a single writer and makes a
 * late save unable to overwrite a newer target.
 */
class SaveProfile(
    private val profileRepository: ProfileRepository,
    private val calculateDailyTargets: CalculateDailyTargets,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(profile: StoredProfile): DailyTargetResult {
        if (!profile.hasValidValues) {
            return DailyTargetResult.Unavailable(DailyTargetUnavailableReason.INVALID_MEASUREMENTS)
        }
        profileRepository.saveProfile(profile, timeProvider.today())
        return calculateDailyTargets.forStoredProfile(profile)
    }
}
