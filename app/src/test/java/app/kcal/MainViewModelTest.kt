package app.kcal

import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UserPreferences
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading and then reports the gate result, theme and language`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = MainViewModel(repository)
        val states = mutableListOf<MainUiState>()
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }

        runCurrent()
        repository.state.value =
            UserPreferences(
                profile = completeProfile(),
                themeMode = ThemeMode.BLACK,
                appLanguage = AppLanguage.RUSSIAN,
            )
        runCurrent()
        collection.cancel()

        assertEquals(MainUiState(), states.first())
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
            FakeProfileRepository(
                UserPreferences(profile = completeProfile().copy(activityLevel = null)),
            )
        val viewModel = MainViewModel(repository)
        val states = mutableListOf<MainUiState>()
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }

        runCurrent()
        collection.cancel()

        assertEquals(false, states.last().isProfileComplete)
        assertEquals(false, states.last().isLoading)
    }

    @Test
    fun `a missing current weight keeps the gate closed`() = runTest {
        val repository =
            FakeProfileRepository(UserPreferences(profile = StoredProfile(heightCm = 176.0)))
        val viewModel = MainViewModel(repository)
        val states = mutableListOf<MainUiState>()
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }

        runCurrent()
        collection.cancel()

        assertEquals(false, states.last().isProfileComplete)
    }
}
