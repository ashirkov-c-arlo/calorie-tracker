package app.kcal.feature.entry

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.R
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import app.kcal.llm.FailureReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class EntryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `idle shows the hint and the parse action`() {
        show(EntryUiState())

        composeRule.onNodeWithText(string(R.string.entry_text_hint)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_parse)).assertIsEnabled()
        composeRule.onNodeWithText(string(R.string.action_log_manually)).assertIsEnabled()
    }

    @Test
    fun `parsing replaces the action with progress and disables closing`() {
        show(entryParsingPreviewState)

        composeRule.onNodeWithText(string(R.string.entry_parsing)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.action_cancel)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.action_log_manually)).assertIsNotEnabled()
    }

    @Test
    fun `a failure explains itself and offers retry plus manual logging`() {
        var retries = 0
        show(entryFailurePreviewState, onRetry = { retries++ })

        composeRule.onNodeWithText(string(R.string.entry_failure_no_network)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).performClick()
        composeRule.onNodeWithText(string(R.string.action_log_manually)).assertIsEnabled()
        assertEquals(1, retries)
    }

    @Test
    fun `every failure reason renders a localized message`() {
        show(entryIdlePreviewState)

        FailureReason.entries.forEach { reason ->
            composeRule.runOnIdle { state = entryIdlePreviewState.copy(failure = reason) }
            composeRule.onNodeWithText(string(R.string.action_retry)).assertIsDisplayed()
        }
    }

    @Test
    fun `an unanswered clarification cannot be sent`() {
        show(entryClarificationPreviewState.copy(clarificationAnswer = ""))

        composeRule.onNodeWithText("Approximately how large was the serving?").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_send_answer)).assertIsNotEnabled()
    }

    @Test
    fun `the confirmation sheet shows the note, the review warning and the confirm action`() {
        show(entryConfirmationPreviewState)

        composeRule.onNodeWithText(string(R.string.entry_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Weights are estimated from a typical serving.").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.manual_entry_review_warning)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_confirm)).assertIsEnabled()
    }

    private var state by mutableStateOf(EntryUiState())

    private fun show(uiState: EntryUiState, onRetry: () -> Unit = {}) {
        state = uiState
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                EntryScreen(
                    uiState = state,
                    onBackClick = {},
                    onLogManually = {},
                    onTextChange = {},
                    onParse = {},
                    onClarificationAnswerChange = {},
                    onSubmitClarification = {},
                    onRetry = onRetry,
                    onItemChange = { _, _, _ -> },
                    onAddItem = {},
                    onRemoveItem = {},
                    onDismissConfirmation = {},
                    onConfirm = {},
                )
            }
        }
    }

    private fun string(resId: Int): String = composeRule.activity.getString(resId)
}
