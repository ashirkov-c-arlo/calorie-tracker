package app.kcal

import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.ThemeMode

data class MainUiState(
    val isLoading: Boolean = true,
    val isProfileComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    /** Today's target could not be recomputed on start, so nothing is shown yet. */
    val startupFailed: Boolean = false,
)

/**
 * The interface language to hand to the app-locale API, or null while startup is still in
 * progress. Applying the default during a cold start would clear a stored locale and make
 * the activity recreate twice.
 */
internal fun MainUiState.appLanguageToApply(): AppLanguage? = appLanguage.takeUnless { isLoading }
