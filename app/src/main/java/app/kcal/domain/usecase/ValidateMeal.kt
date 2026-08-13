package app.kcal.domain.usecase

import app.kcal.domain.model.FoodItem

/** Hard-invalid meal fields that must never reach persistence. */
enum class MealValidationError {
    EMPTY_ITEMS,
    BLANK_NAME,
    INVALID_GRAMS,
    INVALID_MACROS,
    INVALID_CONFIDENCE,
}

sealed interface MealValidationResult {
    data object Valid : MealValidationResult

    data class Invalid(val errors: Set<MealValidationError>) : MealValidationResult
}

class ValidateMeal {

    operator fun invoke(items: List<FoodItem>): MealValidationResult {
        val errors = mutableSetOf<MealValidationError>()
        if (items.isEmpty()) errors += MealValidationError.EMPTY_ITEMS
        items.forEach { item ->
            if (item.name.isBlank()) errors += MealValidationError.BLANK_NAME
            if (item.grams?.let { !it.isFinite() || it < 0.0 } == true) {
                errors += MealValidationError.INVALID_GRAMS
            }
            if (
                item.macros.kcal < 0 ||
                listOf(item.macros.proteinG, item.macros.fatG, item.macros.carbsG)
                    .any { !it.isFinite() || it < 0.0 }
            ) {
                errors += MealValidationError.INVALID_MACROS
            }
            if (!item.confidence.isFinite() || item.confidence !in 0f..1f) {
                errors += MealValidationError.INVALID_CONFIDENCE
            }
        }
        return if (errors.isEmpty()) MealValidationResult.Valid else MealValidationResult.Invalid(errors)
    }
}

/** Soft sanity bounds keep the item editable and mark it for review instead of coercing it. */
fun FoodItem.needsReview(): Boolean = macros.kcal !in MIN_ITEM_KCAL..MAX_ITEM_KCAL ||
    grams?.let { it !in MIN_ITEM_GRAMS..MAX_ITEM_GRAMS } == true

private const val MIN_ITEM_KCAL = 0
private const val MAX_ITEM_KCAL = 5000
private const val MIN_ITEM_GRAMS = 0.0
private const val MAX_ITEM_GRAMS = 5000.0
