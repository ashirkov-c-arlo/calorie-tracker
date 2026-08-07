package app.kcal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.designsystem.applyKcalSystemBars
import app.kcal.core.designsystem.shouldUseBlackPalette
import app.kcal.domain.model.AppLanguage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val useBlackPalette = shouldUseBlackPalette(uiState.themeMode, isSystemInDarkTheme())

            // Edge-to-edge is (re)applied from the resolved palette, so the scrim and the
            // bar icons follow the selected theme rather than the system one.
            LaunchedEffect(useBlackPalette) { applyKcalSystemBars(useBlackPalette) }

            // Only applied once preferences are loaded: applying the default during the
            // initial loading state would clear a stored locale and recreate the activity.
            val appLanguage = uiState.appLanguageToApply()
            LaunchedEffect(appLanguage) { appLanguage?.let(::applyAppLanguage) }

            KcalTheme(themeMode = uiState.themeMode) {
                KcalApp(uiState = uiState)
            }
        }
    }

    /**
     * Applies the selected interface language through the AppCompat app-locale API. The call
     * is skipped when the locales already match, because applying them recreates the
     * activity.
     */
    private fun applyAppLanguage(appLanguage: AppLanguage) {
        val requested =
            appLanguage.languageTag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
        if (AppCompatDelegate.getApplicationLocales() != requested) {
            AppCompatDelegate.setApplicationLocales(requested)
        }
    }
}
