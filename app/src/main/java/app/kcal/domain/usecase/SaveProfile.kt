package app.kcal.domain.usecase

import app.kcal.domain.model.StoredProfile
import app.kcal.domain.repository.ProfileRepository

/**
 * Persists the calculator inputs and keeps today's target consistent with them.
 *
 * The write spans DataStore and Room, so it cannot be one transaction. The target is
 * calculated first, the user's input is stored next, and today's snapshot is written or
 * removed last. If the last step fails or the process dies in between, the profile is still
 * saved and [ReconcileTodayTarget] repairs the missing snapshot on the next start.
 */
class SaveProfile(private val profileRepository: ProfileRepository, private val applyTodayTarget: ApplyTodayTarget) {
    suspend operator fun invoke(profile: StoredProfile): DailyTargetResult {
        profileRepository.saveProfile(profile)
        return applyTodayTarget(profile)
    }
}
