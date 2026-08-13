package app.kcal.feature.today

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.Macros
import app.kcal.domain.usecase.AggregateMealMacros
import app.kcal.testing.FakeDailyTargetRepository
import app.kcal.testing.FakeMealRepository
import app.kcal.testing.foodItem
import app.kcal.testing.mealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val today = LocalDate.of(2026, 3, 15)
    private val clock = MutableClock(Instant.parse("2026-03-15T10:00:00Z"))
    private val timeProvider = TimeProvider(clock, ZoneId.of("UTC"))

    @Before
    fun setUp() {
        clock.current = Instant.parse("2026-03-15T10:00:00Z")
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading becomes chronological content with aggregated progress`() = runTest {
        val meals =
            FakeMealRepository(
                listOf(
                    mealEntry(
                        id = 2,
                        at = Instant.parse("2026-03-15T12:00:00Z"),
                        items = listOf(foodItem(name = "Lunch", kcal = 500)),
                    ),
                    mealEntry(
                        id = 1,
                        at = Instant.parse("2026-03-15T08:00:00Z"),
                        items = listOf(foodItem(name = "Breakfast", kcal = 300)),
                    ),
                ),
            )
        val targets = FakeDailyTargetRepository()
        targets.snapshots.value =
            mapOf(
                today to
                    DailyTargetSnapshot(
                        today,
                        Macros(kcal = 2000, proteinG = 100.0, fatG = 60.0, carbsG = 250.0),
                        0.4,
                    ),
            )
        val viewModel = viewModel(meals, targets)
        val states = collect(viewModel)

        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()

        val loaded = states.last()
        assertFalse(loaded.isLoading)
        assertEquals(listOf("Breakfast", "Lunch"), loaded.meals.map { it.itemNames.single() })
        assertEquals(800, loaded.consumed.kcal)
        assertEquals(0.4f, loaded.progress?.kcalFraction)
    }

    @Test
    fun `meal flow updates totals and delete removes the selected meal`() = runTest {
        val meals = FakeMealRepository()
        val viewModel = viewModel(meals, FakeDailyTargetRepository())
        runCurrent()

        meals.save(mealEntry(id = 4, items = listOf(foodItem(kcal = 250))), null)
        meals.save(mealEntry(id = 5, items = listOf(foodItem(kcal = 350))), null)
        runCurrent()
        assertEquals(600, viewModel.uiState.value.consumed.kcal)

        viewModel.onDeleteMeal(4)
        runCurrent()
        assertEquals(listOf(5L), viewModel.uiState.value.meals.map { it.id })
        assertEquals(350, viewModel.uiState.value.consumed.kcal)
    }

    @Test
    fun `returning after midnight observes the new local day`() = runTest {
        val meals =
            FakeMealRepository(
                listOf(
                    mealEntry(id = 1, localDate = today),
                    mealEntry(id = 2, localDate = today.plusDays(1)),
                ),
            )
        val viewModel = viewModel(meals, FakeDailyTargetRepository())
        runCurrent()
        assertEquals(listOf(1L), viewModel.uiState.value.meals.map { it.id })

        clock.current = Instant.parse("2026-03-16T00:01:00Z")
        viewModel.onVisible()
        runCurrent()

        assertEquals(listOf(2L), viewModel.uiState.value.meals.map { it.id })
    }

    @Test
    fun `delete failure keeps a retryable error state`() = runTest {
        val meals = FakeMealRepository(listOf(mealEntry())).apply { writeFails = true }
        val viewModel = viewModel(meals, FakeDailyTargetRepository())
        runCurrent()

        viewModel.onDeleteMeal(1)
        runCurrent()

        assertTrue(viewModel.uiState.value.hasError)
        assertEquals(1, meals.meals.value.size)
    }

    @Test
    fun `storage error is retryable`() = runTest {
        val meals = FakeMealRepository().apply { readFails = true }
        val viewModel = viewModel(meals, FakeDailyTargetRepository())
        runCurrent()
        assertTrue(viewModel.uiState.value.hasError)

        meals.readFails = false
        viewModel.onRetry()
        runCurrent()

        assertFalse(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun viewModel(meals: FakeMealRepository, targets: FakeDailyTargetRepository) =
        TodayViewModel(meals, targets, AggregateMealMacros(), timeProvider)

    private fun TestScope.collect(viewModel: TodayViewModel): List<TodayUiState> {
        val states = mutableListOf<TodayUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
        return states
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}
