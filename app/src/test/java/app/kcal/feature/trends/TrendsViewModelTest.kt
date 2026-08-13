package app.kcal.feature.trends

import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.usecase.BuildWeightTrend
import app.kcal.feature.profile.ProfileFieldError
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
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {

    private val today = LocalDate.of(2026, 3, 15)
    private val timeProvider =
        TimeProvider(Clock.fixed(Instant.parse("2026-03-15T07:00:00Z"), ZoneId.of("UTC")), ZoneId.of("UTC"))
    private val locale = Locale.forLanguageTag("en")

    @Before
    fun setUp() {
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
        // The field starts from the entry of the current local date.
        assertEquals("81.0", loaded.weightInput)
    }

    @Test
    fun `saving upserts the current local date and replaces the same-day entry`() = runTest {
        val repository = repository(today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        viewModel.onWeightChange("80.4")
        viewModel.onSave()
        runCurrent()

        assertEquals(mapOf(today to 80.4), repository.weightsByDate.value.toMap())
        assertEquals(listOf(80.4), states.last().points.map { it.value })
        assertNull(states.last().inputError)
        assertFalse(states.last().saveFailed)
    }

    @Test
    fun `imperial input is stored in kilograms and displayed in pounds`() = runTest {
        val repository = repository(unitSystem = UnitSystem.IMPERIAL)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        // A decimal comma is accepted next to the dot.
        viewModel.onWeightChange("180,5")
        viewModel.onSave()
        runCurrent()

        val storedKg = repository.weightsByDate.value.getValue(today)
        assertEquals(81.9, storedKg, absoluteTolerance = 0.05)
        assertEquals(180.5, states.last().points.single().value, absoluteTolerance = 0.05)
    }

    @Test
    fun `switching units refills the field so a typed value cannot change meaning`() = runTest {
        val repository = repository(today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()
        assertEquals("81.0", states.last().weightInput)

        viewModel.onWeightChange("80.4")
        repository.setUnitSystem(UnitSystem.IMPERIAL)
        runCurrent()

        assertEquals("178.6", states.last().weightInput)
        assertEquals(UnitSystem.IMPERIAL, states.last().unitSystem)
    }

    @Test
    fun `blank, unparseable and out-of-range values are reported and never stored`() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        viewModel.onSave()
        runCurrent()
        assertEquals(ProfileFieldError.REQUIRED, states.last().inputError)

        viewModel.onWeightChange("8oo")
        viewModel.onSave()
        runCurrent()
        assertEquals(ProfileFieldError.INVALID_NUMBER, states.last().inputError)

        viewModel.onWeightChange("640")
        viewModel.onSave()
        runCurrent()
        assertEquals(ProfileFieldError.OUT_OF_RANGE, states.last().inputError)

        assertTrue(repository.weightsByDate.value.isEmpty())
        assertTrue(states.last().points.isEmpty())
    }

    @Test
    fun `a failing read is retryable and a failing write is reported`() = runTest {
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

        repository.writeFails = true
        viewModel.onWeightChange("80.4")
        viewModel.onSave()
        runCurrent()

        assertTrue(states.last().saveFailed)
        assertEquals(mapOf(today to 81.0), repository.weightsByDate.value.toMap())
    }

    private fun repository(
        vararg entries: Pair<LocalDate, Double>,
        unitSystem: UnitSystem = UnitSystem.METRIC,
    ): FakeProfileRepository = FakeProfileRepository(UserPreferences(unitSystem = unitSystem)).apply {
        weightsByDate.value = sortedMapOf(*entries)
    }

    private fun viewModel(repository: FakeProfileRepository): TrendsViewModel = TrendsViewModel(
        profileRepository = repository,
        buildWeightTrend = BuildWeightTrend(),
        timeProvider = timeProvider,
        localeProvider = AppLocaleProvider { locale },
    )

    private fun TestScope.collect(viewModel: TrendsViewModel): List<TrendsUiState> {
        val states = mutableListOf<TrendsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
        return states
    }
}
