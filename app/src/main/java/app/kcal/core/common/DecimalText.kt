package app.kcal.core.common

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Locale-aware decimal input and display. Parsing accepts the locale separator and the
 * alternate `.`/`,` separator, plus the grouping spaces some locales insert on paste.
 */
object DecimalText {

    fun parse(text: String): Double? {
        val compact = text.filterNot { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }
        if (compact.isEmpty()) return null
        if (compact.contains(',') && compact.contains('.')) return null
        val normalized = compact.replace(',', '.')
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun parseInt(text: String): Int? = text.filterNot { it.isWhitespace() }.takeIf { it.isNotEmpty() }?.toIntOrNull()

    fun format(value: Double, locale: Locale, decimals: Int = 1): String =
        String.format(locale, "%.${decimals}f", value)

    fun format(value: BigDecimal, locale: Locale, decimals: Int = 1): String =
        String.format(locale, "%.${decimals}f", value)

    /** Shortest locale-aware representation that parses back to the exact same Double. */
    fun formatEditable(value: Double, locale: Locale): String {
        val decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return if (decimalSeparator == '.') value.toString() else value.toString().replace('.', decimalSeparator)
    }

    fun formatInt(value: Int, locale: Locale): String = value.toString()

    fun formatLong(value: Long, locale: Locale): String = value.toString()
}
