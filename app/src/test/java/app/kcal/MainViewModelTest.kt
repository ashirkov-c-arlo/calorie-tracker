package app.kcal

import app.kcal.domain.model.ThemeMode
import app.kcal.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `starts loading and then reports the gate result and theme`() = runTest {
        val repository = FakeProfileRepository(isComplete = false, themeMode = ThemeMode.SYSTEM)
        val viewModel = MainViewModel(repository)
        val states = mutableListOf<MainUiState>()
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }

        runCurrent()
        repository.isComplete.value = true
        repository.theme.value = ThemeMode.BLACK
        runCurrent()
        collection.cancel()

        assertEquals(
            MainUiState(isLoading = true, isProfileComplete = false, themeMode = ThemeMode.SYSTEM),
            states.first(),
        )
        assertEquals(
            MainUiState(isLoading = false, isProfileComplete = true, themeMode = ThemeMode.BLACK),
            states.last(),
        )
    }

    private class FakeProfileRepository(isComplete: Boolean, themeMode: ThemeMode) : ProfileRepository {
        val isComplete = MutableStateFlow(isComplete)
        val theme = MutableStateFlow(themeMode)

        override val isProfileComplete: Flow<Boolean> = this.isComplete
        override val themeMode: Flow<ThemeMode> = theme
    }
}
