package app.kcal

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.usecase.ApplyTodayTarget
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.ReconcileTodayTarget
import app.kcal.testing.FakeDailyTargetRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val today = LocalDate.of(2026, 3, 15)
    private val timeProvider =
        TimeProvider(
            clock = Clock.fixed(Instant.parse("2026-03-15T09:00:00Z"), ZoneId.of("UTC")),
            zoneId = ZoneId.of("UTC"),
        )
    private val dailyTargetRepository = FakeDailyTargetRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startup rewrites today's target before the gate reports a complete profile`() = runTest {
        val repository = FakeProfileRepository(UserPreferences(profile = completeProfile()))
        val viewModel = viewModel(repository)
        val states = collect(viewModel)

        runCurrent()

        assertEquals(MainUiState(), states.first())
        val loaded = states.last()
        assertEquals(false, loaded.isLoading)
        assertEquals(true, loaded.isProfileComplete)
        assertNotNull(dailyTargetRepository.find(today))
        assertTrue(states.none { !it.isLoading && it.isProfileComplete && dailyTargetMissing() })
    }

    @Test
    fun `theme and language follow the stored preferences`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = viewModel(repository)
        val states = collect(viewModel)

        runCurrent()
        repository.state.value =
            UserPreferences(
                profile = completeProfile(),
                themeMode = ThemeMode.BLACK,
                appLanguage = AppLanguage.RUSSIAN,
            )
        runCurrent()

        assertEquals(
            MainUiState(
                isLoading = false,
                isProfileComplete = true,
                themeMode = ThemeMode.BLACK,
                appLanguage = AppLanguage.RUSSIAN,
            ),
            states.last(),
        )
    }

    @Test
    fun `an incomplete profile keeps the gate closed`() = runTest {
        val repository =
            FakeProfileRepository(UserPreferences(profile = completeProfile(activityLevel = null)))
        val viewModel = viewModel(repository)
        val states = collect(viewModel)

        runCurrent()

        assertEquals(false, states.last().isProfileComplete)
        assertEquals(false, states.last().isLoading)
        assertNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `a missing current weight keeps the gate closed`() = runTest {
        val repository =
            FakeProfileRepository(UserPreferences(profile = StoredProfile(heightCm = 176.0)))
        val viewModel = viewModel(repository)
        val states = collect(viewModel)

        runCurrent()

        assertEquals(false, states.last().isProfileComplete)
    }

    @Test
    fun `a storage failure during startup becomes a retryable error instead of an open screen`() = runTest {
        val repository = FakeProfileRepository(UserPreferences(profile = completeProfile()))
        dailyTargetRepository.failNextWrites(true)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)

        runCurrent()

        val failed = states.last()
        assertTrue(failed.startupFailed)
        assertEquals(false, failed.isLoading)
        assertNull(dailyTargetRepository.find(today))

        dailyTargetRepository.failNextWrites(false)
        viewModel.onRetryStartup()
        runCurrent()

        val recovered = states.last()
        assertEquals(false, recovered.startupFailed)
        assertEquals(false, recovered.isLoading)
        assertNotNull(dailyTargetRepository.find(today))
    }

    private fun dailyTargetMissing(): Boolean = dailyTargetRepository.snapshots.value.isEmpty()

    private fun TestScope.collect(viewModel: MainViewModel): List<MainUiState> {
        val states = mutableListOf<MainUiState>()
        // backgroundScope keeps the collector from outliving the test body.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
        return states
    }

    private fun viewModel(repository: FakeProfileRepository) = MainViewModel(
        repository,
        ReconcileTodayTarget(
            repository,
            ApplyTodayTarget(dailyTargetRepository, CalculateDailyTargets()),
            timeProvider,
        ),
    )
}
