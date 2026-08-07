package app.kcal.core.designsystem

import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * On API 26-28 the navigation bar cannot be transparent, so its scrim must follow the
 * selected palette. Otherwise a palette that contradicts the system theme produces
 * light icons on a light scrim.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarsTest {

    @Test
    @Config(sdk = [26, 28])
    fun `black palette on a light system theme keeps a dark navigation bar scrim`() {
        val activity = launchActivity()

        activity.applyKcalSystemBars(useBlackPalette = true)

        assertEquals(DARK_SYSTEM_BAR_SCRIM, navigationBarColor(activity))
    }

    @Test
    @Config(sdk = [26, 28], qualifiers = "night")
    fun `white palette on a dark system theme keeps a light navigation bar scrim`() {
        val activity = launchActivity()

        activity.applyKcalSystemBars(useBlackPalette = false)

        assertEquals(LIGHT_SYSTEM_BAR_SCRIM, navigationBarColor(activity))
    }

    @Test
    @Config(sdk = [28])
    fun `bar icons follow the selected palette and not the system theme`() {
        val activity = launchActivity()

        activity.applyKcalSystemBars(useBlackPalette = true)
        with(insetsController(activity)) {
            assertFalse(isAppearanceLightStatusBars)
            assertFalse(isAppearanceLightNavigationBars)
        }

        activity.applyKcalSystemBars(useBlackPalette = false)
        with(insetsController(activity)) {
            assertTrue(isAppearanceLightStatusBars)
            assertTrue(isAppearanceLightNavigationBars)
        }
    }

    private fun launchActivity(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    // The scrim is exactly what `enableEdgeToEdge` sets through this deprecated window
    // property on the API levels that cannot draw a transparent navigation bar.
    @Suppress("DEPRECATION")
    private fun navigationBarColor(activity: ComponentActivity): Int = activity.window.navigationBarColor

    private fun insetsController(activity: ComponentActivity) =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
}
