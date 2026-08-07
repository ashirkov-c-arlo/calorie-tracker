package app.kcal.domain.usecase

import app.kcal.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.first

/**
 * Repairs a partially completed profile save. A crash or a failed Room write between the
 * two stores can leave a complete profile without today's target; this recreates it. When
 * the profile yields no target, a stale snapshot is removed instead.
 */
class ReconcileTodayTarget(
    private val profileRepository: ProfileRepository,
    private val applyTodayTarget: ApplyTodayTarget,
) {
    suspend operator fun invoke() {
        val profile = profileRepository.preferences.first().profile
        if (!profile.isComplete) return
        if (applyTodayTarget.todaySnapshotExists()) return
        applyTodayTarget(profile)
    }
}
