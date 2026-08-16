package app.kcal.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredProfileTest {

    @Test
    fun `a complete profile maps to validated inputs`() {
        val profile = complete()

        assertTrue(profile.isComplete)
        val inputs = assertNotNull(profile.toInputs())
        assertEquals(profile.currentWeightKg, inputs.currentWeightKg)
        assertEquals(profile.energyEquationSex, inputs.energyEquationSex)
    }

    @Test
    fun `every missing required input keeps the profile incomplete`() {
        listOf(
            "current weight" to complete().copy(currentWeightKg = null),
            "height" to complete().copy(heightCm = null),
            "age" to complete().copy(ageYears = null),
            "formula variant" to complete().copy(energyEquationSex = null),
            "activity level" to complete().copy(activityLevel = null),
            "target weight" to complete().copy(targetWeightKg = null),
            "loss pace" to complete().copy(lossPace = null),
        ).forEach { (name, profile) ->
            assertFalse(profile.isComplete, "missing $name must stay incomplete")
            assertNull(profile.toInputs(), "missing $name must not produce inputs")
        }
    }

    @Test
    fun `an empty profile has no fabricated defaults`() {
        val empty = StoredProfile()

        assertFalse(empty.isComplete)
        assertNull(empty.energyEquationSex)
        assertNull(empty.activityLevel)
        assertNull(empty.lossPace)
    }

    @Test
    fun `below the reference body mass index a profile is complete without a pace`() {
        // 50 kg at 175 cm gives a body mass index of 16.3, so there is no deficit to choose.
        val underweight = complete().copy(currentWeightKg = 50.0, heightCm = 175.0, lossPace = null)

        assertTrue(underweight.isComplete)
        assertNull(assertNotNull(underweight.toInputs()).lossPace)
    }

    @Test
    fun `user preferences default to metric, system language and system theme`() {
        val defaults = UserPreferences()

        assertEquals(UnitSystem.METRIC, defaults.unitSystem)
        assertEquals(AppLanguage.SYSTEM, defaults.appLanguage)
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertFalse(defaults.profile.isComplete)
    }

    private fun complete() = StoredProfile(
        currentWeightKg = 82.4,
        heightCm = 176.0,
        ageYears = 34,
        energyEquationSex = EnergyEquationSex.MALE,
        activityLevel = ActivityLevel.LIGHT,
        targetWeightKg = 78.0,
        lossPace = LossPace.MODERATE,
    )
}
