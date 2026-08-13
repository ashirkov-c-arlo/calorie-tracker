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
import app.kcal.feature.entry.EntryScreen
import app.kcal.feature.entry.EntryUiState
import app.kcal.feature.entry.ManualEntryScreen
import app.kcal.feature.entry.manualEntryEmptyPreviewState
import app.kcal.feature.history.HistoryScreen
import app.kcal.feature.history.historyContentPreviewState
import app.kcal.feature.profile.filledProfileFormUiState
import app.kcal.feature.settings.SettingsScreen
import app.kcal.feature.today.TodayScreen
import app.kcal.feature.today.todayContentPreviewState
import app.kcal.feature.trends.TrendsScreen
import app.kcal.feature.trends.trendsManyPointsPreviewState
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

        composeRule.onNodeWithText(string(R.string.today_progress_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_trends)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_history)).assertIsDisplayed()
    }

    @Test
    fun `selecting a tab switches the content`() {
        showNavHost()

        composeRule.onNodeWithText(string(R.string.nav_trends)).performClick()
        composeRule.onNodeWithText(string(R.string.trends_log_title)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.nav_history)).performClick()
        composeRule.onNodeWithText(string(R.string.history_day_no_target)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.nav_today)).performClick()
        composeRule.onNodeWithText(string(R.string.today_progress_title)).assertIsDisplayed()
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

        composeRule.onNodeWithText(string(R.string.today_progress_title)).assertIsDisplayed()
    }

    @Test
    fun `add meal opens text entry and logging manually replaces it`() {
        showNavHost()

        composeRule.onNodeWithContentDescription(string(R.string.today_add_meal_content_description))
            .performClick()

        composeRule.onNodeWithText(string(R.string.entry_title)).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.nav_trends)).assertCountEquals(0)

        composeRule.onNodeWithText(string(R.string.action_log_manually)).performClick()
        composeRule.onNodeWithText(string(R.string.manual_entry_add_title)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(string(R.string.action_cancel)).performClick()
        composeRule.onNodeWithText(string(R.string.today_progress_title)).assertIsDisplayed()
    }

    /** Stateful Hilt routes are replaced with their stateless screens in this navigation test. */
    private fun showNavHost() {
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                KcalNavHost(
                    todayContent = { onSettingsClick, onAddMealClick, onEditMealClick ->
                        TodayScreen(
                            uiState = todayContentPreviewState,
                            onSettingsClick = onSettingsClick,
                            onAddMealClick = onAddMealClick,
                            onEditMealClick = onEditMealClick,
                            onDeleteMealClick = {},
                            onRetry = {},
                        )
                    },
                    entryContent = { mealId, onClose ->
                        ManualEntryScreen(
                            uiState = manualEntryEmptyPreviewState.copy(mealId = mealId),
                            onBackClick = onClose,
                            onItemChange = { _, _, _ -> },
                            onAddItem = {},
                            onRemoveItem = {},
                            onSave = {},
                            onRetry = {},
                        )
                    },
                    foodTextContent = { onClose, onLogManually ->
                        EntryScreen(
                            uiState = EntryUiState(),
                            onBackClick = onClose,
                            onLogManually = onLogManually,
                            onTextChange = {},
                            onParse = {},
                            onClarificationAnswerChange = {},
                            onSubmitClarification = {},
                            onRetry = {},
                            onItemChange = { _, _, _ -> },
                            onAddItem = {},
                            onRemoveItem = {},
                            onDismissConfirmation = {},
                            onConfirm = {},
                        )
                    },
                    historyContent = { onEditMealClick ->
                        HistoryScreen(
                            uiState = historyContentPreviewState,
                            onDayClick = {},
                            onEditMealClick = onEditMealClick,
                            onDeleteMealClick = {},
                            onRetry = {},
                        )
                    },
                    trendsContent = {
                        TrendsScreen(
                            uiState = trendsManyPointsPreviewState,
                            onWeightChange = {},
                            onSave = {},
                            onEntryClick = {},
                            onLogTodayClick = {},
                            onRetry = {},
                        )
                    },
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
                            onLossPaceSelect = {},
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
