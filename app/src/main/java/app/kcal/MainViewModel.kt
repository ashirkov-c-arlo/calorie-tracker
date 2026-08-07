package app.kcal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(profileRepository: ProfileRepository) : ViewModel() {

    val uiState: StateFlow<MainUiState> =
        combine(
            profileRepository.isProfileComplete,
            profileRepository.themeMode,
        ) { isProfileComplete, themeMode ->
            MainUiState(
                isLoading = false,
                isProfileComplete = isProfileComplete,
                themeMode = themeMode,
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
