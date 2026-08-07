package app.kcal.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.kcal.KcalApp
import app.kcal.MainUiState
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.feature.history.HistoryScreen
import app.kcal.feature.profile.ProfileFormUiState
import app.kcal.feature.profile.ProfileSetupScreen
import app.kcal.feature.profile.emptyProfileFormUiState
import app.kcal.feature.profile.filledProfileFormUiState
import app.kcal.feature.profile.guardedProfileFormUiState
import app.kcal.feature.profile.invalidProfileFormUiState
import app.kcal.feature.profile.russianProfileFormUiState
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
 * White/Black visual regression for every screen, plus Russian text-heavy states for the
 * screens that carry most of the wording.
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
    fun navHostWhite() = capture(ThemeMode.WHITE) { KcalNavHost() }

    @Test
    fun navHostBlack() = capture(ThemeMode.BLACK) { KcalNavHost() }

    @Test
    fun appLoadingWhite() = capture(ThemeMode.WHITE) { KcalApp(uiState = MainUiState()) }

    @Test
    fun appLoadingBlack() = capture(ThemeMode.BLACK) { KcalApp(uiState = MainUiState()) }

    @Test
    fun appStartupErrorWhite() =
        capture(ThemeMode.WHITE) { KcalApp(uiState = MainUiState(isLoading = false, startupFailed = true)) }

    @Test
    fun appStartupErrorBlack() =
        capture(ThemeMode.BLACK) { KcalApp(uiState = MainUiState(isLoading = false, startupFailed = true)) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupEmptyWhite() = capture(ThemeMode.WHITE) { ProfileSetupFixture(emptyProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupEmptyBlack() = capture(ThemeMode.BLACK) { ProfileSetupFixture(emptyProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupFilledWhite() = capture(ThemeMode.WHITE) { ProfileSetupFixture(filledProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupGuardrailBlack() = capture(ThemeMode.BLACK) { ProfileSetupFixture(guardedProfileFormUiState) }

    @Test
    @Config(qualifiers = "+ru-h2000dp")
    fun profileSetupGuardrailWhiteRussian() = capture(ThemeMode.WHITE) {
        ProfileSetupFixture(russianProfileFormUiState)
    }

    @Test
    @Config(qualifiers = "+ru")
    fun appStartupErrorWhiteRussian() =
        capture(ThemeMode.WHITE) { KcalApp(uiState = MainUiState(isLoading = false, startupFailed = true)) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupErrorsWhite() = capture(ThemeMode.WHITE) { ProfileSetupFixture(invalidProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupErrorsBlack() = capture(ThemeMode.BLACK) { ProfileSetupFixture(invalidProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2600dp")
    fun settingsWhite() = capture(ThemeMode.WHITE) { SettingsFixture(filledProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2600dp")
    fun settingsBlack() = capture(ThemeMode.BLACK) { SettingsFixture(filledProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2600dp")
    fun settingsImperialWhite() = capture(ThemeMode.WHITE) {
        SettingsFixture(
            filledProfileFormUiState.copy(
                unitSystem = UnitSystem.IMPERIAL,
                fields =
                filledProfileFormUiState.fields.copy(
                    currentWeight = "181.7",
                    heightFeet = "5",
                    heightInches = "9.3",
                ),
            ),
        )
    }

    @Test
    @Config(qualifiers = "+ru-h2600dp")
    fun settingsWhiteRussian() = capture(ThemeMode.WHITE) { SettingsFixture(russianProfileFormUiState) }

    @Composable
    private fun ProfileSetupFixture(uiState: ProfileFormUiState) {
        ProfileSetupScreen(
            uiState = uiState,
            onCurrentWeightChange = {},
            onHeightChange = {},
            onHeightFeetChange = {},
            onHeightInchesChange = {},
            onAgeChange = {},
            onFormulaVariantSelect = {},
            onActivityLevelSelect = {},
            onTargetWeightChange = {},
            onLossPaceSelect = {},
            onUnitSystemSelect = {},
            onSave = {},
        )
    }

    @Composable
    private fun SettingsFixture(uiState: ProfileFormUiState) {
        SettingsScreen(
            uiState = uiState,
            onBackClick = {},
            onCurrentWeightChange = {},
            onHeightChange = {},
            onHeightFeetChange = {},
            onHeightInchesChange = {},
            onAgeChange = {},
            onFormulaVariantSelect = {},
            onActivityLevelSelect = {},
            onTargetWeightChange = {},
            onLossPaceSelect = {},
            onUnitSystemSelect = {},
            onAppLanguageSelect = {},
            onThemeModeSelect = {},
            onSave = {},
        )
    }

    private fun capture(themeMode: ThemeMode, content: @Composable () -> Unit) {
        composeRule.setContent {
            KcalTheme(themeMode = themeMode) { content() }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
