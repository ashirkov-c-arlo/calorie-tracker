package app.kcal.feature.entry

import app.kcal.llm.FailureReason
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * Text entry and the confirmation that follows a parse. [items] is only filled once the parser
 * succeeded, and [isConfirming] is what shows the editable sheet: nothing is persisted before
 * the user confirms it there.
 */
data class EntryUiState(
    val text: String = "",
    val textMissing: Boolean = false,
    val isParsing: Boolean = false,
    val failure: FailureReason? = null,
    val clarificationQuestion: String? = null,
    val clarificationAnswer: String = "",
    val isConfirming: Boolean = false,
    val note: String? = null,
    val items: PersistentList<MealItemUiState> = persistentListOf(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isParsing && !isSaving
}

sealed interface EntryEvent {
    data object Saved : EntryEvent
}

internal val entryIdlePreviewState = EntryUiState(text = "омлет из трёх яиц и кофе с молоком")

internal val entryParsingPreviewState = entryIdlePreviewState.copy(isParsing = true)

internal val entryFailurePreviewState = entryIdlePreviewState.copy(failure = FailureReason.NO_NETWORK)

internal val entryClarificationPreviewState = entryIdlePreviewState.copy(
    clarificationQuestion = "Approximately how large was the serving?",
    clarificationAnswer = "About 250 grams",
)

internal val entryConfirmationPreviewState = entryIdlePreviewState.copy(
    isConfirming = true,
    note = "Weights are estimated from a typical serving.",
    items =
    persistentListOf(
        MealItemUiState(
            key = 1,
            name = "Omelette",
            grams = "180.0",
            kcal = "297",
            protein = "19.8",
            fat = "22.5",
            carbs = "1.2",
            confidence = 0.82f,
        ),
        MealItemUiState(
            key = 2,
            name = "Coffee with milk",
            grams = "200.0",
            kcal = "63",
            protein = "3.3",
            fat = "3.4",
            carbs = "4.8",
            confidence = 0.74f,
            needsReview = true,
        ),
    ),
)
