package app.kcal.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
    MaterialTheme(
        colorScheme = if (useBlack) BlackColorScheme else WhiteColorScheme,
        typography = KcalTypography,
        content = content,
    )
}
