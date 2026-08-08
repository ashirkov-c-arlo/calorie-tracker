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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
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

    private val currentDate = MutableStateFlow(timeProvider.today())
    private var startupJob: Job? = null

    init {
        start()
    }

    fun onAppResumed() {
        currentDate.value = timeProvider.today()
    }

    fun onRetryStartup() {
        start()
    }

    private fun start() {
        if (startupJob?.isActive == true) return
        _uiState.value = MainUiState()
        startupJob =
            viewModelScope.launch {
                try {
                    var syncedTarget: Pair<StoredProfile, LocalDate>? = null
                    combine(profileRepository.preferences, currentDate) { preferences, _ ->
                        preferences to timeProvider.today()
                    }.collect { (preferences, localDate) ->
                        val target = preferences.profile to localDate
                        if (target != syncedTarget) {
                            // This serial collector is the sole owner of target replacement.
                            _uiState.value =
                                _uiState.value.copy(
                                    isLoading = true,
                                    isProfileComplete = false,
                                    startupFailed = false,
                                    themeMode = preferences.themeMode,
                                    appLanguage = preferences.appLanguage,
                                )
                            applyTodayTarget(preferences.profile, localDate)
                            syncedTarget = target
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
