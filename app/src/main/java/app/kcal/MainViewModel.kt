package app.kcal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.ReconcileTodayTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    private val reconcileTodayTarget: ReconcileTodayTarget,
) : ViewModel() {

    /**
     * A crash or a failed write between DataStore and Room can leave today's target missing
     * or stale, so it is rewritten before anything is shown. The gate stays in its loading
     * state until that finishes, and a storage failure becomes a retryable error state
     * instead of silently opening a screen without a goal.
     */
    private val startupState = MutableStateFlow(StartupState.IN_PROGRESS)

    val uiState: StateFlow<MainUiState> =
        combine(profileRepository.preferences, startupState) { preferences, startup ->
            MainUiState(
                isLoading = startup == StartupState.IN_PROGRESS,
                isProfileComplete = preferences.profile.isComplete,
                themeMode = preferences.themeMode,
                appLanguage = preferences.appLanguage,
                startupFailed = startup == StartupState.FAILED,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = MainUiState(),
        )

    init {
        reconcile()
    }

    fun onRetryStartup() {
        reconcile()
    }

    private fun reconcile() {
        startupState.value = StartupState.IN_PROGRESS
        viewModelScope.launch {
            startupState.value =
                try {
                    reconcileTodayTarget()
                    StartupState.READY
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (storageFailure: Exception) {
                    // Handled by surfacing a retryable error state; details are never logged.
                    StartupState.FAILED
                }
        }
    }

    private enum class StartupState {
        IN_PROGRESS,
        READY,
        FAILED,
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
