package app.kcal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.ReconcileTodayTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    private val reconcileTodayTarget: ReconcileTodayTarget,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> =
        profileRepository.preferences
            .map { preferences ->
                MainUiState(
                    isLoading = false,
                    isProfileComplete = preferences.profile.isComplete,
                    themeMode = preferences.themeMode,
                    appLanguage = preferences.appLanguage,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = MainUiState(),
            )

    init {
        // A crash or a failed write between DataStore and Room can leave a complete profile
        // without today's target. Repair it before the main navigation opens.
        viewModelScope.launch {
            try {
                reconcileTodayTarget()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                // Nothing to show at the app shell level; the next start tries again.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
