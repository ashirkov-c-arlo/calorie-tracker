package app.kcal.feature.entry

import app.kcal.llm.FailureReason
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * One described item on its way to the parser. Every item is its own request, so it carries its
 * own text, its own optional transient photo, and its own outcome: the proxy accepts one image
 * per request and a clarification belongs to the text it was asked about.
 */
data class EntryInputUiState(
    val key: Long,
    val text: String = "",
    val textMissing: Boolean = false,
    val photoPath: String? = null,
    val isAttachingPhoto: Boolean = false,
    val photoFailed: Boolean = false,
    val isParsing: Boolean = false,
    val isParsed: Boolean = false,
    val failure: FailureReason? = null,
    val clarificationQuestion: String? = null,
    val clarificationAnswer: String = "",
)

/**
 * The described items and the confirmation that follows their parses. [items] is only filled once
 * every input succeeded, and [isConfirming] is what shows the editable sheet: nothing is persisted
 * before the user confirms it there. Every [EntryInputUiState.photoPath] points at a cache file
 * that lives only as long as this flow needs it and is never stored with the meal.
 */
data class EntryUiState(
    val inputs: PersistentList<EntryInputUiState> = persistentListOf(EntryInputUiState(key = FIRST_ITEM_KEY)),
    val isConfirming: Boolean = false,
    val note: String? = null,
    val items: PersistentList<MealItemUiState> = persistentListOf(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val isParsing: Boolean
        get() = inputs.any { it.isParsing }

    val canSubmit: Boolean
        get() = !isParsing && !isSaving && inputs.none { it.isAttachingPhoto }
}

sealed interface EntryEvent {
    data object Saved : EntryEvent
}

private val typedInput = EntryInputUiState(key = FIRST_ITEM_KEY, text = "омлет из трёх яиц и кофе с молоком")

private fun entryState(vararg inputs: EntryInputUiState) = EntryUiState(inputs = persistentListOf(*inputs))

internal val entryIdlePreviewState = entryState(typedInput)

internal val entryParsingPreviewState = entryState(typedInput.copy(isParsing = true))

internal val entryFailurePreviewState = entryState(typedInput.copy(failure = FailureReason.NO_NETWORK))

internal val entryPhotoAttachedPreviewState =
    entryState(typedInput.copy(text = "это на тарелке", photoPath = "/cache/entry-photos/meal.jpg"))

internal val entryPhotoPreparingPreviewState = entryState(typedInput.copy(isAttachingPhoto = true))

internal val entryPhotoFailedPreviewState = entryState(typedInput.copy(photoFailed = true))

internal val entryClarificationPreviewState = entryState(
    typedInput.copy(
        clarificationQuestion = "Approximately how large was the serving?",
        clarificationAnswer = "About 250 grams",
    ),
)

/** Several items at once: one already read, one still waiting for its own retry. */
internal val entryMultipleInputsPreviewState = entryState(
    typedInput.copy(text = "омлет из трёх яиц", isParsed = true),
    EntryInputUiState(
        key = FIRST_ITEM_KEY + 1,
        text = "это на тарелке",
        photoPath = "/cache/entry-photos/plate.jpg",
        failure = FailureReason.TIMEOUT,
    ),
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

/**
 * What the Russian interface really shows: proxy text in Russian and decimals formatted with
 * the locale separator.
 */
internal val entryConfirmationRussianPreviewState = entryConfirmationPreviewState.copy(
    note = "Вес порций оценён по типичной подаче.",
    items =
    persistentListOf(
        MealItemUiState(
            key = 1,
            name = "Омлет из трёх яиц",
            grams = "180",
            kcal = "297",
            protein = "19,8",
            fat = "22,5",
            carbs = "1,2",
            confidence = 0.82f,
        ),
        MealItemUiState(
            key = 2,
            name = "Кофе с молоком",
            grams = "200",
            kcal = "63",
            protein = "3,3",
            fat = "3,4",
            carbs = "4,8",
            confidence = 0.74f,
            needsReview = true,
        ),
    ),
)
