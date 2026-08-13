package app.kcal.feature.history

import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.Macros
import app.kcal.domain.usecase.AggregateMealMacros
import app.kcal.domain.usecase.BuildHistory
import app.kcal.testing.FakeDailyTargetRepository
import app.kcal.testing.FakeMealRepository
import app.kcal.testing.foodItem
import app.kcal.testing.mealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val monday = LocalDate.of(2026, 3, 16)
    private val tuesday = LocalDate.of(2026, 3, 17)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading becomes weeks of days with their own saved targets`() = runTest {
        val meals =
            FakeMealRepository(
                listOf(
                    mealEntry(
                        id = 1,
                        localDate = monday,
                        at = Instant.parse("2026-03-16T08:00:00Z"),
                        items = listOf(foodItem(kcal = 500)),
                    ),
                    mealEntry(
                        id = 2,
                        localDate = tuesday,
                        at = Instant.parse("2026-03-17T08:00:00Z"),
                        items = listOf(foodItem(kcal = 1_000)),
                    ),
                ),
            )
        val targets = FakeDailyTargetRepository().apply {
            snapshots.value = mapOf(monday to snapshot(monday))
        }
        val viewModel = viewModel(meals, targets)
        assertTrue(viewModel.uiState.value.isLoading)

        runCurrent()

        val week = viewModel.uiState.value.weeks.single()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1_500L, week.consumed.kcal)
        assertEquals(listOf(tuesday, monday), week.days.map { it.localDate })
        assertNull(week.days.first().progress)
        val progress = assertNotNull(week.days.last().progress)
        assertEquals(2_000, progress.target.kcal)
        assertEquals(0.25f, progress.kcalFraction)
        assertEquals(0.1f, progress.proteinFraction)
        assertEquals(0.1f, progress.fatFraction)
        assertEquals(0.2f, progress.carbsFraction)
    }

    @Test
    fun `clicking a day expands its meals and clicking again collapses them`() = runTest {
        val meals = FakeMealRepository(listOf(mealEntry(id = 7, localDate = monday)))
        val viewModel = viewModel(meals, FakeDailyTargetRepository())
        runCurrent()
        assertFalse(day(viewModel).isExpanded)

        viewModel.onDayClick(monday)
        runCurrent()
        assertTrue(day(viewModel).isExpanded)
        assertEquals(listOf(7L), day(viewModel).meals.map { it.id })

        viewModel.onDayClick(monday)
        runCurrent()
        assertFalse(day(viewModel).isExpanded)
    }

    @Test
    fun `deleting a meal from an expanded day updates day and week totals`() = runTest {
        val meals =
            FakeMealRepository(
                listOf(
                    mealEntry(id = 1, localDate = monday, items = listOf(foodItem(kcal = 400))),
                    mealEntry(
                        id = 2,
                        localDate = monday,
                        at = Instant.parse("2026-03-16T18:00:00Z"),
                        items = listOf(foodItem(kcal = 600)),
                    ),
                ),
            )
        val viewModel = viewModel(meals, FakeDailyTargetRepository())
        viewModel.onDayClick(monday)
        runCurrent()
        assertEquals(1_000L, viewModel.uiState.value.weeks.single().consumed.kcal)

        viewModel.onDeleteMeal(1)
        runCurrent()

        assertEquals(listOf(2L), day(viewModel).meals.map { it.id })
        assertEquals(600L, day(viewModel).consumed.kcal)
        assertEquals(600L, viewModel.uiState.value.weeks.single().consumed.kcal)
    }

    @Test
    fun `an empty journal is content without weeks`() = runTest {
        val viewModel = viewModel(FakeMealRepository(), FakeDailyTargetRepository())

        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasError)
        assertTrue(viewModel.uiState.value.weeks.isEmpty())
    }

    @Test
    fun `storage error is retryable`() = runTest {
        val meals = FakeMealRepository(listOf(mealEntry(localDate = monday))).apply { readFails = true }
        val viewModel = viewModel(meals, FakeDailyTargetRepository())
        runCurrent()
        assertTrue(viewModel.uiState.value.hasError)

        meals.readFails = false
        viewModel.onRetry()
        runCurrent()

        assertFalse(viewModel.uiState.value.hasError)
        assertEquals(1, viewModel.uiState.value.weeks.size)
    }

    @Test
    fun `delete failure reports itself once and keeps the history visible`() = runTest {
        val meals = FakeMealRepository(listOf(mealEntry(localDate = monday))).apply { writeFails = true }
        val viewModel = viewModel(meals, FakeDailyTargetRepository())
        runCurrent()

        viewModel.onDeleteMeal(1)
        runCurrent()

        assertEquals(HistoryEvent.DeleteFailed, viewModel.events.first())
        assertFalse(viewModel.uiState.value.hasError)
        assertEquals(1, viewModel.uiState.value.weeks.single().days.single().meals.size)
        assertEquals(1, meals.meals.value.size)
    }

    private fun day(viewModel: HistoryViewModel): HistoryDayUiState =
        viewModel.uiState.value.weeks.single().days.single()

    private fun viewModel(meals: FakeMealRepository, targets: FakeDailyTargetRepository): HistoryViewModel {
        val aggregate = AggregateMealMacros()
        return HistoryViewModel(
            mealRepository = meals,
            dailyTargetRepository = targets,
            buildHistory = BuildHistory(aggregate),
            aggregateMealMacros = aggregate,
        )
    }

    private fun snapshot(localDate: LocalDate) = DailyTargetSnapshot(
        localDate = localDate,
        targets = Macros(kcal = 2_000, proteinG = 100.0, fatG = 60.0, carbsG = 250.0),
        effectiveLossRateKgPerWeek = 0.4,
    )
}
