package app.kcal.core.designsystem

import androidx.compose.ui.unit.dp

/** The single source of spacing values; screens never hardcode dp literals. */
object KcalSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val extraLarge = 32.dp

    /** Minimum interactive size required for accessibility. */
    val minTouchTarget = 48.dp
}
