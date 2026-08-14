package app.kcal.feature.entry

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TransientCapture
import app.kcal.core.common.TransientPhotoStore
import app.kcal.domain.model.EntrySource
import app.kcal.domain.model.FoodItem
import app.kcal.domain.usecase.SaveMeal
import app.kcal.domain.usecase.SaveMealResult
import app.kcal.llm.ClarificationAnswer
import app.kcal.llm.FailureReason
import app.kcal.llm.NutritionParser
import app.kcal.llm.ParseResult
import app.kcal.llm.UserInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Parsing of one or more described items and the single confirmation that follows them. Each item
 * is one request, because the contract carries one text and at most one image, so each keeps its
 * own clarification, failure, and retry. Typed text survives all of them and only an explicit
 * confirmation writes to storage. Photos are transient: they are uploaded, never persisted, and
 * deleted as soon as the flow no longer needs them.
 */
@HiltViewModel
class EntryViewModel @Inject constructor(
    private val nutritionParser: NutritionParser,
    private val saveMeal: SaveMeal,
    private val localeProvider: AppLocaleProvider,
    private val photoStore: TransientPhotoStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EntryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var nextInputKey = FIRST_ITEM_KEY + 1
    private var nextItemKey = FIRST_ITEM_KEY

    /** What each input parsed into, kept until the confirmation merges all of them. */
    private val parsed = mutableMapOf<Long, ParsedInput>()

    /**
     * The capture the camera app is writing to, and the input it belongs to. It is owned here
     * rather than in the composable so that a result that arrives after the screen was recreated
     * still finds its target.
     */
    private var pendingCapture: PendingCapture? = null

    private data class ParsedInput(val items: List<FoodItem>, val note: String?, val fromPhoto: Boolean)

    private data class PendingCapture(val inputKey: Long, val capture: TransientCapture)

    /**
     * A question and a parsed result belong to the text they were about, so editing one item's
     * description drops both instead of letting them describe different food.
     */
    fun onTextChange(key: Long, text: String) {
        val input = input(key) ?: return
        if (input.text == text) return
        parsed -= key
        replace(
            input.copy(
                text = text,
                textMissing = false,
                isParsed = false,
                failure = null,
                clarificationQuestion = null,
                clarificationAnswer = "",
            ),
        )
    }

    fun onAddInput() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.value = state.copy(inputs = state.inputs.adding(EntryInputUiState(key = nextInputKey++)))
    }

    fun onRemoveInput(key: Long) {
        val state = _uiState.value
        if (!state.canSubmit || state.inputs.size == 1) return
        val input = input(key) ?: return
        input.photoPath?.let(photoStore::discard)
        parsed -= key
        _uiState.value = state.copy(inputs = state.inputs.removing(input))
    }

    fun onClarificationAnswerChange(key: Long, answer: String) {
        input(key)?.let { replace(it.copy(clarificationAnswer = answer)) }
    }

    /**
     * Creates the capture the camera app will write to for one input. The value is kept here, so a
     * result that arrives after the screen was recreated still finds its target.
     */
    fun onCaptureRequested(key: Long): TransientCapture {
        // Anything still pending at this point never arrived, so it cannot be waited for.
        discardPendingCapture()
        val capture = photoStore.newCapture()
        pendingCapture = PendingCapture(inputKey = key, capture = capture)
        return capture
    }

    /** A capture that did not happen leaves nothing behind, whatever the camera app wrote. */
    fun onCaptureResult(captured: Boolean) {
        val pending = pendingCapture ?: return
        pendingCapture = null
        if (captured) {
            onPhotoPicked(pending.inputKey, pending.capture.uri)
        } else {
            photoStore.discard(pending.capture.path)
        }
    }

    /** No camera app could handle the request; reported like any other unusable image. */
    fun onCaptureUnavailable(key: Long) {
        discardPendingCapture()
        input(key)?.let { replace(it.copy(isAttachingPhoto = false, photoFailed = true)) }
    }

    /**
     * Turns the picked or captured image into this input's upload candidate. A new photo is a new
     * request, so the input's pending clarification and parsed result go with it.
     */
    fun onPhotoPicked(key: Long, source: Uri) {
        val input = input(key) ?: return
        input.photoPath?.let(photoStore::discard)
        parsed -= key
        replace(
            input.copy(
                isAttachingPhoto = true,
                photoPath = null,
                photoFailed = false,
                isParsed = false,
                failure = null,
                clarificationQuestion = null,
                clarificationAnswer = "",
            ),
        )
        viewModelScope.launch {
            val path = photoStore.prepareForUpload(source)
            val current = input(key)
            if (current == null) {
                // The row was removed while the image was being encoded; nobody owns the file.
                path?.let(photoStore::discard)
                return@launch
            }
            replace(current.copy(isAttachingPhoto = false, photoPath = path, photoFailed = path == null))
        }
    }

    fun onRemovePhoto(key: Long) {
        val input = input(key) ?: return
        input.photoPath?.let(photoStore::discard)
        parsed -= key
        replace(
            input.copy(
                photoPath = null,
                photoFailed = false,
                isParsed = false,
                clarificationQuestion = null,
                clarificationAnswer = "",
            ),
        )
    }

    /** Sends every item that has not been read yet, then confirms once all of them succeeded. */
    fun onParse() {
        val state = _uiState.value
        if (!state.canSubmit) return
        if (state.inputs.any { it.text.isBlank() }) {
            _uiState.value =
                state.copy(
                    inputs = state.inputs.map { it.copy(textMissing = it.text.isBlank()) }.toPersistentList(),
                )
            return
        }
        val pending = state.inputs.filterNot { it.isParsed }
        pending.forEach { markParsing(it, clarification = null) }
        viewModelScope.launch {
            pending.forEach { parseOne(it.key, clarification = null) }
            confirmWhenEveryItemIsRead()
        }
    }

    /** Resubmits one item's text and photo together with the answer; the proxy keeps no session. */
    fun onSubmitClarification(key: Long) {
        val input = input(key) ?: return
        val question = input.clarificationQuestion ?: return
        if (!_uiState.value.canSubmit || input.clarificationAnswer.isBlank()) return
        resend(input, ClarificationAnswer(question = question, answer = input.clarificationAnswer))
    }

    /** Repeats one item's last request, including the answer it carried. */
    fun onRetry(key: Long) {
        val input = input(key) ?: return
        if (!_uiState.value.canSubmit) return
        resend(input, input.pendingClarification())
    }

    fun onItemChange(key: Long, field: MealItemField, value: String) {
        val state = _uiState.value
        _uiState.value = state.copy(items = state.items.changingItem(key, field, value), saveFailed = false)
    }

    fun onAddItem() {
        val state = _uiState.value
        _uiState.value =
            state.copy(items = state.items.adding(MealItemUiState(key = nextItemKey++)), saveFailed = false)
    }

    fun onRemoveItem(key: Long) {
        val state = _uiState.value
        _uiState.value = state.copy(items = state.items.removingItem(key), saveFailed = false)
    }

    /** Dismissing the sheet discards the parsed draft; the texts stay so they can be parsed again. */
    fun onDismissConfirmation() {
        val state = _uiState.value
        if (state.isSaving) return
        parsed.clear()
        _uiState.value =
            state.copy(
                isConfirming = false,
                items = persistentListOf(),
                note = null,
                saveFailed = false,
                inputs = state.inputs.map { it.copy(isParsed = false) }.toPersistentList(),
            )
    }

    fun onConfirm() {
        val state = _uiState.value
        if (state.isSaving) return
        val validatedItems = state.items.validated()
        _uiState.value = state.copy(items = validatedItems, saveFailed = false)
        if (validatedItems.any { it.errors.hasAny }) return

        val foodItems = validatedItems.toFoodItemsOrNull()
        if (foodItems == null) {
            _uiState.value = _uiState.value.copy(saveFailed = true)
            return
        }
        val source =
            if (parsed.values.any { it.fromPhoto }) EntrySource.LLM_PHOTO else EntrySource.LLM_TEXT
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val result =
                    saveMeal(
                        mealId = null,
                        items = foodItems,
                        source = source,
                        rawUserInput = state.inputs.joinToString(separator = "\n") { it.text },
                    )
                when (result) {
                    is SaveMealResult.Saved -> eventChannel.send(EntryEvent.Saved)

                    is SaveMealResult.Invalid, SaveMealResult.NotFound ->
                        _uiState.value = _uiState.value.copy(saveFailed = true)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                _uiState.value = _uiState.value.copy(saveFailed = true)
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    private fun input(key: Long): EntryInputUiState? = _uiState.value.inputs.firstOrNull { it.key == key }

    private fun replace(input: EntryInputUiState) {
        val state = _uiState.value
        val index = state.inputs.indexOfFirst { it.key == input.key }
        if (index < 0) return
        _uiState.value = state.copy(inputs = state.inputs.replacingAt(index, input))
    }

    /**
     * The answer that still belongs to the last request of this input, which is what a retry has
     * to resend. An edited description clears both fields, so this is null exactly when the text
     * was submitted on its own.
     */
    private fun EntryInputUiState.pendingClarification(): ClarificationAnswer? {
        val question = clarificationQuestion ?: return null
        return clarificationAnswer.takeIf { it.isNotBlank() }?.let { ClarificationAnswer(question, it) }
    }

    private fun resend(input: EntryInputUiState, clarification: ClarificationAnswer?) {
        markParsing(input, clarification)
        viewModelScope.launch {
            parseOne(input.key, clarification)
            confirmWhenEveryItemIsRead()
        }
    }

    private fun markParsing(input: EntryInputUiState, clarification: ClarificationAnswer?) {
        replace(
            input.copy(
                isParsing = true,
                textMissing = false,
                failure = null,
                // The contract only allows the question from the immediately preceding response,
                // so a question-free submission drops any earlier clarification.
                clarificationQuestion = clarification?.question,
                clarificationAnswer = clarification?.answer.orEmpty(),
            ),
        )
    }

    private suspend fun parseOne(key: Long, clarification: ClarificationAnswer?) {
        val input = input(key) ?: return
        val request =
            if (input.photoPath == null) {
                UserInput.Text(text = input.text, clarification = clarification)
            } else {
                UserInput.TextWithPhoto(
                    text = input.text,
                    temporaryPhotoPath = input.photoPath,
                    clarification = clarification,
                )
            }
        val result =
            try {
                nutritionParser.parse(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unexpected: Exception) {
                ParseResult.Failure(FailureReason.UNKNOWN, unexpected)
            }
        val current = input(key) ?: return
        when (result) {
            is ParseResult.Success -> {
                parsed[key] =
                    ParsedInput(
                        items = result.items,
                        note = result.note,
                        fromPhoto = request is UserInput.TextWithPhoto,
                    )
                // Contract §8: the photo is deleted as soon as a final answer arrives. A
                // clarification or a failure is not final, so those keep it for the resubmission.
                current.photoPath?.let(photoStore::discard)
                replace(
                    current.copy(
                        isParsing = false,
                        isParsed = true,
                        photoPath = null,
                        failure = null,
                        clarificationQuestion = null,
                        clarificationAnswer = "",
                    ),
                )
            }

            is ParseResult.NeedsClarification ->
                replace(
                    current.copy(
                        isParsing = false,
                        failure = null,
                        clarificationQuestion = result.question,
                        clarificationAnswer = "",
                    ),
                )

            is ParseResult.Failure -> replace(current.copy(isParsing = false, failure = result.reason))
        }
    }

    /** One sheet confirms the whole meal, so it opens only once no item is left unread. */
    private fun confirmWhenEveryItemIsRead() {
        val state = _uiState.value
        if (state.inputs.any { !it.isParsed }) return
        val results = state.inputs.mapNotNull { parsed[it.key] }
        val items = results.flatMap { it.items }.toItemStates(localeProvider.current())
        nextItemKey = items.size.toLong() + FIRST_ITEM_KEY
        _uiState.value =
            state.copy(
                isConfirming = true,
                note = results.mapNotNull { it.note }.distinct().joinToString("\n").ifBlank { null },
                items = items,
            )
    }

    private fun discardPendingCapture() {
        pendingCapture?.let { photoStore.discard(it.capture.path) }
        pendingCapture = null
    }

    /** Leaving the flow, by Back, by navigation, or by a saved meal, takes the photos with it. */
    public override fun onCleared() {
        pendingCapture = null
        photoStore.clear()
    }
}
