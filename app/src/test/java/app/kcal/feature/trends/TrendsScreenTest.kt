package app.kcal.feature.trends

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getBy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h800dp-xxhdpi")
class TrendsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** A logged day is selected by tapping its row. */
    @Test
    fun `tapping a logged day selects it`() {
        var uiState by mutableStateOf(trendsManyPointsPreviewState)
        val clicked = mutableListOf<LocalDate>()
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                TrendsScreen(
                    uiState = uiState,
                    onEntryClick = { date ->
                        clicked += date
                        uiState = uiState.copy(editedDate = date)
                    },
                    onDeleteEntry = {},
                    onRetry = {},
                )
            }
        }
        val target = uiState.points.first().localDate

        composeRule.onNodeWithText(mediumDate(target)).performScrollTo().performClick()

        assertEquals(listOf(target), clicked)
    }

    /** Tapping the day that is already selected must still work. */
    @Test
    fun `tapping the already selected day still registers the click`() {
        val uiState = trendsManyPointsPreviewState
        var clicks = 0
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                TrendsScreen(
                    uiState = uiState,
                    onEntryClick = { clicks++ },
                    onDeleteEntry = {},
                    onRetry = {},
                )
            }
        }
        val selected = uiState.points.last().localDate

        composeRule.onNodeWithText(mediumDate(uiState.points.first().localDate)).performScrollTo()
        composeRule.onNodeWithText(mediumDate(selected)).performScrollTo().performClick()

        assertEquals(1, clicks)
    }

    private fun mediumDate(localDate: LocalDate): String = format(FormatStyle.MEDIUM, localDate)

    private fun format(style: FormatStyle, localDate: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(style).withLocale(Locale.forLanguageTag("en-US")).format(localDate)
}
