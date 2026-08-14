package app.kcal.domain.usecase

import app.kcal.domain.model.Macros
import app.kcal.domain.model.ProfileInputs
import app.kcal.domain.model.StoredProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Why a daily target cannot be estimated. Never replaced by a guessed target. */
enum class DailyTargetUnavailableReason {
    MISSING_PROFILE_INPUTS,
    AGE_BELOW_MINIMUM,
    INVALID_MEASUREMENTS,
    NON_POSITIVE_ENERGY,
}

/** A guardrail that made the effective pace differ from the requested one. */
enum class DailyTargetWarning {
    /** The requested pace exceeded 1 kg per week or 1% of body weight per week. */
    RATE_LIMITED,

    /** The deficit was capped at 20% of TDEE or 750 kcal. */
    DEFICIT_CAPPED,

    /** Current weight already reached the target weight, so the target is maintenance. */
    TARGET_WEIGHT_REACHED,
}

sealed interface DailyTargetResult {

    data class Available(
        val targets: Macros,
        val requestedLossRateKgPerWeek: Double,
        val effectiveLossRateKgPerWeek: Double,
        val warnings: Set<DailyTargetWarning>,
    ) : DailyTargetResult

    data class Unavailable(val reason: DailyTargetUnavailableReason) : DailyTargetResult
}

/**
 * Deterministic daily calorie and macro estimate for generally healthy adults aged 18+.
 * Mifflin-St Jeor resting metabolic rate, one habitual activity multiplier, conservative
 * rate and deficit guardrails, weight-based protein, fat at 25% of energy, and
 * carbohydrates as the remainder. An LLM never performs this arithmetic.
 */
class CalculateDailyTargets {

    fun forStoredProfile(profile: StoredProfile): DailyTargetResult = profile.toInputs()?.let { invoke(it) }
        ?: DailyTargetResult.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS)

    operator fun invoke(inputs: ProfileInputs): DailyTargetResult {
        validate(inputs)?.let { return DailyTargetResult.Unavailable(it) }

        val restingMetabolicRate =
            WEIGHT_FACTOR * inputs.currentWeightKg +
                HEIGHT_FACTOR * inputs.heightCm -
                AGE_FACTOR * inputs.ageYears +
                inputs.energyEquationSex.rmrOffsetKcal
        if (!restingMetabolicRate.isFinite() || restingMetabolicRate <= 0.0) {
            return DailyTargetResult.Unavailable(DailyTargetUnavailableReason.NON_POSITIVE_ENERGY)
        }

        val totalDailyEnergyExpenditure = restingMetabolicRate * inputs.activityLevel.pal
        if (!totalDailyEnergyExpenditure.isFinite() || totalDailyEnergyExpenditure <= 0.0) {
            return DailyTargetResult.Unavailable(DailyTargetUnavailableReason.NON_POSITIVE_ENERGY)
        }

        val warnings = mutableSetOf<DailyTargetWarning>()
        val targetReached = inputs.currentWeightKg <= inputs.targetWeightKg
        if (targetReached) {
            warnings += DailyTargetWarning.TARGET_WEIGHT_REACHED
        }

        val bodyWeightRateLimit = inputs.currentWeightKg * MAX_WEEKLY_BODY_WEIGHT_FRACTION
        val safeRateKgPerWeek =
            minOf(inputs.requestedLossRateKgPerWeek, MAX_WEEKLY_LOSS_KG, bodyWeightRateLimit)
        if (!targetReached && safeRateKgPerWeek < inputs.requestedLossRateKgPerWeek) {
            warnings += DailyTargetWarning.RATE_LIMITED
        }

        val requestedDeficit = safeRateKgPerWeek * KCAL_PER_KG_OF_BODY_MASS / DAYS_PER_WEEK
        val cappedDeficit =
            minOf(
                requestedDeficit,
                totalDailyEnergyExpenditure * MAX_DEFICIT_FRACTION_OF_TDEE,
                MAX_DEFICIT_KCAL,
            )
        if (!targetReached && cappedDeficit < requestedDeficit) {
            warnings += DailyTargetWarning.DEFICIT_CAPPED
        }

        val targetKcalExact =
            if (targetReached) totalDailyEnergyExpenditure else totalDailyEnergyExpenditure - cappedDeficit

        val targets = macrosFor(targetKcalExact, inputs)
        val effectiveLossRateKgPerWeek =
            max(0.0, totalDailyEnergyExpenditure - targetKcalExact) * DAYS_PER_WEEK /
                KCAL_PER_KG_OF_BODY_MASS

        return DailyTargetResult.Available(
            targets = targets,
            requestedLossRateKgPerWeek = inputs.requestedLossRateKgPerWeek,
            effectiveLossRateKgPerWeek = effectiveLossRateKgPerWeek,
            warnings = warnings,
        )
    }

    private fun validate(inputs: ProfileInputs): DailyTargetUnavailableReason? {
        val measurements =
            listOf(
                inputs.currentWeightKg,
                inputs.heightCm,
                inputs.targetWeightKg,
                inputs.requestedLossRateKgPerWeek,
            )
        if (measurements.any { !it.isFinite() }) return DailyTargetUnavailableReason.INVALID_MEASUREMENTS
        if (inputs.currentWeightKg <= 0.0 || inputs.heightCm <= 0.0 || inputs.targetWeightKg <= 0.0) {
            return DailyTargetUnavailableReason.INVALID_MEASUREMENTS
        }
        if (inputs.requestedLossRateKgPerWeek < 0.0) return DailyTargetUnavailableReason.INVALID_MEASUREMENTS
        if (inputs.ageYears <= 0) return DailyTargetUnavailableReason.INVALID_MEASUREMENTS
        if (inputs.ageYears < MIN_AGE_YEARS) return DailyTargetUnavailableReason.AGE_BELOW_MINIMUM
        return null
    }

    /**
     * Rounds only the final stored values: kcal to a whole number, then protein and fat to
     * one decimal, then carbohydrates as the remaining energy so the macro energy sum stays
     * within [ENERGY_SUM_TOLERANCE_KCAL] of the calorie target.
     */
    private fun macrosFor(targetKcalExact: Double, inputs: ProfileInputs): Macros {
        val referenceWeightKg = min(inputs.currentWeightKg, inputs.targetWeightKg)
        val proteinCandidateG = PROTEIN_G_PER_KG * referenceWeightKg
        val proteinG =
            proteinCandidateG.coerceIn(
                targetKcalExact * MIN_PROTEIN_ENERGY_FRACTION / KCAL_PER_G_PROTEIN,
                targetKcalExact * MAX_PROTEIN_ENERGY_FRACTION / KCAL_PER_G_PROTEIN,
            )
        val fatG = targetKcalExact * FAT_ENERGY_FRACTION / KCAL_PER_G_FAT

        val kcal = targetKcalExact.roundToInt()
        val roundedProteinG = proteinG.roundToOneDecimal()
        val roundedFatG = fatG.roundToOneDecimal()
        val carbsG =
            ((kcal - roundedProteinG * KCAL_PER_G_PROTEIN - roundedFatG * KCAL_PER_G_FAT) / KCAL_PER_G_CARBS)
                .coerceAtLeast(0.0)
                .roundToOneDecimal()

        return Macros(kcal = kcal, proteinG = roundedProteinG, fatG = roundedFatG, carbsG = carbsG)
    }

    private fun Double.roundToOneDecimal(): Double = (this * 10.0).roundToInt() / 10.0

    companion object {
        const val MIN_AGE_YEARS: Int = 18
        const val ENERGY_SUM_TOLERANCE_KCAL: Double = 1.0

        private const val WEIGHT_FACTOR = 10.0
        private const val HEIGHT_FACTOR = 6.25
        private const val AGE_FACTOR = 5.0
        private const val DAYS_PER_WEEK = 7.0
        private const val KCAL_PER_KG_OF_BODY_MASS = 7700.0
        private const val MAX_WEEKLY_LOSS_KG = 1.0
        private const val MAX_WEEKLY_BODY_WEIGHT_FRACTION = 0.01
        private const val MAX_DEFICIT_FRACTION_OF_TDEE = 0.20
        private const val MAX_DEFICIT_KCAL = 750.0
        private const val PROTEIN_G_PER_KG = 1.2
        private const val MIN_PROTEIN_ENERGY_FRACTION = 0.10
        private const val MAX_PROTEIN_ENERGY_FRACTION = 0.30
        private const val FAT_ENERGY_FRACTION = 0.25
        private const val KCAL_PER_G_PROTEIN = 4.0
        private const val KCAL_PER_G_FAT = 9.0
        private const val KCAL_PER_G_CARBS = 4.0
    }
}
