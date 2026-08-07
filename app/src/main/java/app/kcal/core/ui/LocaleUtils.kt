package app.kcal.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** The locale currently applied to the interface, for locale-aware number formatting. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
