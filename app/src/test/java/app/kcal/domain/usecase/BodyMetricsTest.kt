package app.kcal.domain.usecase

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BodyMetricsTest {

    @Test
    fun `the target weight range is the reference body mass index range for the height`() {
        val range = assertNotNull(BodyMetrics.targetWeightRangeKg(176.0))

        // 1.76 m squared is 3.0976, so 18.5 and 24.9 bound the interval.
        assertEquals(18.5 * 3.0976, range.start, 0.0001)
        assertEquals(24.9 * 3.0976, range.endInclusive, 0.0001)
    }

    @Test
    fun `a taller height produces a higher range`() {
        val shorter = assertNotNull(BodyMetrics.targetWeightRangeKg(160.0))
        val taller = assertNotNull(BodyMetrics.targetWeightRangeKg(190.0))

        assertEquals(true, taller.start > shorter.start)
        assertEquals(true, taller.endInclusive > shorter.endInclusive)
    }

    @Test
    fun `a missing or unusable height has no range`() {
        assertNull(BodyMetrics.targetWeightRangeKg(null))
        assertNull(BodyMetrics.targetWeightRangeKg(0.0))
        assertNull(BodyMetrics.targetWeightRangeKg(-10.0))
        assertNull(BodyMetrics.targetWeightRangeKg(Double.NaN))
        assertNull(BodyMetrics.targetWeightRangeKg(Double.POSITIVE_INFINITY))
    }
}
