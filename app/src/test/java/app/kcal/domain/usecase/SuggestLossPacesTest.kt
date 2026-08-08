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
    private val suggest = SuggestLossPaces()

    @Test
    fun `the paces are weekly shares of body weight`() {
        val options = assertNotNull(suggest(completeProfile(currentWeightKg = 82.4)))

        assertEquals(0.21, options.slowKgPerWeek, 0.005)
        assertEquals(0.41, options.moderateKgPerWeek, 0.005)
        assertEquals(0.62, options.fastKgPerWeek, 0.005)
    }

    @Test
    fun `the paces stay ordered and distinct down to the lowest accepted weight`() {
        listOf(20.0, 25.0, 40.0, 82.4, 150.0, 400.0).forEach { weightKg ->
            val options = assertNotNull(suggest(completeProfile(currentWeightKg = weightKg)))

            assertTrue(options.slowKgPerWeek > 0.0, "slow must be positive at $weightKg kg")
            assertTrue(options.slowKgPerWeek < options.moderateKgPerWeek, "ordering at $weightKg kg")
            assertTrue(options.moderateKgPerWeek < options.fastKgPerWeek, "ordering at $weightKg kg")
        }
    }

    @Test
    fun `the fastest pace never exceeds the one percent of body weight guardrail`() {
        listOf(20.0, 82.4, 400.0).forEach { weightKg ->
            val options = assertNotNull(suggest(completeProfile(currentWeightKg = weightKg)))

            assertTrue(options.fastKgPerWeek <= weightKg * 0.01 + 0.005, "guardrail at $weightKg kg")
        }
    }

    @Test
    fun `the paces are offered even while the target itself is unavailable`() {
        // Below the minimum age there is no target, but the user still states an intent.
        assertNotNull(suggest(completeProfile(ageYears = 15)))
        // The same holds once the target weight is reached.
        assertNotNull(suggest(completeProfile(currentWeightKg = 70.0, targetWeightKg = 78.0)))
        // And while other inputs are still missing.
        assertNotNull(suggest(completeProfile(activityLevel = null)))
    }

    @Test
    fun `no pace is offered without a usable current weight`() {
        assertNull(suggest(completeProfile(currentWeightKg = null)))
        assertNull(suggest(completeProfile(currentWeightKg = 0.0)))
        assertNull(suggest(completeProfile(currentWeightKg = Double.NaN)))
    }

    @Test
    fun `slower paces change the calorie target and a capped one is explained`() {
        val profile = completeProfile(targetWeightKg = 72.0)
        val options = assertNotNull(suggest(profile))

        val results =
            LossPace.entries.associateWith { pace ->
                calculate.forStoredProfile(profile.copy(requestedLossRateKgPerWeek = options.rateFor(pace)))
            }

        results.forEach { (pace, result) ->
            assertTrue(result is DailyTargetResult.Available, "expected a target for $pace")
        }
        val slow = results.getValue(LossPace.SLOW) as DailyTargetResult.Available
        val moderate = results.getValue(LossPace.MODERATE) as DailyTargetResult.Available
        val fast = results.getValue(LossPace.FAST) as DailyTargetResult.Available

        assertTrue(slow.targets.kcal > moderate.targets.kcal)
        assertTrue(moderate.targets.kcal >= fast.targets.kcal)
        // The requested intent is preserved even where a guardrail lowers the effective pace.
        assertEquals(options.fastKgPerWeek, fast.requestedLossRateKgPerWeek)
        if (fast.effectiveLossRateKgPerWeek < fast.requestedLossRateKgPerWeek) {
            assertTrue(fast.warnings.isNotEmpty(), "a capped pace must be explained")
        }
    }

    @Test
    fun `a stored rate only matches a pace that produces it exactly`() {
        val options = assertNotNull(suggest(completeProfile(currentWeightKg = 82.4)))

        assertEquals(LossPace.SLOW, options.paceFor(options.slowKgPerWeek))
        assertEquals(LossPace.MODERATE, options.paceFor(options.moderateKgPerWeek))
        assertEquals(LossPace.FAST, options.paceFor(options.fastKgPerWeek))
        // A hand-entered rate keeps its value instead of being mapped to a nearby option.
        assertNull(options.paceFor(0.5))
        assertNull(options.paceFor(0.0))
    }
}
