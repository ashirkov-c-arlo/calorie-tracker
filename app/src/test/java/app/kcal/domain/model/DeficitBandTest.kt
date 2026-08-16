package app.kcal.domain.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeficitBandTest {

    @Test
    fun `band boundaries follow the body mass index`() {
        val heightCm = 200.0 // 4 m^2, so kilograms map to a body mass index of weight / 4
        val cases =
            mapOf(
                18.49 to null,
                18.5 to DeficitBand.NORMAL_WEIGHT,
                24.99 to DeficitBand.NORMAL_WEIGHT,
                25.0 to DeficitBand.OVERWEIGHT,
                29.99 to DeficitBand.OVERWEIGHT,
                30.0 to DeficitBand.OBESE,
                45.0 to DeficitBand.OBESE,
            )

        cases.forEach { (bodyMassIndex, expected) ->
            assertEquals(
                expected,
                DeficitBand.forBody(bodyMassIndex * 4.0, heightCm, ActivityLevel.LIGHT),
                "body mass index $bodyMassIndex",
            )
        }
    }

    @Test
    fun `hard habitual activity overrides every band except the lower bound`() {
        listOf(20.0, 27.0, 35.0).forEach { bodyMassIndex ->
            assertEquals(
                DeficitBand.HIGH_ACTIVITY,
                DeficitBand.forBody(bodyMassIndex * 4.0, 200.0, ActivityLevel.HIGH),
                "body mass index $bodyMassIndex",
            )
        }
        assertNull(DeficitBand.forBody(17.0 * 4.0, 200.0, ActivityLevel.HIGH))
    }

    @Test
    fun `the three positions are the low bound, the midpoint and the high bound`() {
        val expected =
            mapOf(
                DeficitBand.NORMAL_WEIGHT to Triple(0.10, 0.125, 0.15),
                DeficitBand.OVERWEIGHT to Triple(0.15, 0.175, 0.20),
                DeficitBand.OBESE to Triple(0.20, 0.225, 0.25),
                DeficitBand.HIGH_ACTIVITY to Triple(0.15, 0.175, 0.20),
            )

        DeficitBand.entries.forEach { band ->
            val (slow, moderate, fast) = expected.getValue(band)
            assertEquals(slow, band.fractionFor(LossPace.SLOW), 1e-9, "$band slow")
            assertEquals(moderate, band.fractionFor(LossPace.MODERATE), 1e-9, "$band moderate")
            assertEquals(fast, band.fractionFor(LossPace.FAST), 1e-9, "$band fast")
        }
    }

    @Test
    fun `caps are the approved kilocalorie limits`() {
        assertEquals(400.0, DeficitBand.NORMAL_WEIGHT.capKcal)
        assertEquals(600.0, DeficitBand.OVERWEIGHT.capKcal)
        assertEquals(750.0, DeficitBand.OBESE.capKcal)
        assertEquals(750.0, DeficitBand.HIGH_ACTIVITY.capKcal)
    }

    @Test
    fun `unusable measurements produce neither an index nor a band`() {
        listOf(
            null to 176.0,
            82.4 to null,
            Double.NaN to 176.0,
            82.4 to Double.POSITIVE_INFINITY,
            0.0 to 176.0,
            82.4 to 0.0,
            82.4 to -176.0,
        ).forEach { (weightKg, heightCm) ->
            assertNull(DeficitBand.bodyMassIndex(weightKg, heightCm), "index for $weightKg kg, $heightCm cm")
            assertNull(DeficitBand.forBody(weightKg, heightCm, ActivityLevel.LIGHT), "band for $weightKg kg")
        }

        assertEquals(26.6, assertNotNull(DeficitBand.bodyMassIndex(82.4, 176.0)), 0.05)
    }
}
