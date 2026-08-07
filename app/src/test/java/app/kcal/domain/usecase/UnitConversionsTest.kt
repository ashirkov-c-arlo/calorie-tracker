package app.kcal.domain.usecase

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnitConversionsTest {

    @Test
    fun `known weight values convert both ways`() {
        assertEquals(220.462, UnitConversions.kilogramsToPounds(100.0), 0.001)
        assertEquals(100.0, UnitConversions.poundsToKilograms(220.462262185), 0.0001)
    }

    @Test
    fun `weight round trips within tolerance`() {
        listOf(20.0, 45.5, 82.4, 120.0, 400.0).forEach { kg ->
            val roundTripped = UnitConversions.poundsToKilograms(UnitConversions.kilogramsToPounds(kg))
            assertEquals(kg, roundTripped, KG_TOLERANCE)
        }
    }

    @Test
    fun `height round trips within tolerance`() {
        listOf(150.0, 165.4, 176.0, 190.5, 210.0).forEach { cm ->
            val feetAndInches = UnitConversions.centimetresToFeetAndInches(cm)
            val roundTripped =
                UnitConversions.feetAndInchesToCentimetres(feetAndInches.feet, feetAndInches.inches)
            assertEquals(cm, roundTripped, CM_TOLERANCE)
        }
    }

    @Test
    fun `inches stay inside a foot`() {
        listOf(152.4, 182.88, 210.0, 179.9, 180.0).forEach { cm ->
            val feetAndInches = UnitConversions.centimetresToFeetAndInches(cm)
            assertTrue(
                feetAndInches.inches >= 0.0 && feetAndInches.inches < UnitConversions.INCHES_PER_FOOT,
                "inches out of range for $cm: $feetAndInches",
            )
        }
    }

    @Test
    fun `exact foot boundaries do not report twelve inches`() {
        // 6 ft is 182.88 cm; rounding must roll over into the next foot instead.
        assertEquals(
            UnitConversions.FeetAndInches(feet = 6, inches = 0.0),
            UnitConversions.centimetresToFeetAndInches(182.88),
        )
        assertEquals(
            UnitConversions.FeetAndInches(feet = 5, inches = 0.0),
            UnitConversions.centimetresToFeetAndInches(152.4),
        )
    }

    @Test
    fun `loss rate round trips within tolerance`() {
        listOf(0.0, 0.25, 0.5, 1.0, 2.5).forEach { kgPerWeek ->
            val roundTripped =
                UnitConversions.poundsPerWeekToKilogramsPerWeek(
                    UnitConversions.kilogramsPerWeekToPoundsPerWeek(kgPerWeek),
                )
            assertEquals(kgPerWeek, roundTripped, KG_TOLERANCE)
        }
    }

    private companion object {
        /** Conversion is exact within double precision; storage never changes units. */
        const val KG_TOLERANCE = 1e-9

        /** Inches are shown with one decimal, which is at most 0.13 cm of display rounding. */
        const val CM_TOLERANCE = 0.13
    }
}
