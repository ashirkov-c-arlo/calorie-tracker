package app.kcal.llm

import app.kcal.domain.model.FoodItem

/**
 * Transport-agnostic nutrition parsing contract. `domain` and `feature` depend on this file
 * only, so swapping the model, region, or transport never reaches another package.
 */
data class ClarificationAnswer(val question: String, val answer: String)

sealed interface UserInput {
    /** Always non-blank: photo-only parsing is not supported. */
    val text: String
    val clarification: ClarificationAnswer?

    data class Text(override val text: String, override val clarification: ClarificationAnswer? = null) : UserInput

    data class TextWithPhoto(
        override val text: String,
        val temporaryPhotoPath: String,
        override val clarification: ClarificationAnswer? = null,
    ) : UserInput
}

sealed interface ParseResult {
    /**
     * Items still need explicit user confirmation before anything is persisted. [summary] is the
     * one-line meal name the journal shows; it is display text only and may be absent.
     */
    data class Success(val items: List<FoodItem>, val note: String?, val summary: String? = null) : ParseResult

    data class NeedsClarification(val question: String) : ParseResult

    data class Failure(val reason: FailureReason, val cause: Throwable? = null) : ParseResult
}

enum class FailureReason {
    NO_NETWORK,
    TIMEOUT,
    THROTTLED,
    INVALID_REQUEST,
    PAYLOAD_TOO_LARGE,
    INVALID_RESPONSE,
    CONTENT_BLOCKED,
    AUTH,
    QUOTA,
    UNKNOWN,
}

interface NutritionParser {
    suspend fun parse(input: UserInput): ParseResult
}
