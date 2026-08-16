package app.kcal.domain.model

import kotlin.math.pow

/**
 * The calorie deficit range and the hard kilocalorie cap that apply to one body-mass state.
 *
 * The three offered positions are the low bound, the midpoint and the high bound of the
 * range, so every choice stays inside the band. Weekly weight loss is derived from the
 * resulting deficit and is a reference value only, never an input.
 */
enum class DeficitBand(val minFraction: Double, val maxFraction: Double, val capKcal: Double) {
    /** Body mass index from [MIN_DEFICIT_BMI] to below 25. */
    NORMAL_WEIGHT(minFraction = 0.10, maxFraction = 0.15, capKcal = 400.0),

    /** Body mass index from 25 to below 30. */
    OVERWEIGHT(minFraction = 0.15, maxFraction = 0.20, capKcal = 600.0),

    /** Body mass index of 30 and above. */
    OBESE(minFraction = 0.20, maxFraction = 0.25, capKcal = 750.0),

    /** Habitual hard activity, which replaces the body-mass band. */
    HIGH_ACTIVITY(minFraction = 0.15, maxFraction = 0.20, capKcal = 750.0),
    ;

    fun fractionFor(pace: LossPace): Double = when (pace) {
        LossPace.SLOW -> minFraction
        LossPace.MODERATE -> (minFraction + maxFraction) / 2.0
        LossPace.FAST -> maxFraction
    }

    companion object {
        /** Below this body mass index no deficit applies and the goal is maintenance. */
        const val MIN_DEFICIT_BMI: Double = 18.5

        private const val OVERWEIGHT_BMI = 25.0
        private const val OBESE_BMI = 30.0
        private const val CENTIMETRES_PER_METRE = 100.0

        /**
         * Null when no deficit applies at all: unusable measurements, or a body mass index
         * below [MIN_DEFICIT_BMI]. [ActivityLevel.HIGH] overrides the body-mass band, but not
         * that lower bound: maintenance still wins there.
         */
        fun forBody(weightKg: Double?, heightCm: Double?, activityLevel: ActivityLevel?): DeficitBand? {
            val bodyMassIndex = bodyMassIndex(weightKg, heightCm) ?: return null
            return when {
                bodyMassIndex < MIN_DEFICIT_BMI -> null
                activityLevel == ActivityLevel.HIGH -> HIGH_ACTIVITY
                bodyMassIndex < OVERWEIGHT_BMI -> NORMAL_WEIGHT
                bodyMassIndex < OBESE_BMI -> OVERWEIGHT
                else -> OBESE
            }
        }

        /** Null when the measurements cannot produce a body mass index. */
        fun bodyMassIndex(weightKg: Double?, heightCm: Double?): Double? {
            if (weightKg == null || heightCm == null) return null
            if (!weightKg.isFinite() || !heightCm.isFinite() || weightKg <= 0.0 || heightCm <= 0.0) return null
            return (weightKg / (heightCm / CENTIMETRES_PER_METRE).pow(2)).takeIf { it.isFinite() && it > 0.0 }
        }
    }
}
