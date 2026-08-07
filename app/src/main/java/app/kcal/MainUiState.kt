package app.kcal

import app.kcal.domain.model.ThemeMode

data class MainUiState(
    val isLoading: Boolean = true,
    val isProfileComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
