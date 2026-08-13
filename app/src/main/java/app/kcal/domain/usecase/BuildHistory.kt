package app.kcal.domain.usecase

import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.HistoryDay
import app.kcal.domain.model.HistoryWeek
import app.kcal.domain.model.MealEntry
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * Groups the journal into logged days and ISO weeks, newest first. All arithmetic is local;
 * an LLM never aggregates history.
 */
class BuildHistory(private val aggregateMealMacros: AggregateMealMacros) {

    operator fun invoke(meals: List<MealEntry>, targets: List<DailyTargetSnapshot>): List<HistoryWeek> {
        val targetsByDate = targets.associateBy { it.localDate }
        val days =
            meals
                .groupBy(MealEntry::localDate)
                .map { (localDate, dayMeals) ->
                    val ordered = dayMeals.sortedWith(compareBy(MealEntry::at, MealEntry::id))
                    HistoryDay(
                        localDate = localDate,
                        consumed = aggregateMealMacros(ordered),
                        target = targetsByDate[localDate]?.targets,
                        meals = ordered,
                    )
                }
        return days
            .groupBy { it.localDate.isoWeek() }
            .map { (week, weekDays) ->
                val orderedDays = weekDays.sortedByDescending(HistoryDay::localDate)
                HistoryWeek(
                    weekBasedYear = week.year,
                    weekOfYear = week.number,
                    start = week.monday,
                    end = week.monday.plusDays(DAYS_PER_WEEK - 1),
                    consumed = aggregateMealMacros(orderedDays.flatMap(HistoryDay::meals)),
                    days = orderedDays,
                )
            }
            .sortedByDescending { it.start }
    }

    private data class IsoWeek(val year: Int, val number: Int, val monday: LocalDate)

    private fun LocalDate.isoWeek(): IsoWeek = IsoWeek(
        year = get(WeekFields.ISO.weekBasedYear()),
        number = get(WeekFields.ISO.weekOfWeekBasedYear()),
        monday = with(WeekFields.ISO.dayOfWeek(), 1),
    )

    private companion object {
        const val DAYS_PER_WEEK = 7L
    }
}
