package app.kcal.llm.fake

import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.interfaceLocale
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
 * they are deliberately not resources; like the proxy, it answers in the interface language
 * whatever the input language is. Text ending in `?` returns a clarification once, which makes
 * the full entry flow verifiable before the proxy exists.
 */
class FakeNutritionParser @Inject constructor(private val localeProvider: AppLocaleProvider) : NutritionParser {

    override suspend fun parse(input: UserInput): ParseResult {
        val text = input.text.trim()
        if (text.isEmpty()) return ParseResult.Failure(FailureReason.INVALID_REQUEST)
        val russian = interfaceLocale(localeProvider.current()).language == RUSSIAN_LANGUAGE
        if (input.clarification == null && text.endsWith("?")) {
            return ParseResult.NeedsClarification(if (russian) QUESTION_RU else QUESTION_EN)
        }
        return ParseResult.Success(
            items =
            listOf(
                FoodItem(
                    name = if (russian) ITEM_NAME_RU else ITEM_NAME_EN,
                    grams = 150.0,
                    macros = Macros(kcal = 250, proteinG = 12.0, fatG = 8.0, carbsG = 32.0),
                    confidence = 0.5f,
                ),
            ),
            note = if (russian) NOTE_RU else NOTE_EN,
            summary = if (russian) SUMMARY_RU else SUMMARY_EN,
        )
    }

    private companion object {
        const val RUSSIAN_LANGUAGE = "ru"
        const val QUESTION_EN = "Approximately how large was the serving?"
        const val QUESTION_RU = "Какого примерно размера была порция?"
        const val ITEM_NAME_EN = "Sample dish"
        const val ITEM_NAME_RU = "Пробное блюдо"
        const val NOTE_EN = "Sample values from the offline parser stub."
        const val NOTE_RU = "Значения-заглушки офлайн-парсера."
        const val SUMMARY_EN = "sample dish with stub values"
        const val SUMMARY_RU = "пробное блюдо со значениями-заглушками"
    }
}
