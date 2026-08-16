package app.kcal.feature.trends

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.model.WeightEntry
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.BuildWeightTrend
import app.kcal.testing.FakeProfileRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {

    private val today = LocalDate.of(2026, 3, 15)
    private val clock = MutableClock(Instant.parse("2026-03-15T07:00:00Z"))
    private val timeProvider = TimeProvider(clock, ZoneId.of("UTC"))

    @Before
    fun setUp() {
        clock.current = Instant.parse("2026-03-15T07:00:00Z")
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading becomes raw points with their calendar-window trend`() = runTest {
        val repository = repository(today.minusDays(1) to 83.0, today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)

        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()

        val loaded = states.last()
        assertFalse(loaded.isLoading)
        assertEquals(listOf(today.minusDays(1), today), loaded.points.map { it.localDate })
        assertEquals(listOf(83.0, 81.0), loaded.points.map { it.value })
        assertEquals(listOf(83.0, 82.0), loaded.points.map { it.trendValue })
    }

    @Test
    fun `selecting a logged day updates editedDate`() = runTest {
        val past = today.minusDays(3)
        val repository = repository(past to 90.0, today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        assertNull(states.last().editedDate)

        viewModel.onEntryClick(past)
        runCurrent()

        assertEquals(past, states.last().editedDate)
    }

    @Test
    fun `deleting an entry removes it from the list`() = runTest {
        val past = today.minusDays(3)
        val repository = repository(past to 90.0, today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        assertEquals(2, states.last().points.size)

        viewModel.onDeleteEntry(past)
        runCurrent()

        assertEquals(1, states.last().points.size)
        assertEquals(today, states.last().points.first().localDate)
        assertNull(states.last().editedDate)
    }

    @Test
    fun `deleting the selected entry clears the selection`() = runTest {
        val past = today.minusDays(3)
        val repository = repository(past to 90.0, today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        viewModel.onEntryClick(past)
        runCurrent()
        assertEquals(past, states.last().editedDate)

        viewModel.onDeleteEntry(past)
        runCurrent()

        assertNull(states.last().editedDate)
    }

    @Test
    fun `imperial units display weight in pounds`() = runTest {
        val repository = repository(today to 81.0, unitSystem = UnitSystem.IMPERIAL)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        assertEquals(UnitSystem.IMPERIAL, states.last().unitSystem)
        // 81 kg ≈ 178.6 lb
        assertEquals(178.6, states.last().points.first().value, absoluteTolerance = 0.1)
    }

    @Test
    fun `a failing read is retryable`() = runTest {
        val repository = repository(today to 81.0)
        repository.readFails = true
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        assertTrue(states.last().hasError)

        repository.readFails = false
        viewModel.onRetry()
        runCurrent()

        assertFalse(states.last().hasError)
        assertEquals(1, states.last().points.size)
    }

    @Test
    fun `empty weight list shows empty state`() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        assertTrue(states.last().points.isEmpty())
    }

    private fun repository(
        vararg entries: Pair<LocalDate, Double>,
        unitSystem: UnitSystem = UnitSystem.METRIC,
    ): FakeProfileRepository = FakeProfileRepository(UserPreferences(unitSystem = unitSystem)).apply {
        weightsByDate.value = sortedMapOf(*entries)
    }

    private fun viewModel(repository: ProfileRepository): TrendsViewModel = TrendsViewModel(
        profileRepository = repository,
        buildWeightTrend = BuildWeightTrend(),
        timeProvider = timeProvider,
    )

    private fun TestScope.collect(viewModel: TrendsViewModel): List<TrendsUiState> {
        val states = mutableListOf<TrendsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
        return states
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
    }
}
