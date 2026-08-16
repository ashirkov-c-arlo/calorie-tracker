package app.kcal.feature.trends

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.R
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h800dp-xxhdpi")
class TrendsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** A logged day is edited by tapping its row, which must reach the editor at the top. */
    @Test
    fun `tapping a logged day selects it and brings the editor into view`() {
        var uiState by mutableStateOf(trendsManyPointsPreviewState)
        val clicked = mutableListOf<LocalDate>()
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                TrendsScreen(
                    uiState = uiState,
                    onWeightChange = {},
                    onSave = {},
                    onEntryClick = { date ->
                        clicked += date
                        uiState = uiState.copy(editedDate = date, isEditingToday = false)
                    },
                    onDeleteEntry = {},
                    onLogTodayClick = {},
                    onRetry = {},
                )
            }
        }
        val target = uiState.points.first().localDate

        composeRule.onNodeWithText(mediumDate(target)).performScrollTo().performClick()

        assertEquals(listOf(target), clicked)
        // The editor scrolled back into view and the selected row is marked as such.
        composeRule.onNodeWithText(fullDate(target)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.trends_log_today)).assertIsDisplayed()
        composeRule.onNode(hasText(mediumDate(target))).assertIsSelected()
    }

    /** Tapping the day that is already selected must still take the user to the editor. */
    @Test
    fun `tapping the already selected day still brings the editor into view`() {
        val uiState = trendsManyPointsPreviewState
        var clicks = 0
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                TrendsScreen(
                    uiState = uiState,
                    onWeightChange = {},
                    onSave = {},
                    onEntryClick = { clicks++ },
                    onDeleteEntry = {},
                    onLogTodayClick = {},
                    onRetry = {},
                )
            }
        }
        val selected = uiState.points.last().localDate
        assertEquals(selected, uiState.editedDate)
        val editorHeader = composeRule.onNodeWithText(fullDate(selected))

        composeRule.onNodeWithText(mediumDate(uiState.points.first().localDate)).performScrollTo()
        editorHeader.assertIsNotDisplayed()
        composeRule.onNodeWithText(mediumDate(selected)).performScrollTo().performClick()

        assertEquals(1, clicks)
        editorHeader.assertIsDisplayed()
    }

    /** A localized date already carries its own punctuation, so the message must not double it. */
    @Test
    @Config(qualifiers = "+ru")
    fun `the russian save failure snackbar names the date without doubling its period`() {
        val locale = Locale.forLanguageTag("ru")
        val failedDate = LocalDate.of(2026, 3, 15)
        val message = saveFailedMessage(
            template = composeRule.activity.getString(R.string.trends_save_failed_for_date),
            localDate = failedDate,
            locale = locale,
        )
        val snackbarHostState = SnackbarHostState()
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                TrendsScreen(
                    uiState = trendsManyPointsPreviewState,
                    onWeightChange = {},
                    onSave = {},
                    onEntryClick = {},
                    onDeleteEntry = {},
                    onLogTodayClick = {},
                    onRetry = {},
                    snackbarHostState = snackbarHostState,
                )
            }
            LaunchedEffect(message) { snackbarHostState.showSnackbar(message) }
        }

        assertFalse(message.contains(".."), message)
        assertTrue(message.contains("вес"), message)
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    private fun mediumDate(localDate: LocalDate): String = format(FormatStyle.MEDIUM, localDate)

    private fun fullDate(localDate: LocalDate): String = format(FormatStyle.FULL, localDate)

    private fun format(style: FormatStyle, localDate: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(style).withLocale(Locale.forLanguageTag("en-US")).format(localDate)
}
