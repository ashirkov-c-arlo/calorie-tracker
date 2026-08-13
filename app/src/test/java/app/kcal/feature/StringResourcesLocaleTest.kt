package app.kcal.feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The interface language follows the system when it is Russian or English and falls back to
 * English for anything else.
 */
@RunWith(AndroidJUnit4::class)
class StringResourcesLocaleTest {

    @Test
    @Config(qualifiers = "en")
    fun `english system language uses the default resources`() {
        assertEquals("Today", string(R.string.nav_today))
    }

    @Test
    @Config(qualifiers = "ru")
    fun `russian system language uses the russian resources`() {
        assertEquals("Сегодня", string(R.string.nav_today))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `unsupported system language falls back to english`() {
        assertEquals("Today", string(R.string.nav_today))
    }

    /**
     * A Russian date in `FormatStyle.MEDIUM` ends with `г.`, so messages that end with the date
     * must not add punctuation of their own.
     */
    @Test
    @Config(qualifiers = "ru")
    fun `russian messages do not double the period a localized date ends with`() {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.forLanguageTag("ru"))
            .format(LocalDate.of(2026, 3, 15))

        listOf(
            string(R.string.trends_save_failed_for_date, date),
            string(R.string.trends_chart_content_description, date, date),
        ).forEach { message -> assertFalse(message.contains(".."), message) }
    }

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    private fun string(resId: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(resId, *args)
}
