package app.kcal.domain.usecase

import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.HistoryDay
import app.kcal.domain.model.Macros
import app.kcal.testing.foodItem
import app.kcal.testing.mealEntry
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildHistoryTest {

    private val buildHistory = BuildHistory(AggregateMealMacros())

    @Test
    fun `days and weeks are newest first with per-day and per-week totals`() {
        val weeks =
            buildHistory(
                meals =
                listOf(
                    meal(id = 1, date = LocalDate.of(2026, 3, 16), kcal = 400),
                    meal(id = 2, date = LocalDate.of(2026, 3, 16), kcal = 600),
                    meal(id = 3, date = LocalDate.of(2026, 3, 18), kcal = 700),
                    meal(id = 4, date = LocalDate.of(2026, 3, 9), kcal = 100),
                ),
                targets = emptyList(),
            )

        assertEquals(listOf(LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 9)), weeks.map { it.start })
        assertEquals(listOf(12, 11), weeks.map { it.weekOfYear })
        assertEquals(1_700L, weeks.first().consumed.kcal)
        assertEquals(
            listOf(LocalDate.of(2026, 3, 18), LocalDate.of(2026, 3, 16)),
            weeks.first().days.map(HistoryDay::localDate),
        )
        assertEquals(listOf(700L, 1_000L), weeks.first().days.map { it.consumed.kcal })
        assertEquals(LocalDate.of(2026, 3, 22), weeks.first().end)
    }

    @Test
    fun `meals of a day stay chronological`() {
        val day = LocalDate.of(2026, 3, 16)
        val weeks =
            buildHistory(
                meals =
                listOf(
                    meal(id = 2, date = day, kcal = 1, at = Instant.parse("2026-03-16T19:00:00Z")),
                    meal(id = 1, date = day, kcal = 2, at = Instant.parse("2026-03-16T07:00:00Z")),
                ),
                targets = emptyList(),
            )

        assertEquals(listOf(1L, 2L), weeks.single().days.single().meals.map { it.id })
    }

    @Test
    fun `iso weeks follow Monday starts across a month and a year boundary`() {
        val weeks =
            buildHistory(
                meals =
                listOf(
                    // Sunday, still ISO week 5 of 2027 that started on Monday 2027-02-01.
                    meal(id = 1, date = LocalDate.of(2027, 2, 7), kcal = 100),
                    // Monday of ISO week 6 of 2027.
                    meal(id = 2, date = LocalDate.of(2027, 2, 8), kcal = 100),
                    // Friday 2027-01-01 belongs to ISO week 53 of week-based year 2026.
                    meal(id = 3, date = LocalDate.of(2027, 1, 1), kcal = 100),
                ),
                targets = emptyList(),
            )

        assertEquals(
            listOf(Triple(2027, 6, LocalDate.of(2027, 2, 8)), Triple(2027, 5, LocalDate.of(2027, 2, 1))),
            weeks.take(2).map { Triple(it.weekBasedYear, it.weekOfYear, it.start) },
        )
        val newYearWeek = weeks.last()
        assertEquals(2026 to 53, newYearWeek.weekBasedYear to newYearWeek.weekOfYear)
        assertEquals(LocalDate.of(2026, 12, 28), newYearWeek.start)
        assertEquals(LocalDate.of(2027, 1, 3), newYearWeek.end)
    }

    @Test
    fun `each day keeps the target snapshot saved for its own date`() {
        val first = LocalDate.of(2026, 3, 16)
        val second = LocalDate.of(2026, 3, 17)
        val weeks =
            buildHistory(
                meals = listOf(meal(id = 1, date = first, kcal = 100), meal(id = 2, date = second, kcal = 100)),
                targets = listOf(snapshot(first, kcal = 2_100)),
            )

        val days = weeks.single().days.associateBy(HistoryDay::localDate)
        assertEquals(2_100, days.getValue(first).target?.kcal)
        assertNull(days.getValue(second).target)
    }

    @Test
    fun `days without meals are not history rows`() {
        val weeks = buildHistory(meals = emptyList(), targets = listOf(snapshot(LocalDate.of(2026, 3, 16))))

        assertTrue(weeks.isEmpty())
    }

    private fun meal(
        id: Long,
        date: LocalDate,
        kcal: Int,
        at: Instant = date.atStartOfDay().toInstant(ZoneOffset.UTC),
    ) = mealEntry(id = id, localDate = date, at = at, items = listOf(foodItem(kcal = kcal)))

    private fun snapshot(date: LocalDate, kcal: Int = 2_000) = DailyTargetSnapshot(
        localDate = date,
        targets = Macros(kcal = kcal, proteinG = 120.0, fatG = 55.0, carbsG = 250.0),
        effectiveLossRateKgPerWeek = 0.5,
    )
}
