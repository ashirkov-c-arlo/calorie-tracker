package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.first

/**
 * Rewrites today's target from the stored profile on every start. A crash or a failed Room
 * write between the two stores can leave today's snapshot missing, stale from a previous
 * profile, or present when the profile no longer yields a target. Recomputing and upserting
 * one row is cheap and repairs all three cases; past snapshots are never touched.
 */
class ReconcileTodayTarget(
    private val profileRepository: ProfileRepository,
    private val applyTodayTarget: ApplyTodayTarget,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke() {
        val profile = profileRepository.preferences.first().profile
        applyTodayTarget(profile, timeProvider.today())
    }
}
