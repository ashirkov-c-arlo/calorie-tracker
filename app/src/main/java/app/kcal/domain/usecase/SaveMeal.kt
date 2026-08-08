package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.EntrySource
import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.MealEntry
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.first

sealed interface SaveMealResult {
    data class Saved(val mealId: Long) : SaveMealResult

    data class Invalid(val errors: Set<MealValidationError>) : SaveMealResult

    data object NotFound : SaveMealResult
}

/** Creates or edits a meal only after hard validation and explicit user Save. */
class SaveMeal(
    private val mealRepository: MealRepository,
    private val profileRepository: ProfileRepository,
    private val calculateDailyTargets: CalculateDailyTargets,
    private val validateMeal: ValidateMeal,
    private val timeProvider: TimeProvider,
) {
    /**
     * [source] and [rawUserInput] describe how a *new* meal was produced. Editing keeps the
     * origin of the stored meal, because the user only changed its numbers.
     */
    suspend operator fun invoke(
        mealId: Long?,
        items: List<FoodItem>,
        source: EntrySource = EntrySource.MANUAL,
        rawUserInput: String? = null,
    ): SaveMealResult {
        val normalizedItems = items.map { it.copy(name = it.name.trim()) }
        val validation = validateMeal(normalizedItems)
        if (validation is MealValidationResult.Invalid) return SaveMealResult.Invalid(validation.errors)

        val instant = timeProvider.now()
        val today = timeProvider.localDateAt(instant)
        val existing = mealId?.let { mealRepository.findById(it) }
        if (mealId != null && existing == null) return SaveMealResult.NotFound

        val meal = existing?.copy(items = normalizedItems) ?: MealEntry(
            id = 0,
            localDate = today,
            at = instant,
            items = normalizedItems,
            rawUserInput = rawUserInput?.takeIf { it.isNotBlank() },
            source = source,
        )
        val targetIfMissing = if (meal.localDate == today) currentTarget(today) else null
        return SaveMealResult.Saved(mealRepository.save(meal, targetIfMissing))
    }

    private suspend fun currentTarget(localDate: java.time.LocalDate): DailyTargetSnapshot? =
        when (val result = calculateDailyTargets.forStoredProfile(profileRepository.preferences.first().profile)) {
            is DailyTargetResult.Available ->
                DailyTargetSnapshot(
                    localDate = localDate,
                    targets = result.targets,
                    effectiveLossRateKgPerWeek = result.effectiveLossRateKgPerWeek,
                )

            is DailyTargetResult.Unavailable -> null
        }
}
