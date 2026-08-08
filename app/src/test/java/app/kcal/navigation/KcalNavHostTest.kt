package app.kcal.navigation

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.R
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import app.kcal.feature.profile.filledProfileFormUiState
import app.kcal.feature.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class KcalNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `starts on today with the bottom navigation visible`() {
        showNavHost()

        composeRule.onNodeWithText(string(R.string.placeholder_today)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_trends)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_history)).assertIsDisplayed()
    }

    @Test
    fun `selecting a tab switches the content`() {
        showNavHost()

        composeRule.onNodeWithText(string(R.string.nav_trends)).performClick()
        composeRule.onNodeWithText(string(R.string.placeholder_trends)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.nav_history)).performClick()
        composeRule.onNodeWithText(string(R.string.placeholder_history)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.nav_today)).performClick()
        composeRule.onNodeWithText(string(R.string.placeholder_today)).assertIsDisplayed()
    }

    @Test
    fun `settings opens without the bottom navigation and returns back`() {
        showNavHost()

        composeRule.onNodeWithContentDescription(string(R.string.settings_open_content_description))
            .performClick()

        composeRule.onNodeWithText(string(R.string.settings_section_profile)).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.nav_trends)).assertCountEquals(0)

        composeRule.onNodeWithContentDescription(string(R.string.navigate_back_content_description))
            .performClick()

        composeRule.onNodeWithText(string(R.string.placeholder_today)).assertIsDisplayed()
    }

    /** The settings route resolves a Hilt view model, so the test injects the stateless screen. */
    private fun showNavHost() {
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                KcalNavHost(
                    settingsContent = { onBackClick ->
                        SettingsScreen(
                            uiState = filledProfileFormUiState,
                            onBackClick = onBackClick,
                            onCurrentWeightChange = {},
                            onHeightChange = {},
                            onHeightFeetChange = {},
                            onHeightInchesChange = {},
                            onAgeChange = {},
                            onFormulaVariantSelect = {},
                            onActivityLevelSelect = {},
                            onTargetWeightChange = {},
                            onLossRateChange = {},
                            onUnitSystemSelect = {},
                            onAppLanguageSelect = {},
                            onThemeModeSelect = {},
                            onSave = {},
                        )
                    },
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
