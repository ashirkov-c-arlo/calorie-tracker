package app.kcal.domain.usecase

import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.LossPace
import app.kcal.testing.completeProfile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestLossPacesTest {

    private val calculate = CalculateDailyTargets()
    private val suggest = SuggestLossPaces(calculate)

    @Test
    fun `each position shows exactly the weekly loss its target will produce`() {
        val profile = completeProfile(currentWeightKg = 82.4, targetWeightKg = 72.0)
        val options = assertNotNull(suggest(profile))

        LossPace.entries.forEach { pace ->
            val target = calculate.forStoredProfile(profile.copy(lossPace = pace))
            assertTrue(target is DailyTargetResult.Available, "expected a target for $pace")
            assertEquals(target.effectiveLossRateKgPerWeek, options.rateFor(pace), "shown rate for $pace")
        }
    }

    @Test
    fun `the estimates stay ordered and distinct while no cap applies`() {
        // 82.4 kg at 176 cm is overweight: 15%, 17.5% and 20% of 2418.6 kcal stay below 600.
        val options = assertNotNull(suggest(completeProfile(currentWeightKg = 82.4, targetWeightKg = 72.0)))
        val slow = assertNotNull(options.slowKgPerWeek)
        val moderate = assertNotNull(options.moderateKgPerWeek)
        val fast = assertNotNull(options.fastKgPerWeek)

        assertTrue(slow > 0.0)
        assertTrue(slow < moderate)
        assertTrue(moderate < fast)
    }

    @Test
    fun `hard habitual activity changes the offered estimates`() {
        val obese = completeProfile(currentWeightKg = 100.0, heightCm = 175.0, targetWeightKg = 80.0)

        val moderate = assertNotNull(suggest(obese.copy(activityLevel = ActivityLevel.MODERATE)))
        val high = assertNotNull(suggest(obese.copy(activityLevel = ActivityLevel.HIGH)))

        // The override lowers the share from 25% to 20%, even though energy expenditure is higher.
        assertTrue(assertNotNull(high.fastKgPerWeek) < assertNotNull(moderate.fastKgPerWeek))
    }

    @Test
    fun `the positions are offered without an estimate while the energy inputs are missing`() {
        val options = assertNotNull(suggest(completeProfile(activityLevel = null)))

        LossPace.entries.forEach { pace -> assertNull(options.rateFor(pace), "no fabricated rate for $pace") }
    }

    @Test
    fun `no position is offered below the reference body mass index`() {
        // 50 kg at 175 cm is a body mass index of 16.3, so the goal is maintenance.
        assertNull(suggest(completeProfile(currentWeightKg = 50.0, heightCm = 175.0)))
    }

    @Test
    fun `no position is offered without usable measurements`() {
        assertNull(suggest(completeProfile(currentWeightKg = null)))
        assertNull(suggest(completeProfile(currentWeightKg = 0.0)))
        assertNull(suggest(completeProfile(currentWeightKg = Double.NaN)))
        assertNull(suggest(completeProfile(heightCm = null)))
    }

    @Test
    fun `the estimate is zero once the target weight is reached`() {
        val options = assertNotNull(suggest(completeProfile(currentWeightKg = 70.0, targetWeightKg = 78.0)))

        LossPace.entries.forEach { pace -> assertEquals(0.0, assertNotNull(options.rateFor(pace)), 1e-9) }
    }

    @Test
    fun `slower positions leave more calories in the target`() {
        val profile = completeProfile(targetWeightKg = 72.0)
        val targets =
            LossPace.entries.map { pace ->
                val result = calculate.forStoredProfile(profile.copy(lossPace = pace))
                assertTrue(result is DailyTargetResult.Available, "expected a target for $pace")
                result.targets.kcal
            }

        assertEquals(targets.sortedDescending(), targets)
    }
}
