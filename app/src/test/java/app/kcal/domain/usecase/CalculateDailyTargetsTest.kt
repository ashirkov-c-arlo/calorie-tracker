package app.kcal.domain.usecase

import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.DeficitBand
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.ProfileInputs
import app.kcal.domain.model.StoredProfile
import org.junit.Test
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculateDailyTargetsTest {

    private val calculate = CalculateDailyTargets()

    @Test
    fun `male branch matches the documented arithmetic`() {
        // RMR = 10*82.4 + 6.25*176 - 5*34 + 5 = 1759; TDEE = 1759 * 1.375 = 2418.625.
        // 82.4 kg at 176 cm is a body mass index of 26.6, so the middle position asks 17.5%.
        val result = available(inputs(pace = LossPace.MODERATE))

        assertEquals(1995, result.targets.kcal)
        assertEquals(93.6, result.targets.proteinG)
        assertEquals(55.4, result.targets.fatG)
        assertEquals(280.5, result.targets.carbsG)
        assertEquals(423, result.deficitKcal)
        assertEquals(423.259375 * 7 / 7700, result.effectiveLossRateKgPerWeek, TOLERANCE)
        assertEquals(emptySet(), result.warnings)
    }

    @Test
    fun `female branch matches the documented arithmetic`() {
        // RMR = 10*70 + 6.25*165 - 5*30 - 161 = 1420.25; TDEE = 1704.3; body mass index 25.7.
        val result =
            available(
                inputs(
                    weight = 70.0,
                    height = 165.0,
                    age = 30,
                    sex = EnergyEquationSex.FEMALE,
                    activityLevel = ActivityLevel.SEDENTARY,
                    targetWeight = 65.0,
                    pace = LossPace.MODERATE,
                ),
            )

        assertEquals(1406, result.targets.kcal)
        assertEquals(78.0, result.targets.proteinG)
        assertEquals(39.1, result.targets.fatG)
        assertEquals(298, result.deficitKcal)
        assertEquals(emptySet(), result.warnings)
    }

    @Test
    fun `the two branches never share constants`() {
        val female = available(inputs(sex = EnergyEquationSex.FEMALE))
        val male = available(inputs(sex = EnergyEquationSex.MALE))

        // 166 kcal of resting metabolic rate, scaled by the activity multiplier and by the
        // share of energy that is left after the deficit.
        val expected = 166.0 * ActivityLevel.LIGHT.pal * (1 - 0.175)
        assertEquals(expected, (male.targets.kcal - female.targets.kcal).toDouble(), 1.0)
    }

    @Test
    fun `each position takes its share of the band and the weekly loss follows the deficit`() {
        val cases =
            listOf(
                // Body mass index 21.2: normal weight, 10% to 15%.
                Triple(65.0, 175.0, DeficitBand.NORMAL_WEIGHT),
                // Body mass index 26.1: overweight, 15% to 20%.
                Triple(80.0, 175.0, DeficitBand.OVERWEIGHT),
                // Body mass index 32.7: obese, 20% to 25%.
                Triple(100.0, 175.0, DeficitBand.OBESE),
            )

        cases.forEach { (weight, height, band) ->
            val energy = totalDailyEnergyExpenditure(weight, height, AGE, EnergyEquationSex.MALE, ActivityLevel.LIGHT)
            LossPace.entries.forEach { pace ->
                val result = available(inputs(weight = weight, height = height, targetWeight = 55.0, pace = pace))
                val expectedDeficit = min(energy * band.fractionFor(pace), band.capKcal)

                assertEquals(expectedDeficit.roundToInt(), result.deficitKcal, "$band at $pace")
                assertEquals(
                    expectedDeficit * 7 / 7700,
                    result.effectiveLossRateKgPerWeek,
                    TOLERANCE,
                    "weekly loss of $band at $pace",
                )
                assertEquals((energy - expectedDeficit).roundToInt(), result.targets.kcal, "$band at $pace")
            }
        }
    }

    @Test
    fun `every band stops at its own hard cap`() {
        val cases =
            listOf(
                // Normal weight, 400 kcal: 15% of 2894.625 is 434.
                Triple(
                    inputs(weight = 80.0, height = 190.0, age = 25, activityLevel = ActivityLevel.MODERATE),
                    400,
                    2495,
                ),
                // Overweight, 600 kcal: 20% of 3041.875 is 608.
                Triple(
                    inputs(weight = 97.0, height = 182.0, age = 30, activityLevel = ActivityLevel.MODERATE),
                    600,
                    2442,
                ),
                // Obese, 750 kcal: 25% of 3023.28 is 756.
                Triple(inputs(weight = 130.0, height = 175.0, age = 40), 750, 2273),
                // Hard habitual activity, 750 kcal: 20% of 3792.84 is 759.
                Triple(inputs(weight = 130.0, height = 175.0, age = 40, activityLevel = ActivityLevel.HIGH), 750, 3043),
            )

        cases.forEach { (input, expectedDeficit, expectedKcal) ->
            val result = available(input.copy(lossPace = LossPace.FAST))

            assertEquals(expectedDeficit, result.deficitKcal, "cap for $input")
            assertEquals(expectedKcal, result.targets.kcal, "target for $input")
            assertEquals(setOf(DailyTargetWarning.DEFICIT_CAPPED), result.warnings, "warning for $input")
        }
    }

    @Test
    fun `hard habitual activity replaces the body mass band`() {
        val obese = inputs(weight = 100.0, height = 175.0, age = 40, targetWeight = 80.0, pace = LossPace.FAST)

        val moderate = available(obese.copy(activityLevel = ActivityLevel.MODERATE))
        val high = available(obese.copy(activityLevel = ActivityLevel.HIGH))

        // 25% of 2943.0625 against 20% of 3275.34375: the override lowers the share, not the cap.
        assertEquals(736, moderate.deficitKcal)
        assertEquals(655, high.deficitKcal)
        assertEquals(emptySet(), high.warnings)
    }

    @Test
    fun `below the reference body mass index the goal is maintenance`() {
        // 50 kg at 175 cm is a body mass index of 16.3; RMR = 1448.75, TDEE = 1992.03.
        val underweight = inputs(weight = 50.0, height = 175.0, age = 30, targetWeight = 48.0, pace = null)

        listOf(underweight, underweight.copy(lossPace = LossPace.FAST)).forEach { input ->
            val result = available(input)

            assertEquals(1992, result.targets.kcal, "maintenance for $input")
            assertEquals(0, result.deficitKcal)
            assertEquals(0.0, result.effectiveLossRateKgPerWeek, TOLERANCE)
            assertEquals(setOf(DailyTargetWarning.NO_DEFICIT_BELOW_REFERENCE_BMI), result.warnings)
        }

        // Hard habitual activity does not override the lower bound.
        val active = available(underweight.copy(activityLevel = ActivityLevel.HIGH))
        assertEquals(0, active.deficitKcal)
        assertTrue(DailyTargetWarning.NO_DEFICIT_BELOW_REFERENCE_BMI in active.warnings)
    }

    @Test
    fun `a missing position has no target wherever a deficit applies`() {
        assertEquals(
            DailyTargetResult.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
            calculate(inputs(pace = null)),
        )
    }

    @Test
    fun `every activity level raises the target monotonically`() {
        val targets = ActivityLevel.entries.map { available(inputs(activityLevel = it)).targets.kcal }

        assertEquals(targets.sorted(), targets)
        assertEquals(ActivityLevel.entries.size, targets.distinct().size)
    }

    @Test
    fun `higher activity moves the extra energy to carbohydrates and keeps protein weight based`() {
        val sedentary = available(inputs(activityLevel = ActivityLevel.SEDENTARY))
        val high = available(inputs(activityLevel = ActivityLevel.HIGH))

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
                    lossPace = LossPace.MODERATE,
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
        val result = available(inputs(weight = 78.0, targetWeight = 78.0, pace = LossPace.FAST))

        assertEquals(0, result.deficitKcal)
        assertEquals(0.0, result.effectiveLossRateKgPerWeek, TOLERANCE)
        assertEquals(setOf(DailyTargetWarning.TARGET_WEIGHT_REACHED), result.warnings)
        // RMR = 780 + 1100 - 170 + 5 = 1715; TDEE = 1715 * 1.375 = 2358.125.
        assertEquals(2358, result.targets.kcal)
    }

    @Test
    fun `protein is raised to the ten percent floor when weight based protein is too low`() {
        val result =
            available(
                inputs(
                    weight = 40.0,
                    height = 180.0,
                    age = 18,
                    activityLevel = ActivityLevel.HIGH,
                    targetWeight = 40.0,
                    pace = null,
                ),
            )

        assertTrue(result.targets.proteinG > 1.2 * 40.0)
        assertEquals(0.10, result.targets.proteinG * 4 / result.targets.kcal, 0.01)
    }

    @Test
    fun `a capped deficit is never silent`() {
        listOf(
            inputs(weight = 130.0, height = 175.0, age = 40, pace = LossPace.FAST),
            inputs(
                weight = 80.0,
                height = 190.0,
                age = 25,
                activityLevel = ActivityLevel.MODERATE,
                pace = LossPace.FAST,
            ),
        ).forEach { input ->
            val result = available(input)
            val band =
                requireNotNull(DeficitBand.forBody(input.currentWeightKg, input.heightCm, input.activityLevel))

            assertEquals(band.capKcal.roundToInt(), result.deficitKcal, "the cap applies for $input")
            assertTrue(DailyTargetWarning.DEFICIT_CAPPED in result.warnings, "a capped deficit must be explained")
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
                                for (pace in LossPace.entries) {
                                    add(
                                        inputs(
                                            weight = weight,
                                            height = 170.0,
                                            age = age,
                                            sex = sex,
                                            activityLevel = activity,
                                            targetWeight = weight - 5.0,
                                            pace = pace,
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
            assertEquals(
                result.deficitKcal * 7 / 7700.0,
                result.effectiveLossRateKgPerWeek,
                0.001,
                "the weekly loss follows the deficit for $input",
            )
        }
    }

    private fun available(inputs: ProfileInputs): DailyTargetResult.Available {
        val result = calculate(inputs)
        assertTrue(result is DailyTargetResult.Available, "expected an available target for $inputs")
        return result
    }

    /** Independent arithmetic, so the expected energy expenditure is not read from production code. */
    private fun totalDailyEnergyExpenditure(
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        sex: EnergyEquationSex,
        activityLevel: ActivityLevel,
    ): Double {
        val offset = if (sex == EnergyEquationSex.MALE) 5.0 else -161.0
        return (10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + offset) * activityLevel.pal
    }

    private fun inputs(
        weight: Double = 82.4,
        height: Double = 176.0,
        age: Int = AGE,
        sex: EnergyEquationSex = EnergyEquationSex.MALE,
        activityLevel: ActivityLevel = ActivityLevel.LIGHT,
        targetWeight: Double = 78.0,
        pace: LossPace? = LossPace.MODERATE,
    ) = ProfileInputs(
        currentWeightKg = weight,
        heightCm = height,
        ageYears = age,
        energyEquationSex = sex,
        activityLevel = activityLevel,
        targetWeightKg = targetWeight,
        lossPace = pace,
    )

    private companion object {
        const val TOLERANCE = 0.0001
        const val AGE = 34
    }
}
