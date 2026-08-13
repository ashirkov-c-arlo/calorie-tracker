package app.kcal.domain.usecase

import app.kcal.domain.model.WeightEntry
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * The trend is a calendar window: the mean of the entries dated within the last seven days,
 * never an interpolation and never the last seven measurements.
 */
class BuildWeightTrendTest {

    private val buildWeightTrend = BuildWeightTrend()

    @Test
    fun `no entries produce no points`() {
        assertEquals(emptyList(), buildWeightTrend(emptyList()))
    }

    @Test
    fun `a single entry is its own trend`() {
        val date = LocalDate.of(2026, 3, 15)

        val points = buildWeightTrend(listOf(WeightEntry(date, 82.4)))

        assertEquals(1, points.size)
        assertEquals(82.4, points.single().kg)
        assertEquals(82.4, points.single().trendKg)
    }

    @Test
    fun `unordered input becomes a chronological series`() {
        val start = LocalDate.of(2026, 3, 10)

        val points =
            buildWeightTrend(
                listOf(
                    WeightEntry(start.plusDays(2), 81.0),
                    WeightEntry(start, 83.0),
                    WeightEntry(start.plusDays(1), 82.0),
                ),
            )

        assertEquals(listOf(start, start.plusDays(1), start.plusDays(2)), points.map { it.localDate })
        assertEquals(listOf(83.0, 82.5, 82.0), points.map { it.trendKg })
    }

    @Test
    fun `the window covers seven calendar days and ignores older entries`() {
        val start = LocalDate.of(2026, 3, 1)
        val entries = (0..7).map { WeightEntry(start.plusDays(it.toLong()), 80.0 + it) }

        val points = buildWeightTrend(entries)

        // Day 7 (March 8) averages March 2..8 and drops March 1.
        assertEquals(84.0, points.last().trendKg)
        // Day 6 (March 7) still averages all seven logged days.
        assertEquals(83.0, points[6].trendKg)
    }

    @Test
    fun `a calendar gap is not interpolated and older measurements are not substituted`() {
        val entries =
            listOf(
                WeightEntry(LocalDate.of(2026, 3, 1), 90.0),
                WeightEntry(LocalDate.of(2026, 3, 2), 88.0),
                // Nothing logged for eight days.
                WeightEntry(LocalDate.of(2026, 3, 11), 80.0),
                WeightEntry(LocalDate.of(2026, 3, 12), 82.0),
            )

        val points = buildWeightTrend(entries)

        // March 11 has no other entry inside March 5..11, so the trend is that entry alone.
        assertEquals(80.0, points[2].trendKg)
        // March 12 averages only March 11 and 12; the two March entries are outside the window.
        assertEquals(81.0, points[3].trendKg)
    }

    @Test
    fun `windows span month and year boundaries`() {
        val entries =
            listOf(
                WeightEntry(LocalDate.of(2025, 12, 29), 84.0),
                WeightEntry(LocalDate.of(2025, 12, 31), 83.0),
                WeightEntry(LocalDate.of(2026, 1, 1), 82.0),
                WeightEntry(LocalDate.of(2026, 1, 4), 81.0),
                WeightEntry(LocalDate.of(2026, 2, 28), 80.0),
                WeightEntry(LocalDate.of(2026, 3, 1), 79.0),
            )

        val points = buildWeightTrend(entries)

        // January 1 averages December 29, December 31 and January 1.
        assertEquals(83.0, points[2].trendKg)
        // January 4 averages December 29 onwards, because December 29 is still within six days.
        assertEquals(82.5, points[3].trendKg)
        // March 1 averages February 28 and March 1 across the month boundary.
        assertEquals(79.5, points[5].trendKg)
    }
}
