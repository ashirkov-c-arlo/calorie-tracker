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

/**
 * Resources, the locale config, and the proxy contract support Russian and English only, so
 * every other system language resolves to English for both formatting and `Accept-Language`.
 */
fun interfaceLocale(locale: Locale): Locale = if (locale.language == RUSSIAN.language) RUSSIAN else Locale.ENGLISH

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")
