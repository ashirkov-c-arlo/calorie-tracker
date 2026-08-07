package app.kcal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(profileRepository: ProfileRepository) : ViewModel() {

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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
