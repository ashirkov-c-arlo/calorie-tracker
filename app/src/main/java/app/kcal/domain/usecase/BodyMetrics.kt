package app.kcal.domain.usecase

import kotlin.math.pow

/**
 * Body reference values that are plain arithmetic, not advice. The target weight range is
 * the weight interval for a body mass index of 18.5 to 24.9 at a given height, which is the
 * commonly published adult reference range.
 */
object BodyMetrics {

    const val MIN_REFERENCE_BMI: Double = 18.5
    const val MAX_REFERENCE_BMI: Double = 24.9

    /** Soft sanity bounds for a persisted body weight, shared by every weight input. */
    val PLAUSIBLE_WEIGHT_RANGE_KG: ClosedFloatingPointRange<Double> = 20.0..400.0

    /** Null when the height cannot produce a usable range. */
    fun targetWeightRangeKg(heightCm: Double?): ClosedFloatingPointRange<Double>? {
        if (heightCm == null || !heightCm.isFinite() || heightCm <= 0.0) return null
        val heightMetres = heightCm / CENTIMETRES_PER_METRE
        val squared = heightMetres.pow(2)
        val minimum = MIN_REFERENCE_BMI * squared
        val maximum = MAX_REFERENCE_BMI * squared
        if (!minimum.isFinite() || !maximum.isFinite() || minimum <= 0.0 || maximum <= minimum) return null
        return minimum..maximum
    }

    private const val CENTIMETRES_PER_METRE = 100.0
}
