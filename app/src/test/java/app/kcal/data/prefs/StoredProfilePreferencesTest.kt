package app.kcal.data.prefs

import app.kcal.domain.model.ThemeMode
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredProfilePreferencesTest {

    @Test
    fun `all required inputs present`() {
        assertTrue(complete().hasAllRequiredCalculatorInputs)
    }

    @Test
    fun `each missing required input keeps the profile incomplete`() {
        val incompleteVariants =
            listOf(
                "height" to complete().copy(heightCm = null),
                "age" to complete().copy(ageYears = null),
                "formulaVariant" to complete().copy(formulaVariant = null),
                "blank formulaVariant" to complete().copy(formulaVariant = "  "),
                "activityLevel" to complete().copy(activityLevel = null),
                "blank activityLevel" to complete().copy(activityLevel = ""),
                "targetWeight" to complete().copy(targetWeightKg = null),
                "lossRate" to complete().copy(requestedLossRateKgPerWeek = null),
            )

        incompleteVariants.forEach { (name, preferences) ->
            assertFalse(preferences.hasAllRequiredCalculatorInputs, "missing $name must be incomplete")
        }
    }

    @Test
    fun `theme mode defaults to system`() {
        val empty =
            StoredProfilePreferences(
                heightCm = null,
                ageYears = null,
                formulaVariant = null,
                activityLevel = null,
                targetWeightKg = null,
                requestedLossRateKgPerWeek = null,
                themeMode = ThemeMode.SYSTEM,
            )
        assertFalse(empty.hasAllRequiredCalculatorInputs)
    }

    private fun complete() = StoredProfilePreferences(
        heightCm = 176.0,
        ageYears = 34,
        formulaVariant = "MALE",
        activityLevel = "LIGHT",
        targetWeightKg = 78.0,
        requestedLossRateKgPerWeek = 0.5,
        themeMode = ThemeMode.SYSTEM,
    )
}
