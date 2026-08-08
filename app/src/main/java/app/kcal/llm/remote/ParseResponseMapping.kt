package app.kcal.llm.remote

import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.Macros
import app.kcal.domain.usecase.MealValidationResult
import app.kcal.domain.usecase.ValidateMeal
import app.kcal.llm.FailureReason
import app.kcal.llm.ParseResult

private const val TYPE_SUCCESS = "success"
private const val TYPE_CLARIFICATION = "clarification"
private const val TYPE_ERROR = "error"

/**
 * Contract payload to domain. Hard-invalid payloads become [FailureReason.INVALID_RESPONSE];
 * soft sanity bounds stay a reviewable draft and are derived by the confirmation UI, never
 * coerced here.
 */
internal fun ParseResponseDto.toParseResult(validateMeal: ValidateMeal): ParseResult = when (type) {
    TYPE_SUCCESS -> toSuccess(validateMeal)

    TYPE_CLARIFICATION ->
        question
            ?.takeIf { it.isNotBlank() }
            ?.let { ParseResult.NeedsClarification(it) }
            ?: ParseResult.Failure(FailureReason.INVALID_RESPONSE)

    TYPE_ERROR -> ParseResult.Failure(failureReasonOfCode(code) ?: FailureReason.UNKNOWN)

    else -> ParseResult.Failure(FailureReason.UNKNOWN)
}

private fun ParseResponseDto.toSuccess(validateMeal: ValidateMeal): ParseResult {
    val dtoItems = items.orEmpty()
    val foodItems = dtoItems.mapNotNull { it.toFoodItemOrNull() }
    if (foodItems.size != dtoItems.size) return ParseResult.Failure(FailureReason.INVALID_RESPONSE)
    return when (validateMeal(foodItems)) {
        MealValidationResult.Valid -> ParseResult.Success(items = foodItems, note = note?.takeIf { it.isNotBlank() })
        is MealValidationResult.Invalid -> ParseResult.Failure(FailureReason.INVALID_RESPONSE)
    }
}

/** A missing required field is invalid. A missing `grams` only means the mass is unknown. */
private fun FoodItemDto.toFoodItemOrNull(): FoodItem? = FoodItem(
    name = name ?: return null,
    grams = grams,
    macros =
    Macros(
        kcal = kcal ?: return null,
        proteinG = proteinG ?: return null,
        fatG = fatG ?: return null,
        carbsG = carbsG ?: return null,
    ),
    confidence = confidence ?: return null,
)

/** Contract error codes reuse the [FailureReason] names, except the app-local NO_NETWORK. */
internal fun failureReasonOfCode(code: String?): FailureReason? =
    FailureReason.entries.firstOrNull { it.name == code && it != FailureReason.NO_NETWORK }

/** Fallback for a gateway response without a contract body. */
internal fun failureReasonOfStatus(status: Int): FailureReason = when (status) {
    400 -> FailureReason.INVALID_REQUEST
    401, 403 -> FailureReason.AUTH
    413 -> FailureReason.PAYLOAD_TOO_LARGE
    422 -> FailureReason.CONTENT_BLOCKED
    429 -> FailureReason.THROTTLED
    502 -> FailureReason.INVALID_RESPONSE
    504 -> FailureReason.TIMEOUT
    else -> FailureReason.UNKNOWN
}
