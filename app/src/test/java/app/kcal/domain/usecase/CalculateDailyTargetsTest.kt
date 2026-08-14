package app.kcal.domain.usecase

import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.ProfileInputs
import app.kcal.domain.model.StoredProfile
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculateDailyTargetsTest {

    private val calculate = CalculateDailyTargets()

    @Test
    fun `male branch matches the documented arithmetic`() {
        // RMR = 10*82.4 + 6.25*176 - 5*34 + 5 = 1759; TDEE = 1759 * 1.375 = 2418.625
        // requested deficit 330 is below both caps, so the pace is honoured exactly.
        val result = available(inputs(rate = 0.3))

        assertEquals(2089, result.targets.kcal)
        assertEquals(93.6, result.targets.proteinG)
        assertEquals(58.0, result.targets.fatG)
        assertEquals(298.2, result.targets.carbsG)
        assertEquals(0.3, result.effectiveLossRateKgPerWeek, TOLERANCE)
        assertEquals(emptySet(), result.warnings)
    }

    @Test
    fun `female branch matches the documented arithmetic`() {
        // RMR = 10*70 + 6.25*165 - 5*30 - 161 = 1420.25; TDEE = 1704.3
        val result =
            available(
                ProfileInputs(
                    currentWeightKg = 70.0,
                    heightCm = 165.0,
                    ageYears = 30,
                    energyEquationSex = EnergyEquationSex.FEMALE,
                    activityLevel = ActivityLevel.SEDENTARY,
                    targetWeightKg = 65.0,
                    requestedLossRateKgPerWeek = 0.5,
                ),
            )

        assertEquals(1363, result.targets.kcal)
        assertEquals(78.0, result.targets.proteinG)
        assertEquals(37.9, result.targets.fatG)
        assertEquals(0.3098, result.effectiveLossRateKgPerWeek, 0.001)
        assertTrue(DailyTargetWarning.DEFICIT_CAPPED in result.warnings)
    }

    @Test
    fun `the two branches never share constants`() {
        val female = available(inputs(sex = EnergyEquationSex.FEMALE, rate = 0.0))
        val male = available(inputs(sex = EnergyEquationSex.MALE, rate = 0.0))

        // 166 kcal of resting metabolic rate difference, scaled by the activity multiplier.
        assertEquals(166.0 * ActivityLevel.LIGHT.pal, (male.targets.kcal - female.targets.kcal).toDouble(), 1.0)
    }

    @Test
    fun `every activity level raises the target monotonically`() {
        val targets =
            ActivityLevel.entries.map { level ->
                available(inputs(activityLevel = level, rate = 0.0)).targets.kcal
            }

        assertEquals(targets.sorted(), targets)
        assertEquals(ActivityLevel.entries.size, targets.distinct().size)
    }

    @Test
    fun `higher activity moves the extra energy to carbohydrates and keeps protein weight based`() {
        val sedentary = available(inputs(activityLevel = ActivityLevel.SEDENTARY, rate = 0.0))
        val high = available(inputs(activityLevel = ActivityLevel.HIGH, rate = 0.0))

        assertEquals(sedentary.targets.proteinG, high.targets.proteinG)
        assertTrue(high.targets.carbsG > sedentary.targets.carbsG)
    }

    @Test
    fun `age below eighteen has no target`() {
        assertEquals(
            DailyTargetResult.Unavailable(DailyTargetUnavailableReason.AGE_BELOW_MINIMUM),
            calculate(inputs(age = 17)),
        )
    }

    @Test
    fun `missing inputs have no target and no fabricated defaults`() {
        assertEquals(
            DailyTargetResult.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
            calculate.forStoredProfile(StoredProfile()),
        )
        assertEquals(
            DailyTargetResult.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
            calculate.forStoredProfile(
                StoredProfile(
                    currentWeightKg = 82.4,
                    heightCm = 176.0,
                    ageYears = 34,
                    energyEquationSex = EnergyEquationSex.MALE,
                    activityLevel = null,
                    targetWeightKg = 78.0,
                    requestedLossRateKgPerWeek = 0.5,
                ),
            ),
        )
    }

    @Test
    fun `non-finite and non-positive measurements have no target`() {
        listOf(
            inputs(weight = Double.NaN),
            inputs(weight = 0.0),
            inputs(height = Double.POSITIVE_INFINITY),
            inputs(height = -1.0),
            inputs(targetWeight = 0.0),
            inputs(rate = -0.5),
            inputs(age = 0),
        ).forEach { invalid ->
            assertEquals(
                DailyTargetResult.Unavailable(DailyTargetUnavailableReason.INVALID_MEASUREMENTS),
                calculate(invalid),
                "expected unavailable for $invalid",
            )
        }
    }

    @Test
    fun `reaching the target weight switches to maintenance`() {
        val result = available(inputs(weight = 78.0, targetWeight = 78.0, rate = 0.5))

        assertEquals(0.0, result.effectiveLossRateKgPerWeek, TOLERANCE)
        assertTrue(DailyTargetWarning.TARGET_WEIGHT_REACHED in result.warnings)
        // RMR = 780 + 1100 - 170 + 5 = 1715; TDEE = 1715 * 1.375 = 2358.125
        assertEquals(2358, result.targets.kcal)
    }

    @Test
    fun `a zero requested rate produces maintenance without warnings`() {
        val result = available(inputs(rate = 0.0))

        assertEquals(0.0, result.effectiveLossRateKgPerWeek, TOLERANCE)
        assertEquals(emptySet(), result.warnings)
    }

    @Test
    fun `the weekly rate is limited to one kilogram and one percent of body weight`() {
        val onePercentBound = available(inputs(weight = 60.0, targetWeight = 55.0, rate = 2.0))
        assertTrue(DailyTargetWarning.RATE_LIMITED in onePercentBound.warnings)
        assertTrue(onePercentBound.effectiveLossRateKgPerWeek <= 0.6 + TOLERANCE)

        val absoluteBound = available(inputs(weight = 140.0, targetWeight = 100.0, rate = 3.0))
        assertTrue(DailyTargetWarning.RATE_LIMITED in absoluteBound.warnings)
        assertTrue(absoluteBound.effectiveLossRateKgPerWeek <= 1.0 + TOLERANCE)
    }

    @Test
    fun `the deficit never exceeds twenty percent of energy expenditure`() {
        val result = available(inputs(rate = 1.0))

        // TDEE 2418.625, so the deficit stops at 483.725 kcal.
        assertEquals(1935, result.targets.kcal)
        assertTrue(DailyTargetWarning.DEFICIT_CAPPED in result.warnings)
    }

    @Test
    fun `the deficit never exceeds seven hundred fifty kilocalories`() {
        // RMR = 1200 + 1187.5 - 150 + 5 = 2242.5; TDEE = 3868.3125, so 20% is above 750.
        val result =
            available(
                ProfileInputs(
                    currentWeightKg = 120.0,
                    heightCm = 190.0,
                    ageYears = 30,
                    energyEquationSex = EnergyEquationSex.MALE,
                    activityLevel = ActivityLevel.HIGH,
                    targetWeightKg = 100.0,
                    requestedLossRateKgPerWeek = 1.0,
                ),
            )

        assertEquals(3118, result.targets.kcal)
        assertTrue(DailyTargetWarning.DEFICIT_CAPPED in result.warnings)
        assertEquals(750.0 * 7 / 7700, result.effectiveLossRateKgPerWeek, 0.001)
    }

    @Test
    fun `low energy estimates use the percentage deficit cap`() {
        val cases =
            listOf(
                ProfileInputs(
                    currentWeightKg = 69.0,
                    heightCm = 165.0,
                    ageYears = 35,
                    energyEquationSex = EnergyEquationSex.MALE,
                    activityLevel = ActivityLevel.SEDENTARY,
                    targetWeightKg = 60.0,
                    requestedLossRateKgPerWeek = 0.34,
                ) to 1489,
                ProfileInputs(
                    currentWeightKg = 50.0,
                    heightCm = 150.0,
                    ageYears = 60,
                    energyEquationSex = EnergyEquationSex.FEMALE,
                    activityLevel = ActivityLevel.SEDENTARY,
                    targetWeightKg = 45.0,
                    requestedLossRateKgPerWeek = 0.5,
                ) to 937,
            )

        cases.forEach { (input, expectedKcal) ->
            val result = available(input)
            assertEquals(expectedKcal, result.targets.kcal)
            assertEquals(setOf(DailyTargetWarning.DEFICIT_CAPPED), result.warnings)
        }
    }

    @Test
    fun `protein is raised to the ten percent floor when weight based protein is too low`() {
        val result =
            available(
                ProfileInputs(
                    currentWeightKg = 40.0,
                    heightCm = 180.0,
                    ageYears = 18,
                    energyEquationSex = EnergyEquationSex.MALE,
                    activityLevel = ActivityLevel.HIGH,
                    targetWeightKg = 40.0,
                    requestedLossRateKgPerWeek = 0.0,
                ),
            )

        val weightBasedProtein = 1.2 * 40.0
        assertTrue(result.targets.proteinG > weightBasedProtein)
        assertEquals(0.10, result.targets.proteinG * 4 / result.targets.kcal, 0.01)
    }

    @Test
    fun `guardrails are never silent`() {
        listOf(
            inputs(rate = 2.0),
            inputs(rate = 1.0),
            inputs(weight = 55.0, height = 165.0, targetWeight = 50.0, rate = 1.0),
        ).forEach { input ->
            val result = available(input)
            val paceDiffers =
                abs(result.requestedLossRateKgPerWeek - result.effectiveLossRateKgPerWeek) > TOLERANCE
            assertTrue(
                !paceDiffers || result.warnings.isNotEmpty(),
                "a changed pace must carry a warning for $input",
            )
        }
    }

    @Test
    fun `macro shares stay inside the reference ranges and match the calorie target`() {
        val grid =
            buildList {
                for (sex in EnergyEquationSex.entries) {
                    for (activity in ActivityLevel.entries) {
                        for (weight in listOf(45.0, 60.0, 82.4, 120.0, 180.0)) {
                            for (age in listOf(18, 34, 55, 80)) {
                                for (rate in listOf(0.0, 0.25, 0.5, 1.0, 2.5)) {
                                    add(
                                        ProfileInputs(
                                            currentWeightKg = weight,
                                            heightCm = 170.0,
                                            ageYears = age,
                                            energyEquationSex = sex,
                                            activityLevel = activity,
                                            targetWeightKg = weight - 5.0,
                                            requestedLossRateKgPerWeek = rate,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

        grid.forEach { input ->
            val result = available(input)
            val targets = result.targets
            val kcal = targets.kcal.toDouble()

            assertTrue(targets.kcal > 0, "kcal must be positive for $input")
            listOf(targets.proteinG, targets.fatG, targets.carbsG).forEach { grams ->
                assertTrue(grams.isFinite() && grams >= 0.0, "macro grams must be finite and non-negative for $input")
            }

            val energySum = targets.proteinG * 4 + targets.fatG * 9 + targets.carbsG * 4
            assertEquals(kcal, energySum, CalculateDailyTargets.ENERGY_SUM_TOLERANCE_KCAL, "energy sum for $input")

            val proteinShare = targets.proteinG * 4 / kcal
            val fatShare = targets.fatG * 9 / kcal
            val carbsShare = targets.carbsG * 4 / kcal
            assertTrue(proteinShare in 0.099..0.301, "protein share $proteinShare for $input")
            assertEquals(0.25, fatShare, 0.005, "fat share for $input")
            assertTrue(carbsShare in 0.449..0.651, "carbs share $carbsShare for $input")
        }
    }

    private fun available(inputs: ProfileInputs): DailyTargetResult.Available {
        val result = calculate(inputs)
        assertTrue(result is DailyTargetResult.Available, "expected an available target for $inputs")
        return result
    }

    private fun inputs(
        weight: Double = 82.4,
        height: Double = 176.0,
        age: Int = 34,
        sex: EnergyEquationSex = EnergyEquationSex.MALE,
        activityLevel: ActivityLevel = ActivityLevel.LIGHT,
        targetWeight: Double = 78.0,
        rate: Double = 0.5,
    ) = ProfileInputs(
        currentWeightKg = weight,
        heightCm = height,
        ageYears = age,
        energyEquationSex = sex,
        activityLevel = activityLevel,
        targetWeightKg = targetWeight,
        requestedLossRateKgPerWeek = rate,
    )

    private companion object {
        const val TOLERANCE = 0.0001
    }
}
