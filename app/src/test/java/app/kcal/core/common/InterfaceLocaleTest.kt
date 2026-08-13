package app.kcal.core.common

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

/**
 * Resources, the locale config, and the proxy contract support Russian and English only, so
 * this resolution is the single place that decides which of the two applies.
 */
class InterfaceLocaleTest {

    @Test
    fun `russian variants resolve to russian`() {
        listOf("ru", "ru-RU", "ru-BY").forEach { tag ->
            assertEquals("ru", interfaceLocale(Locale.forLanguageTag(tag)).language, tag)
        }
    }

    @Test
    fun `english and every unsupported language resolve to english`() {
        listOf("en", "en-GB", "fr", "de", "kk", "zh-Hans-CN", "").forEach { tag ->
            assertEquals("en", interfaceLocale(Locale.forLanguageTag(tag)).language, tag)
        }
    }
}
