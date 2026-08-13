package app.kcal.domain.usecase

import app.kcal.testing.foodItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidateMealTest {

    private val validate = ValidateMeal()

    @Test
    fun `validates every hard persisted invariant`() {
        val cases =
            listOf(
                MealValidationError.EMPTY_ITEMS to emptyList(),
                MealValidationError.BLANK_NAME to listOf(foodItem(name = "  ")),
                MealValidationError.INVALID_GRAMS to listOf(foodItem(grams = Double.NaN)),
                MealValidationError.INVALID_GRAMS to listOf(foodItem(grams = -1.0)),
                MealValidationError.INVALID_MACROS to listOf(foodItem(kcal = -1)),
                MealValidationError.INVALID_MACROS to listOf(foodItem(proteinG = Double.POSITIVE_INFINITY)),
                MealValidationError.INVALID_MACROS to listOf(foodItem(fatG = -1.0)),
                MealValidationError.INVALID_CONFIDENCE to listOf(foodItem(confidence = Float.NaN)),
                MealValidationError.INVALID_CONFIDENCE to listOf(foodItem(confidence = 1.1f)),
            )

        cases.forEach { (expected, items) ->
            val result = assertIs<MealValidationResult.Invalid>(validate(items))
            assertTrue(expected in result.errors)
        }
        assertEquals(MealValidationResult.Valid, validate(listOf(foodItem(grams = null))))
    }

    @Test
    fun `soft sanity bounds mark review without making the item invalid`() {
        val boundary = foodItem(grams = 5000.0, kcal = 5000)
        val highKcal = foodItem(kcal = 5001)
        val highGrams = foodItem(grams = 5000.1)

        assertFalse(boundary.needsReview())
        assertTrue(highKcal.needsReview())
        assertTrue(highGrams.needsReview())
        assertEquals(MealValidationResult.Valid, validate(listOf(highKcal, highGrams)))
    }
}
