package app.kcal.feature.entry

import app.kcal.core.common.DecimalText
import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.Macros
import app.kcal.domain.usecase.needsReview
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import java.util.Locale

/**
 * Editable text state of one meal item plus the pure transformations both entry flows need:
 * manual logging and the confirmation of parsed food. Numbers stay strings until Save so the
 * user always sees exactly what they typed.
 */
enum class MealItemField {
    NAME,
    GRAMS,
    KCAL,
    PROTEIN,
    FAT,
    CARBS,
}

enum class MealItemFieldError {
    REQUIRED,
    INVALID_NUMBER,
    NEGATIVE,
}

data class MealItemErrors(
    val name: MealItemFieldError? = null,
    val grams: MealItemFieldError? = null,
    val kcal: MealItemFieldError? = null,
    val protein: MealItemFieldError? = null,
    val fat: MealItemFieldError? = null,
    val carbs: MealItemFieldError? = null,
) {
    val hasAny: Boolean
        get() = name != null || grams != null || kcal != null || protein != null || fat != null || carbs != null
}

data class MealItemUiState(
    val key: Long,
    val name: String = "",
    val grams: String = "",
    val kcal: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val errors: MealItemErrors = MealItemErrors(),
    val needsReview: Boolean = false,
    /** Reported by the parser, or certain for a manually typed row. Never user-editable. */
    val confidence: Float = 1f,
)

internal fun emptyMealItems(): PersistentList<MealItemUiState> = persistentListOf(MealItemUiState(key = FIRST_ITEM_KEY))

internal const val FIRST_ITEM_KEY: Long = 1

/** Formats stored values for editing without losing a single stored decimal. */
internal fun List<FoodItem>.toItemStates(locale: Locale): PersistentList<MealItemUiState> = mapIndexed { index, item ->
    MealItemUiState(
        key = index.toLong() + FIRST_ITEM_KEY,
        name = item.name,
        grams = item.grams?.let { DecimalText.formatEditable(it, locale) }.orEmpty(),
        kcal = item.macros.kcal.toString(),
        protein = DecimalText.formatEditable(item.macros.proteinG, locale),
        fat = DecimalText.formatEditable(item.macros.fatG, locale),
        carbs = DecimalText.formatEditable(item.macros.carbsG, locale),
        needsReview = item.needsReview(),
        confidence = item.confidence,
    )
}.toPersistentList()

internal fun PersistentList<MealItemUiState>.changingItem(
    key: Long,
    field: MealItemField,
    value: String,
): PersistentList<MealItemUiState> {
    val index = indexOfFirst { it.key == key }
    if (index < 0) return this
    val item = this[index]
    val updated =
        when (field) {
            MealItemField.NAME -> item.copy(name = value)
            MealItemField.GRAMS -> item.copy(grams = value)
            MealItemField.KCAL -> item.copy(kcal = value)
            MealItemField.PROTEIN -> item.copy(protein = value)
            MealItemField.FAT -> item.copy(fat = value)
            MealItemField.CARBS -> item.copy(carbs = value)
        }
    return replacingAt(index, updated.withDerivedState(showErrors = item.errors.hasAny))
}

internal fun PersistentList<MealItemUiState>.removingItem(key: Long): PersistentList<MealItemUiState> {
    if (size == 1) return this
    val index = indexOfFirst { it.key == key }
    return if (index < 0) this else removingAt(index)
}

/** Reveals every field error, which is what an explicit Save has to do. */
internal fun PersistentList<MealItemUiState>.validated(): PersistentList<MealItemUiState> =
    map { it.withDerivedState(showErrors = true) }.toPersistentList()

/** Returns null when any row is still hard-invalid, so nothing partial reaches the domain. */
internal fun List<MealItemUiState>.toFoodItemsOrNull(): List<FoodItem>? {
    val items = mapNotNull { it.toFoodItem() }
    return items.takeIf { it.size == size }
}

internal fun MealItemUiState.withDerivedState(showErrors: Boolean): MealItemUiState = copy(
    errors = if (showErrors) fieldErrors() else MealItemErrors(),
    needsReview = toFoodItem()?.needsReview() == true,
)

private fun MealItemUiState.fieldErrors(): MealItemErrors = MealItemErrors(
    name = MealItemFieldError.REQUIRED.takeIf { name.isBlank() },
    grams = optionalDecimalError(grams),
    kcal = integerError(kcal),
    protein = requiredDecimalError(protein),
    fat = requiredDecimalError(fat),
    carbs = requiredDecimalError(carbs),
)

private fun MealItemUiState.toFoodItem(): FoodItem? {
    if (name.isBlank()) return null
    val parsedGrams = if (grams.isBlank()) null else DecimalText.parse(grams) ?: return null
    val parsedKcal = DecimalText.parseInt(kcal) ?: return null
    val parsedProtein = DecimalText.parse(protein) ?: return null
    val parsedFat = DecimalText.parse(fat) ?: return null
    val parsedCarbs = DecimalText.parse(carbs) ?: return null
    if (
        parsedGrams?.let { it < 0.0 } == true ||
        parsedKcal < 0 ||
        parsedProtein < 0.0 ||
        parsedFat < 0.0 ||
        parsedCarbs < 0.0
    ) {
        return null
    }
    return FoodItem(
        name = name.trim(),
        grams = parsedGrams,
        macros = Macros(kcal = parsedKcal, proteinG = parsedProtein, fatG = parsedFat, carbsG = parsedCarbs),
        confidence = confidence,
    )
}

private fun requiredDecimalError(text: String): MealItemFieldError? {
    if (text.isBlank()) return MealItemFieldError.REQUIRED
    val number = DecimalText.parse(text) ?: return MealItemFieldError.INVALID_NUMBER
    return MealItemFieldError.NEGATIVE.takeIf { number < 0.0 }
}

private fun optionalDecimalError(text: String): MealItemFieldError? =
    if (text.isBlank()) null else requiredDecimalError(text)

private fun integerError(text: String): MealItemFieldError? {
    if (text.isBlank()) return MealItemFieldError.REQUIRED
    val number = DecimalText.parseInt(text) ?: return MealItemFieldError.INVALID_NUMBER
    return MealItemFieldError.NEGATIVE.takeIf { number < 0 }
}
