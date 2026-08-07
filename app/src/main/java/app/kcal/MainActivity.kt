package app.kcal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.designsystem.applyKcalSystemBars
import app.kcal.core.designsystem.shouldUseBlackPalette
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

            KcalTheme(themeMode = uiState.themeMode) {
                KcalApp(uiState = uiState)
            }
        }
    }
}
