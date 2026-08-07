package app.kcal.core.designsystem

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Scrims for the API levels that cannot draw a fully transparent navigation bar (26-28).
 * Both values are monochrome, like the rest of the palette.
 */
internal const val LIGHT_SYSTEM_BAR_SCRIM: Int = 0xE6FFFFFF.toInt()
internal const val DARK_SYSTEM_BAR_SCRIM: Int = 0x80000000.toInt()

/**
 * Applies edge-to-edge using the resolved palette instead of the system dark mode. This
 * keeps the pre-API 29 navigation bar scrim and the bar icons consistent when the user
 * picks a palette that contradicts the system theme.
 */
fun ComponentActivity.applyKcalSystemBars(useBlackPalette: Boolean) {
    enableEdgeToEdge(
        statusBarStyle =
        SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
            detectDarkMode = { useBlackPalette },
        ),
        navigationBarStyle =
        SystemBarStyle.auto(
            lightScrim = LIGHT_SYSTEM_BAR_SCRIM,
            darkScrim = DARK_SYSTEM_BAR_SCRIM,
            detectDarkMode = { useBlackPalette },
        ),
    )
}
