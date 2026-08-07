package app.kcal.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import app.kcal.domain.model.ThemeMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeResolutionTest {

    @Test
    fun `system mode follows the system palette`() {
        assertEquals(true, shouldUseBlackPalette(ThemeMode.SYSTEM, isSystemInDarkTheme = true))
        assertEquals(false, shouldUseBlackPalette(ThemeMode.SYSTEM, isSystemInDarkTheme = false))
    }

    @Test
    fun `explicit modes ignore the system palette`() {
        assertEquals(false, shouldUseBlackPalette(ThemeMode.WHITE, isSystemInDarkTheme = true))
        assertEquals(false, shouldUseBlackPalette(ThemeMode.WHITE, isSystemInDarkTheme = false))
        assertEquals(true, shouldUseBlackPalette(ThemeMode.BLACK, isSystemInDarkTheme = true))
        assertEquals(true, shouldUseBlackPalette(ThemeMode.BLACK, isSystemInDarkTheme = false))
    }

    @Test
    fun `both palettes are strictly monochrome`() {
        listOf("White" to WhiteColorScheme, "Black" to BlackColorScheme).forEach { (name, scheme) ->
            scheme.namedRoles().forEach { (role, color) ->
                assertTrue(
                    color.red == color.green && color.green == color.blue,
                    "$name palette role $role is not monochrome: $color",
                )
            }
        }
    }

    private fun ColorScheme.namedRoles(): List<Pair<String, Color>> = listOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "inversePrimary" to inversePrimary,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer,
        "onTertiaryContainer" to onTertiaryContainer,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "scrim" to scrim,
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
    )
}
