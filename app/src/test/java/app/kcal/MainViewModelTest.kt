package app.kcal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.core.common.DispatcherProvider
import app.kcal.core.common.TimeProvider
import app.kcal.core.common.TransientPhotoStore
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.usecase.ApplyTodayTarget
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.DailyTargetResult
import app.kcal.testing.FakeDailyTargetRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MainViewModelTest {

    private val today = LocalDate.of(2026, 3, 15)
    private val clock = MutableClock(Instant.parse("2026-03-15T09:00:00Z"))
    private val timeProvider = TimeProvider(clock = clock, zoneId = ZoneId.of("UTC"))
    private val dailyTargetRepository = FakeDailyTargetRepository()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val photoStore = TransientPhotoStore(context, DispatcherProvider(UnconfinedTestDispatcher()))

    @Before
    fun setUp() {
        clock.current = Instant.parse("2026-03-15T09:00:00Z")
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

        val loaded = states.last()
        assertEquals(false, loaded.isLoading)
        assertEquals(true, loaded.isProfileComplete)
        assertEquals(false, loaded.startupFailed)
        // The gate reports a complete profile only together with a stored target; the
        // failing-write test below covers the opposite direction.
        assertNotNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `resume after midnight creates the new day target in the app shell`() = runTest {
        val repository = FakeProfileRepository(UserPreferences(profile = completeProfile()))
        val viewModel = viewModel(repository)
        runCurrent()
        val firstTarget = assertNotNull(dailyTargetRepository.find(today))

        clock.current = Instant.parse("2026-03-16T00:01:00Z")
        viewModel.onAppResumed()
        runCurrent()

        val nextTarget = assertNotNull(dailyTargetRepository.find(today.plusDays(1)))
        assertEquals(firstTarget.targets, nextTarget.targets)
        assertEquals(2, dailyTargetRepository.upsertCount)
    }

    @Test
    fun `a delayed old profile write cannot finish after the latest profile target`() = runTest {
        val profileA = completeProfile(currentWeightKg = 82.4)
        val profileB = completeProfile(currentWeightKg = 95.0)
        val profiles = FakeProfileRepository(UserPreferences(profile = profileA))
        val targets = DelayedFirstWriteRepository()
        val viewModel = viewModel(profiles, targets)

        targets.firstWriteStarted.await()
        profiles.state.value = UserPreferences(profile = profileB)
        runCurrent()
        assertEquals(1, targets.startedWrites)

        targets.releaseFirstWrite.complete(Unit)
        runCurrent()

        val calculator = CalculateDailyTargets()
        val expectedA = assertIs<DailyTargetResult.Available>(calculator.forStoredProfile(profileA)).targets
        val expectedB = assertIs<DailyTargetResult.Available>(calculator.forStoredProfile(profileB)).targets
        assertNotEquals(expectedA, expectedB)
        assertEquals(listOf(expectedA, expectedB), targets.completedWrites.map { it.targets })
        assertEquals(expectedB, targets.find(today)?.targets)
        assertEquals(1, targets.maxConcurrentWrites)
        assertTrue(viewModel.uiState.value.isProfileComplete)
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

    @Test
    fun `a failing preferences read becomes a retryable error instead of an endless loading state`() = runTest {
        val repository = FakeProfileRepository(UserPreferences(profile = completeProfile()), readFails = true)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)

        runCurrent()

        val failed = states.last()
        assertTrue(failed.startupFailed)
        assertEquals(false, failed.isLoading)

        // Retry uses the same view model, as the error screen does.
        repository.readFails = false
        viewModel.onRetryStartup()
        runCurrent()

        val recovered = states.last()
        assertEquals(false, recovered.startupFailed)
        assertEquals(true, recovered.isProfileComplete)
        assertNotNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `completing the profile keeps the gate closed while the target write fails`() = runTest {
        val repository = FakeProfileRepository(UserPreferences(profile = completeProfile(activityLevel = null)))
        val viewModel = viewModel(repository)
        val states = collect(viewModel)
        runCurrent()
        assertEquals(false, states.last().isProfileComplete)

        dailyTargetRepository.failNextWrites(true)
        repository.state.value = UserPreferences(profile = completeProfile())
        runCurrent()

        // The profile became complete, but its target could not be stored.
        val failed = states.last()
        assertTrue(failed.startupFailed)
        assertEquals(false, failed.isProfileComplete)

        dailyTargetRepository.failNextWrites(false)
        viewModel.onRetryStartup()
        runCurrent()

        assertEquals(true, states.last().isProfileComplete)
        assertNotNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `the error state keeps the stored language and theme`() = runTest {
        val repository =
            FakeProfileRepository(
                UserPreferences(
                    profile = completeProfile(),
                    themeMode = ThemeMode.BLACK,
                    appLanguage = AppLanguage.RUSSIAN,
                ),
            )
        dailyTargetRepository.failNextWrites(true)
        val viewModel = viewModel(repository)
        val states = collect(viewModel)

        runCurrent()

        val failed = states.last()
        assertTrue(failed.startupFailed)
        assertEquals(ThemeMode.BLACK, failed.themeMode)
        assertEquals(AppLanguage.RUSSIAN, failed.appLanguage)
        assertEquals(AppLanguage.RUSSIAN, failed.appLanguageToApply())
    }

    private fun TestScope.collect(viewModel: MainViewModel): List<MainUiState> {
        val states = mutableListOf<MainUiState>()
        // backgroundScope keeps the collector from outliving the test body.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
        return states
    }

    private fun viewModel(repository: FakeProfileRepository, targets: DailyTargetRepository = dailyTargetRepository) =
        MainViewModel(
            repository,
            ApplyTodayTarget(targets, CalculateDailyTargets()),
            timeProvider,
            photoStore,
        )

    @Test
    fun `a meal photo left behind by a crash is deleted on the next start`() = runTest {
        val photoDirectory = File(context.cacheDir, "entry-photos").apply { mkdirs() }
        val leftover = File(photoDirectory, "crash-leftover.jpg").apply { writeText("stale") }

        viewModel(FakeProfileRepository(UserPreferences(profile = completeProfile())))

        assertFalse(leftover.exists())
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }

    private class DelayedFirstWriteRepository(
        private val delegate: FakeDailyTargetRepository = FakeDailyTargetRepository(),
    ) : DailyTargetRepository by delegate {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val completedWrites = mutableListOf<DailyTargetSnapshot>()
        var startedWrites = 0
            private set
        var maxConcurrentWrites = 0
            private set
        private var activeWrites = 0

        override suspend fun upsert(snapshot: DailyTargetSnapshot) {
            startedWrites++
            activeWrites++
            maxConcurrentWrites = maxOf(maxConcurrentWrites, activeWrites)
            try {
                if (startedWrites == 1) {
                    firstWriteStarted.complete(Unit)
                    releaseFirstWrite.await()
                }
                delegate.upsert(snapshot)
                completedWrites += snapshot
            } finally {
                activeWrites--
            }
        }
    }
}
