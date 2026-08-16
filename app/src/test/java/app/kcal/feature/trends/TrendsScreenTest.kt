package app.kcal.feature.trends

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h800dp-xxhdpi")
class TrendsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `tapping the chart card fires onChartClick`() {
        var clicks = 0
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                TrendsScreen(
                    uiState = trendsManyPointsPreviewState,
                    onChartClick = { clicks++ },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Weight and 7-day average", substring = true).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `empty state shows message`() {
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                TrendsScreen(
                    uiState = trendsEmptyPreviewState,
                    onChartClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Save your weight", substring = true).assertIsDisplayed()
    }
}
