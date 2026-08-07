package app.kcal.feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

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

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resId)
}
