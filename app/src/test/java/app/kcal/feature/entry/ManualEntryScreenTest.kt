package app.kcal.feature.entry

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.R
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class ManualEntryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `saving disables cancel controls and consumes system back`() {
        var uiState by mutableStateOf(manualEntryContentPreviewState.copy(isSaving = true))
        var closeCount = 0
        var fallbackBackCount = 0
        composeRule.activity.onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    fallbackBackCount++
                }
            },
        )
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                ManualEntryScreen(
                    uiState = uiState,
                    onBackClick = { closeCount++ },
                    onSummaryChange = {},
                    onItemChange = { _, _, _ -> },
                    onAddItem = {},
                    onRemoveItem = {},
                    onSave = {},
                    onRetry = {},
                )
            }
        }

        val cancel = composeRule.activity.getString(R.string.action_cancel)
        composeRule.onNodeWithContentDescription(cancel).assertIsNotEnabled()
        composeRule.onNodeWithText(cancel).assertIsNotEnabled()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(0, closeCount)
        assertEquals(0, fallbackBackCount)

        composeRule.runOnIdle { uiState = uiState.copy(isSaving = false) }
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, fallbackBackCount)
    }
}
