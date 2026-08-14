package app.kcal.feature.entry

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
        composeRule.onNodeWithText(string(R.string.action_add_item)).assertIsEnabled()
        composeRule.onNodeWithText(string(R.string.action_log_manually)).assertIsEnabled()
    }

    @Test
    fun `the only item cannot be removed`() {
        show(EntryUiState())

        composeRule.onNodeWithContentDescription(string(R.string.action_remove_item)).assertIsNotEnabled()
    }

    @Test
    fun `each described item gets its own card and remove action`() {
        var removed: Long? = null
        show(entryMultipleInputsPreviewState, onRemoveInput = { removed = it })

        composeRule.onNodeWithText(string(R.string.manual_entry_item_number, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.entry_item_parsed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.entry_failure_timeout)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(string(R.string.action_remove_item))[1].performClick()

        assertEquals(entryMultipleInputsPreviewState.inputs.last().key, removed)
    }

    @Test
    fun `adding an item is offered next to the parse action`() {
        var added = 0
        show(entryIdlePreviewState, onAddInput = { added++ })

        composeRule.onNodeWithText(string(R.string.action_add_item)).performClick()

        assertEquals(1, added)
    }

    @Test
    fun `parsing shows progress on the item and disables every action`() {
        show(entryParsingPreviewState)

        composeRule.onNodeWithText(string(R.string.entry_parsing)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.action_cancel)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.action_parse)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.action_add_item)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.action_log_manually)).assertIsNotEnabled()
    }

    @Test
    fun `a failure explains itself and offers retry plus manual logging`() {
        var retried: Long? = null
        show(entryFailurePreviewState, onRetry = { retried = it })

        composeRule.onNodeWithText(string(R.string.entry_failure_no_network)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).performClick()
        composeRule.onNodeWithText(string(R.string.action_log_manually)).assertIsEnabled()
        assertEquals(entryFailurePreviewState.inputs.single().key, retried)
    }

    @Test
    fun `every failure reason renders a localized message`() {
        show(entryIdlePreviewState)

        FailureReason.entries.forEach { reason ->
            composeRule.runOnIdle { state = entryIdlePreviewState.withFirstInput { it.copy(failure = reason) } }
            composeRule.onNodeWithText(string(R.string.action_retry)).assertIsDisplayed()
        }
    }

    @Test
    fun `an unanswered clarification cannot be sent`() {
        show(entryClarificationPreviewState.withFirstInput { it.copy(clarificationAnswer = "") })

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

    @Test
    fun `both photo sources are offered for the item that asked for them`() {
        var picked: Long? = null
        var captured: Long? = null
        show(entryIdlePreviewState, onPickPhoto = { picked = it }, onTakePhoto = { captured = it })

        composeRule.onNodeWithText(string(R.string.entry_photo_add)).performClick()
        composeRule.onNodeWithText(string(R.string.entry_photo_take)).performClick()

        val key = entryIdlePreviewState.inputs.single().key
        assertEquals(key, picked)
        assertEquals(key, captured)
    }

    @Test
    fun `a device without a camera only offers the photo picker`() {
        show(entryIdlePreviewState, canTakePhoto = false)

        composeRule.onNodeWithText(string(R.string.entry_photo_add)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.entry_photo_take)).assertDoesNotExist()
    }

    @Test
    fun `an attached photo says it is not saved and can be removed`() {
        var removed: Long? = null
        show(entryPhotoAttachedPreviewState, onRemovePhoto = { removed = it })

        composeRule.onNodeWithText(string(R.string.entry_photo_attached)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.entry_photo_remove)).performClick()

        assertEquals(entryPhotoAttachedPreviewState.inputs.single().key, removed)
    }

    @Test
    fun `preparing a photo blocks parsing until it is ready`() {
        show(entryPhotoPreparingPreviewState)

        composeRule.onNodeWithText(string(R.string.entry_photo_preparing)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_parse)).assertIsNotEnabled()
    }

    @Test
    fun `an unreadable image explains itself and keeps the photo actions available`() {
        show(entryPhotoFailedPreviewState)

        composeRule.onNodeWithText(string(R.string.entry_photo_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.entry_photo_add)).assertIsEnabled()
    }

    private var state by mutableStateOf(EntryUiState())

    private fun show(
        uiState: EntryUiState,
        onAddInput: () -> Unit = {},
        onRemoveInput: (Long) -> Unit = {},
        onRetry: (Long) -> Unit = {},
        canTakePhoto: Boolean = true,
        onPickPhoto: (Long) -> Unit = {},
        onTakePhoto: (Long) -> Unit = {},
        onRemovePhoto: (Long) -> Unit = {},
    ) {
        state = uiState
        composeRule.setContent {
            KcalTheme(themeMode = ThemeMode.WHITE) {
                EntryScreen(
                    uiState = state,
                    onBackClick = {},
                    onLogManually = {},
                    onTextChange = { _, _ -> },
                    onAddInput = onAddInput,
                    onRemoveInput = onRemoveInput,
                    onParse = {},
                    onClarificationAnswerChange = { _, _ -> },
                    onSubmitClarification = {},
                    onRetry = onRetry,
                    onSummaryChange = {},
                    onItemChange = { _, _, _ -> },
                    onAddItem = {},
                    onRemoveItem = {},
                    onDismissConfirmation = {},
                    onConfirm = {},
                    canTakePhoto = canTakePhoto,
                    onPickPhoto = onPickPhoto,
                    onTakePhoto = onTakePhoto,
                    onRemovePhoto = onRemovePhoto,
                )
            }
        }
    }

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    private fun string(resId: Int, vararg formatArgs: Any): String = composeRule.activity.getString(resId, *formatArgs)
}

private fun EntryUiState.withFirstInput(transform: (EntryInputUiState) -> EntryInputUiState): EntryUiState =
    copy(inputs = inputs.replacingAt(0, transform(inputs.first())))
