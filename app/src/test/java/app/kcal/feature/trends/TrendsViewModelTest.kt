package app.kcal.feature.trends

import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.model.WeightEntry
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.BuildWeightTrend
import app.kcal.domain.usecase.LogWeight
import app.kcal.feature.profile.ProfileFieldError
import app.kcal.testing.FakeProfileRepository
import kotlinx.coroutines.CompletableDeferred
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
    private val clock = MutableClock(Instant.parse("2026-03-15T07:00:00Z"))
    private val timeProvider = TimeProvider(clock, ZoneId.of("UTC"))
    private val locale = Locale.forLanguageTag("en")

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
        // The editor starts on the current local date and shows what is stored for it.
        assertEquals(today, loaded.editedDate)
        assertTrue(loaded.isEditingToday)
        assertEquals("81.0", loaded.weightInput)
    }

    @Test
    fun `saving upserts the edited date and replaces the same-day entry`() = runTest {
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
        assertFalse(states.last().isSaving)
    }

    @Test
    fun `a logged past day can be selected and corrected`() = runTest {
        val past = today.minusDays(3)
        val repository = repository(past to 90.0, today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        viewModel.onEntryClick(past)
        runCurrent()

        assertEquals(past, states.last().editedDate)
        assertFalse(states.last().isEditingToday)
        assertEquals("90.0", states.last().weightInput)

        viewModel.onWeightChange("83.5")
        viewModel.onSave()
        runCurrent()

        assertEquals(mapOf(past to 83.5, today to 81.0), repository.weightsByDate.value.toMap())

        viewModel.onLogTodayClick()
        runCurrent()

        assertEquals(today, states.last().editedDate)
        assertEquals("81.0", states.last().weightInput)
    }

    @Test
    fun `an untouched field follows a weight changed elsewhere, a typed one is kept`() = runTest {
        val repository = repository(today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        // Settings upserts today's weight through the same storage.
        repository.logWeight(WeightEntry(today, 79.5))
        runCurrent()

        assertEquals("79.5", states.last().weightInput)

        viewModel.onWeightChange("78.0")
        repository.logWeight(WeightEntry(today.minusDays(1), 82.0))
        runCurrent()

        assertEquals("78.0", states.last().weightInput)
    }

    @Test
    fun `after midnight the editor moves to the new day instead of rewriting yesterday`() = runTest {
        val repository = repository(today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()
        assertEquals("81.0", states.last().weightInput)

        clock.current = Instant.parse("2026-03-16T06:00:00Z")
        viewModel.onVisible()
        runCurrent()

        val tomorrow = today.plusDays(1)
        assertEquals(tomorrow, states.last().editedDate)
        // The new day has nothing stored yet, so yesterday's value cannot be written back.
        assertEquals("", states.last().weightInput)

        viewModel.onWeightChange("80.6")
        viewModel.onSave()
        runCurrent()

        assertEquals(mapOf(today to 81.0, tomorrow to 80.6), repository.weightsByDate.value.toMap())
    }

    @Test
    fun `saving past midnight resolves the day from the clock without a lifecycle event`() = runTest {
        val repository = repository(today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        // The screen stayed open across midnight: no ON_RESUME, so only the clock knows.
        clock.current = Instant.parse("2026-03-16T06:00:00Z")
        viewModel.onWeightChange("80.6")
        viewModel.onSave()
        runCurrent()

        val tomorrow = today.plusDays(1)
        assertEquals(mapOf(today to 81.0, tomorrow to 80.6), repository.weightsByDate.value.toMap())
        assertEquals(tomorrow, states.last().editedDate)
        assertTrue(states.last().isEditingToday)
        assertEquals("80.6", states.last().weightInput)
    }

    @Test
    fun `selecting today's row is not offered a switch to today`() = runTest {
        val repository = repository(today.minusDays(1) to 83.0, today to 81.0)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        viewModel.onEntryClick(today)
        runCurrent()

        assertEquals(today, states.last().editedDate)
        assertTrue(states.last().isEditingToday)

        viewModel.onEntryClick(today.minusDays(1))
        runCurrent()

        assertFalse(states.last().isEditingToday)
    }

    @Test
    fun `a draft started for another date survives a save that lands afterwards`() = runTest {
        val past = today.minusDays(3)
        val repository = repository(past to 90.0, today to 81.0)
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(GatedProfileRepository(repository, gate))
        val states = collect(viewModel)
        runCurrent()

        viewModel.onWeightChange("81.5")
        viewModel.onSave()
        runCurrent()
        assertTrue(states.last().isSaving)

        // The editor moves to another day while today's write is still in flight, and the new
        // draft happens to hold the same text as the pending one.
        viewModel.onEntryClick(past)
        runCurrent()
        assertEquals("90.0", states.last().weightInput)
        viewModel.onWeightChange("81.5")

        gate.complete(Unit)
        runCurrent()

        assertEquals(past, states.last().editedDate)
        assertEquals("81.5", states.last().weightInput)

        viewModel.onSave()
        runCurrent()

        assertEquals(mapOf(past to 81.5, today to 81.5), repository.weightsByDate.value.toMap())
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
    fun `a failed save is not blamed on the date the editor moved to`() = runTest {
        val past = today.minusDays(3)
        val repository = repository(past to 90.0, today to 81.0)
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(GatedProfileRepository(repository, gate))
        val states = collect(viewModel)
        val events = mutableListOf<TrendsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        runCurrent()

        viewModel.onWeightChange("81.5")
        viewModel.onSave()
        runCurrent()

        repository.writeFails = true
        viewModel.onEntryClick(past)
        runCurrent()
        viewModel.onWeightChange("88.0")
        gate.complete(Unit)
        runCurrent()

        // The message names the date that failed instead of appearing under the new draft.
        assertEquals(listOf<TrendsEvent>(TrendsEvent.SaveFailed(today)), events)
        assertFalse(states.last().saveFailed)
        assertNull(states.last().inputError)
        assertEquals("88.0", states.last().weightInput)
    }

    @Test
    fun `an inline save failure is dropped when the editor changes date`() = runTest {
        val past = today.minusDays(3)
        val repository = repository(past to 90.0, today to 81.0)
        repository.writeFails = true
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        viewModel.onWeightChange("80.4")
        viewModel.onSave()
        runCurrent()
        assertTrue(states.last().saveFailed)

        viewModel.onEntryClick(past)
        runCurrent()

        assertFalse(states.last().saveFailed)
    }

    @Test
    fun `repeated taps cannot overlap, so an older write cannot land last`() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()

        viewModel.onWeightChange("81.0")
        viewModel.onSave()
        assertTrue(states.last().isSaving)
        viewModel.onWeightChange("80.0")
        viewModel.onSave()
        runCurrent()

        assertEquals(listOf(WeightEntry(today, 81.0)), repository.loggedWeights)
        assertFalse(states.last().isSaving)
        // The value typed while the first save was in flight is not discarded.
        assertEquals("80.0", states.last().weightInput)

        viewModel.onSave()
        runCurrent()

        assertEquals(
            listOf(WeightEntry(today, 81.0), WeightEntry(today, 80.0)),
            repository.loggedWeights,
        )
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
        assertFalse(states.last().isSaving)
        assertEquals(mapOf(today to 81.0), repository.weightsByDate.value.toMap())
    }

    private fun repository(
        vararg entries: Pair<LocalDate, Double>,
        unitSystem: UnitSystem = UnitSystem.METRIC,
    ): FakeProfileRepository = FakeProfileRepository(UserPreferences(unitSystem = unitSystem)).apply {
        weightsByDate.value = sortedMapOf(*entries)
    }

    private fun viewModel(repository: ProfileRepository): TrendsViewModel = TrendsViewModel(
        profileRepository = repository,
        logWeight = LogWeight(repository),
        buildWeightTrend = BuildWeightTrend(),
        timeProvider = timeProvider,
        localeProvider = AppLocaleProvider { locale },
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

    /** Holds a write open, so a save can be observed while the editor keeps being used. */
    private class GatedProfileRepository(
        private val delegate: ProfileRepository,
        private val gate: CompletableDeferred<Unit>,
    ) : ProfileRepository by delegate {
        override suspend fun logWeight(entry: WeightEntry) {
            gate.await()
            delegate.logWeight(entry)
        }
    }
}
