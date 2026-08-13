package app.kcal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.ApplyTodayTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the app shell state. Reading preferences and rewriting today's target happen in one
 * pipeline, so the gate reports a complete profile only once its target is stored, and any
 * storage failure becomes a retryable error state instead of an open screen without a goal.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val applyTodayTarget: ApplyTodayTarget,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var startupJob: Job? = null

    init {
        start()
    }

    fun onRetryStartup() {
        start()
    }

    private fun start() {
        startupJob?.cancel()
        _uiState.value = MainUiState()
        startupJob =
            viewModelScope.launch {
                try {
                    var syncedProfile: StoredProfile? = null
                    profileRepository.preferences.collect { preferences ->
                        if (preferences.profile != syncedProfile) {
                            // Keep the gate closed until the stored target matches this profile.
                            _uiState.value =
                                _uiState.value.copy(
                                    isLoading = true,
                                    isProfileComplete = false,
                                    startupFailed = false,
                                    themeMode = preferences.themeMode,
                                    appLanguage = preferences.appLanguage,
                                )
                            applyTodayTarget(preferences.profile, timeProvider.today())
                            syncedProfile = preferences.profile
                        }
                        _uiState.value =
                            MainUiState(
                                isLoading = false,
                                isProfileComplete = preferences.profile.isComplete,
                                themeMode = preferences.themeMode,
                                appLanguage = preferences.appLanguage,
                            )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (storageFailure: Exception) {
                    // Handled by surfacing a retryable error state; details are never logged.
                    // The last known theme and language are kept so the error screen is
                    // rendered as the user configured it and no locale is cleared.
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            isProfileComplete = false,
                            startupFailed = true,
                        )
                }
            }
    }
}
