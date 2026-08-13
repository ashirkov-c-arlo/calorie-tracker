package app.kcal.llm.remote

import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.Macros
import app.kcal.domain.usecase.MealValidationResult
import app.kcal.domain.usecase.ValidateMeal
import app.kcal.llm.FailureReason
import app.kcal.llm.ParseResult

/**
 * Contract payload to domain. Hard-invalid payloads become [FailureReason.INVALID_RESPONSE];
 * soft sanity bounds stay a reviewable draft and are derived by the confirmation UI, never
 * coerced here.
 */
internal fun ParseResponseDto.toParseResult(validateMeal: ValidateMeal): ParseResult = when (this) {
    is ParseResponseDto.Success -> toSuccess(validateMeal)

    is ParseResponseDto.Clarification ->
        if (question.isBlank() || !usage.isContractValid()) {
            ParseResult.Failure(FailureReason.INVALID_RESPONSE)
        } else {
            ParseResult.NeedsClarification(question)
        }

    is ParseResponseDto.Error -> ParseResult.Failure(failureReasonOfCode(code) ?: FailureReason.UNKNOWN)

    ParseResponseDto.Unknown -> ParseResult.Failure(FailureReason.UNKNOWN)
}

private fun ParseResponseDto.Success.toSuccess(validateMeal: ValidateMeal): ParseResult {
    if (!usage.isContractValid()) return ParseResult.Failure(FailureReason.INVALID_RESPONSE)
    val foodItems = items.map { it.toFoodItem() }
    return when (validateMeal(foodItems)) {
        MealValidationResult.Valid -> ParseResult.Success(items = foodItems, note = note?.takeIf { it.isNotBlank() })
        is MealValidationResult.Invalid -> ParseResult.Failure(FailureReason.INVALID_RESPONSE)
    }
}

/** An explicit `null` for [FoodItemDto.grams] is the contract's "mass unknown". */
private fun FoodItemDto.toFoodItem(): FoodItem = FoodItem(
    name = name,
    grams = grams,
    macros = Macros(kcal = kcal, proteinG = proteinG, fatG = fatG, carbsG = carbsG),
    confidence = confidence,
)

private fun UsageDto?.isContractValid(): Boolean = this == null || (inputTokens >= 0 && outputTokens >= 0)

/** Contract error codes reuse the [FailureReason] names, except the app-local NO_NETWORK. */
internal fun failureReasonOfCode(code: String?): FailureReason? =
    FailureReason.entries.firstOrNull { it.name == code && it != FailureReason.NO_NETWORK }
