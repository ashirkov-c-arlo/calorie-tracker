package app.kcal.domain.usecase

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure conversions between canonical metric storage and imperial input/display. Stored
 * values are never migrated; conversion happens only at the input and display boundaries.
 */
object UnitConversions {

    const val POUNDS_PER_KILOGRAM: Double = 2.20462262185
    const val CENTIMETRES_PER_INCH: Double = 2.54
    const val INCHES_PER_FOOT: Int = 12

    fun kilogramsToPounds(kg: Double): Double = kg * POUNDS_PER_KILOGRAM

    fun poundsToKilograms(pounds: Double): Double = pounds / POUNDS_PER_KILOGRAM

    fun kilogramsPerWeekToPoundsPerWeek(kgPerWeek: Double): Double = kilogramsToPounds(kgPerWeek)

    fun poundsPerWeekToKilogramsPerWeek(poundsPerWeek: Double): Double = poundsToKilograms(poundsPerWeek)

    fun centimetresToFeetAndInches(cm: Double): FeetAndInches {
        val totalInches = cm / CENTIMETRES_PER_INCH
        val feet = floor(totalInches / INCHES_PER_FOOT).toInt()
        val inches = totalInches - feet * INCHES_PER_FOOT
        val roundedInches = (inches * 10.0).roundToInt() / 10.0
        return if (roundedInches >= INCHES_PER_FOOT) {
            FeetAndInches(feet = feet + 1, inches = 0.0)
        } else {
            FeetAndInches(feet = feet, inches = roundedInches)
        }
    }

    fun feetAndInchesToCentimetres(feet: Int, inches: Double): Double =
        (feet * INCHES_PER_FOOT + inches) * CENTIMETRES_PER_INCH

    data class FeetAndInches(val feet: Int, val inches: Double)
}
