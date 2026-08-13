package app.kcal.core.common

import org.junit.Test
import java.math.BigDecimal
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecimalTextTest {

    @Test
    fun `both decimal separators are accepted`() {
        assertEquals(82.4, DecimalText.parse("82.4"))
        assertEquals(82.4, DecimalText.parse("82,4"))
        assertEquals(0.5, DecimalText.parse("0,5"))
        assertEquals(5.0, DecimalText.parse("5"))
    }

    @Test
    fun `grouping whitespace from pasted values is ignored`() {
        assertEquals(1234.5, DecimalText.parse("1 234,5"))
        assertEquals(1234.5, DecimalText.parse("1\u00A0234.5"))
    }

    @Test
    fun `ambiguous and invalid input is rejected instead of guessed`() {
        assertNull(DecimalText.parse(""))
        assertNull(DecimalText.parse("   "))
        assertNull(DecimalText.parse("1.234,5"))
        assertNull(DecimalText.parse("8,2,4"))
        assertNull(DecimalText.parse("abc"))
        assertNull(DecimalText.parse("82kg"))
        assertNull(DecimalText.parse("NaN"))
        assertNull(DecimalText.parse("Infinity"))
    }

    @Test
    fun `integers are parsed strictly`() {
        assertEquals(34, DecimalText.parseInt("34"))
        assertNull(DecimalText.parseInt("34.5"))
        assertNull(DecimalText.parseInt(""))
        assertNull(DecimalText.parseInt("x"))
    }

    @Test
    fun `formatting follows the given locale`() {
        assertEquals("82.4", DecimalText.format(82.4, Locale.US))
        assertEquals("82,4", DecimalText.format(82.4, Locale.forLanguageTag("ru")))
        assertEquals("0,50", DecimalText.format(0.5, Locale.forLanguageTag("ru"), decimals = 2))
        assertEquals("2089", DecimalText.formatInt(2089, Locale.forLanguageTag("ru")))
    }

    @Test
    fun `editable formatting round trips every Double without rounding`() {
        listOf(Locale.US, Locale.forLanguageTag("ru")).forEach { locale ->
            listOf(12.55, 0.04, 100.05, 1.0e-12).forEach { value ->
                val parsed = DecimalText.parse(DecimalText.formatEditable(value, locale))
                assertEquals(value.toBits(), parsed?.toBits(), "lossless edit of $value in $locale")
            }
        }
        assertEquals("12,55", DecimalText.formatEditable(12.55, Locale.forLanguageTag("ru")))
    }

    @Test
    fun `wide totals format without narrowing`() {
        assertEquals("2147483648", DecimalText.formatLong(2_147_483_648L, Locale.US))
        assertEquals("12,6", DecimalText.format(BigDecimal("12.55"), Locale.forLanguageTag("ru")))
    }

    @Test
    fun `formatting and parsing round trip in both locales`() {
        listOf(Locale.US, Locale.forLanguageTag("ru")).forEach { locale ->
            listOf(20.0, 82.4, 176.5, 0.25).forEach { value ->
                val text = DecimalText.format(value, locale, decimals = 2)
                assertEquals(value, DecimalText.parse(text), "round trip of $value in $locale")
            }
        }
    }
}
