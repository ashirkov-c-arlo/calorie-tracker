package app.kcal.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.kcal.domain.model.ThemeMode

/**
 * Resolves the palette to use. [ThemeMode.SYSTEM] follows the system setting; anything
 * that cannot be resolved falls back to the White palette.
 */
fun shouldUseBlackPalette(themeMode: ThemeMode, isSystemInDarkTheme: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme
    ThemeMode.WHITE -> false
    ThemeMode.BLACK -> true
}

@Composable
fun KcalTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val useBlack = shouldUseBlackPalette(themeMode, isSystemInDarkTheme())

    // System bar icons follow the resolved palette instead of the system dark mode, so an
    // explicitly chosen White or Black theme keeps the bars readable under edge-to-edge.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useBlack
                    isAppearanceLightNavigationBars = !useBlack
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = if (useBlack) BlackColorScheme else WhiteColorScheme,
        typography = KcalTypography,
        content = content,
    )
}
