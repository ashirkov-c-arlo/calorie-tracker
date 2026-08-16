package app.kcal.domain.model

import java.time.Instant
import java.time.LocalDate

/** One food item in a meal. Nutrition is always stored as kcal and grams. */
data class FoodItem(val name: String, val grams: Double?, val macros: Macros, val confidence: Float)

enum class EntrySource {
    MANUAL,
    LLM_TEXT,
    LLM_PHOTO,
}

/**
 * A chronological meal on the user's local date. Photos are never part of this model.
 * [summary] is the one-line name the journal shows; it is null for meals logged before it
 * existed and for manual meals whose author left it empty.
 */
data class MealEntry(
    val id: Long,
    val localDate: LocalDate,
    val at: Instant,
    val items: List<FoodItem>,
    val rawUserInput: String?,
    val source: EntrySource,
    val summary: String? = null,
)
