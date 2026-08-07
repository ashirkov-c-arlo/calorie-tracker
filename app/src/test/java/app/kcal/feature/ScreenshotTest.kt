package app.kcal.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.kcal.KcalApp
import app.kcal.MainUiState
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import app.kcal.feature.history.HistoryScreen
import app.kcal.feature.profile.ProfileSetupScreen
import app.kcal.feature.settings.SettingsScreen
import app.kcal.feature.today.TodayScreen
import app.kcal.feature.trends.TrendsScreen
import app.kcal.navigation.KcalNavHost
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * White/Black visual regression for every stage 1 screen plus one Russian text-heavy state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayWhite() = capture(ThemeMode.WHITE) { TodayScreen(onSettingsClick = {}) }

    @Test
    fun todayBlack() = capture(ThemeMode.BLACK) { TodayScreen(onSettingsClick = {}) }

    @Test
    @Config(qualifiers = "+ru")
    fun todayWhiteRussian() = capture(ThemeMode.WHITE) { TodayScreen(onSettingsClick = {}) }

    @Test
    fun trendsWhite() = capture(ThemeMode.WHITE) { TrendsScreen() }

    @Test
    fun trendsBlack() = capture(ThemeMode.BLACK) { TrendsScreen() }

    @Test
    fun historyWhite() = capture(ThemeMode.WHITE) { HistoryScreen() }

    @Test
    fun historyBlack() = capture(ThemeMode.BLACK) { HistoryScreen() }

    @Test
    fun settingsWhite() = capture(ThemeMode.WHITE) { SettingsScreen(onBackClick = {}) }

    @Test
    fun settingsBlack() = capture(ThemeMode.BLACK) { SettingsScreen(onBackClick = {}) }

    @Test
    fun profileSetupWhite() = capture(ThemeMode.WHITE) { ProfileSetupScreen() }

    @Test
    fun profileSetupBlack() = capture(ThemeMode.BLACK) { ProfileSetupScreen() }

    @Test
    fun appLoadingWhite() = capture(ThemeMode.WHITE) { KcalApp(uiState = MainUiState()) }

    @Test
    fun navHostWhite() = capture(ThemeMode.WHITE) { KcalNavHost() }

    @Test
    fun navHostBlack() = capture(ThemeMode.BLACK) { KcalNavHost() }

    private fun capture(themeMode: ThemeMode, content: @Composable () -> Unit) {
        composeRule.setContent {
            KcalTheme(themeMode = themeMode) { content() }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
