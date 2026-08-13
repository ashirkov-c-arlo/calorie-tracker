package app.kcal.domain.usecase

import app.kcal.domain.model.WeightEntry
import app.kcal.domain.repository.ProfileRepository

/**
 * The only write path for a single weight entry. Values that would break the persisted-data
 * invariants are rejected here, before the repository, so no caller can store a non-finite or
 * non-positive weight. Returns false when nothing was written.
 */
class LogWeight(private val profileRepository: ProfileRepository) {

    suspend operator fun invoke(entry: WeightEntry): Boolean {
        if (!entry.kg.isFinite() || entry.kg <= 0.0) return false
        profileRepository.logWeight(entry)
        return true
    }
}
