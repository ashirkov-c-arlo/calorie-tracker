package app.kcal.core.common

import java.util.Locale

/**
 * The locale currently applied to the interface. AppCompat updates the default locale when
 * the app locales change, so formatting follows the selected interface language. Tests pass
 * an explicit locale instead of depending on the machine default.
 */
fun interface AppLocaleProvider {
    fun current(): Locale
}
