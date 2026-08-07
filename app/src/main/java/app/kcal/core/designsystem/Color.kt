package app.kcal.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The only place where raw color literals are allowed. Both palettes are strictly
 * monochrome: every channel of every value is identical, so no hue can leak into the UI.
 */
private val Black = Color(0xFF000000)
private val Gray08 = Color(0xFF141414)
private val Gray12 = Color(0xFF1F1F1F)
private val Gray20 = Color(0xFF333333)
private val Gray40 = Color(0xFF666666)
private val Gray60 = Color(0xFF999999)
private val Gray80 = Color(0xFFCCCCCC)
private val Gray90 = Color(0xFFE6E6E6)
private val Gray95 = Color(0xFFF2F2F2)
private val White = Color(0xFFFFFFFF)

internal val WhiteColorScheme =
    lightColorScheme(
        primary = Black,
        onPrimary = White,
        primaryContainer = Gray90,
        onPrimaryContainer = Black,
        inversePrimary = White,
        secondary = Gray20,
        onSecondary = White,
        secondaryContainer = Gray95,
        onSecondaryContainer = Black,
        tertiary = Gray40,
        onTertiary = White,
        tertiaryContainer = Gray95,
        onTertiaryContainer = Black,
        background = White,
        onBackground = Black,
        surface = White,
        onSurface = Black,
        surfaceVariant = Gray95,
        onSurfaceVariant = Gray20,
        surfaceTint = Black,
        inverseSurface = Black,
        inverseOnSurface = White,
        error = Gray20,
        onError = White,
        errorContainer = Gray90,
        onErrorContainer = Black,
        outline = Gray60,
        outlineVariant = Gray80,
        scrim = Black,
        surfaceBright = White,
        surfaceDim = Gray90,
        surfaceContainerLowest = White,
        surfaceContainerLow = Gray95,
        surfaceContainer = Gray95,
        surfaceContainerHigh = Gray90,
        surfaceContainerHighest = Gray80,
    )

internal val BlackColorScheme =
    darkColorScheme(
        primary = White,
        onPrimary = Black,
        primaryContainer = Gray20,
        onPrimaryContainer = White,
        inversePrimary = Black,
        secondary = Gray80,
        onSecondary = Black,
        secondaryContainer = Gray12,
        onSecondaryContainer = White,
        tertiary = Gray60,
        onTertiary = Black,
        tertiaryContainer = Gray12,
        onTertiaryContainer = White,
        background = Black,
        onBackground = White,
        surface = Black,
        onSurface = White,
        surfaceVariant = Gray12,
        onSurfaceVariant = Gray80,
        surfaceTint = White,
        inverseSurface = White,
        inverseOnSurface = Black,
        error = Gray80,
        onError = Black,
        errorContainer = Gray20,
        onErrorContainer = White,
        outline = Gray40,
        outlineVariant = Gray20,
        scrim = Black,
        surfaceBright = Gray20,
        surfaceDim = Black,
        surfaceContainerLowest = Black,
        surfaceContainerLow = Gray08,
        surfaceContainer = Gray12,
        surfaceContainerHigh = Gray20,
        surfaceContainerHighest = Gray40,
    )
