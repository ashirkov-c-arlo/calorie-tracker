package app.kcal.llm.fake

import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.Macros
import app.kcal.llm.FailureReason
import app.kcal.llm.NutritionParser
import app.kcal.llm.ParseResult
import app.kcal.llm.UserInput
import javax.inject.Inject

/**
 * Deterministic stand-in for the proxy, used by tests and by debug builds that have no
 * `LLM_API_BASE_URL`. Its strings stand in for server payloads, not for interface text, so
 * they are deliberately not resources. Text ending in `?` returns a clarification once, which
 * makes the full entry flow verifiable before the proxy exists.
 */
class FakeNutritionParser @Inject constructor() : NutritionParser {

    override suspend fun parse(input: UserInput): ParseResult {
        val text = input.text.trim()
        if (text.isEmpty()) return ParseResult.Failure(FailureReason.INVALID_REQUEST)
        if (input.clarification == null && text.endsWith("?")) {
            return ParseResult.NeedsClarification(CLARIFICATION_QUESTION)
        }
        return ParseResult.Success(
            items =
            listOf(
                FoodItem(
                    name = text.take(MAX_NAME_LENGTH),
                    grams = 150.0,
                    macros = Macros(kcal = 250, proteinG = 12.0, fatG = 8.0, carbsG = 32.0),
                    confidence = 0.5f,
                ),
            ),
            note = NOTE,
        )
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
        const val CLARIFICATION_QUESTION = "Approximately how large was the serving?"
        const val NOTE = "Sample values from the offline parser stub."
    }
}
