package app.kcal.domain.usecase

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
    fun `the three paces are ordered and distinct`() {
        val options = assertNotNull(suggest(completeProfile(targetWeightKg = 72.0)))

        assertTrue(options.slowKgPerWeek > 0.0)
        assertTrue(options.slowKgPerWeek < options.moderateKgPerWeek)
        assertTrue(options.moderateKgPerWeek < options.fastKgPerWeek)
    }

    @Test
    fun `every pace stays inside the guardrails, so none of them is capped`() {
        LossPace.entries.forEach { pace ->
            val profile = completeProfile(targetWeightKg = 72.0)
            val options = assertNotNull(suggest(profile))
            val result = calculate.forStoredProfile(profile.copy(requestedLossRateKgPerWeek = options.rateFor(pace)))

            assertTrue(result is DailyTargetResult.Available, "expected a target for $pace")
            assertEquals(emptySet(), result.warnings, "pace $pace must not trigger a guardrail")
            assertEquals(options.rateFor(pace), result.effectiveLossRateKgPerWeek, 0.01)
        }
    }

    @Test
    fun `each pace produces a different calorie target`() {
        val profile = completeProfile(targetWeightKg = 72.0)
        val options = assertNotNull(suggest(profile))

        val targets =
            LossPace.entries.map { pace ->
                val result = calculate.forStoredProfile(
                    profile.copy(requestedLossRateKgPerWeek = options.rateFor(pace)),
                )
                assertTrue(result is DailyTargetResult.Available)
                result.targets.kcal
            }

        assertEquals(targets.distinct().size, targets.size)
        assertEquals(targets.sortedDescending(), targets)
    }

    @Test
    fun `the fastest pace is the fastest the guardrails allow`() {
        val profile = completeProfile(targetWeightKg = 72.0)
        val options = assertNotNull(suggest(profile))
        val ceiling = calculate.forStoredProfile(profile.copy(requestedLossRateKgPerWeek = 10.0))

        assertTrue(ceiling is DailyTargetResult.Available)
        assertTrue(options.fastKgPerWeek <= ceiling.effectiveLossRateKgPerWeek)
        assertEquals(ceiling.effectiveLossRateKgPerWeek, options.fastKgPerWeek, 0.01)
    }

    @Test
    fun `a lighter person gets slower paces than a heavier one`() {
        val light = assertNotNull(suggest(completeProfile(currentWeightKg = 60.0, targetWeightKg = 55.0)))
        val heavy = assertNotNull(suggest(completeProfile(currentWeightKg = 120.0, targetWeightKg = 90.0)))

        assertTrue(light.fastKgPerWeek < heavy.fastKgPerWeek)
    }

    @Test
    fun `no pace is offered once the target weight is reached`() {
        assertNull(suggest(completeProfile(currentWeightKg = 70.0, targetWeightKg = 78.0)))
        assertNull(suggest(completeProfile(currentWeightKg = 78.0, targetWeightKg = 78.0)))
    }

    @Test
    fun `no pace is offered for an incomplete or out of scope profile`() {
        assertNull(suggest(completeProfile(activityLevel = null)))
        assertNull(suggest(completeProfile(ageYears = 15)))
    }

    @Test
    fun `a stored rate maps back to the closest pace`() {
        val options = assertNotNull(suggest(completeProfile(targetWeightKg = 72.0)))

        assertEquals(LossPace.SLOW, options.paceClosestTo(options.slowKgPerWeek))
        assertEquals(LossPace.MODERATE, options.paceClosestTo(options.moderateKgPerWeek))
        assertEquals(LossPace.FAST, options.paceClosestTo(options.fastKgPerWeek))
        assertEquals(LossPace.FAST, options.paceClosestTo(options.fastKgPerWeek + 5.0))
        assertEquals(LossPace.SLOW, options.paceClosestTo(0.0))
    }
}
