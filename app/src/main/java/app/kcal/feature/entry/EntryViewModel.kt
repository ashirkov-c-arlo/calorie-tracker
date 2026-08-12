package app.kcal.feature.entry

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TransientPhotoStore
import app.kcal.domain.model.EntrySource
import app.kcal.domain.usecase.SaveMeal
import app.kcal.domain.usecase.SaveMealResult
import app.kcal.llm.ClarificationAnswer
import app.kcal.llm.FailureReason
import app.kcal.llm.NutritionParser
import app.kcal.llm.ParseResult
import app.kcal.llm.UserInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Text parsing and its confirmation, optionally about a photo of the plate. The typed text
 * survives every clarification, failure, and retry, and only an explicit confirmation writes to
 * storage. The photo is transient: it is uploaded, never persisted, and deleted as soon as the
 * flow no longer needs it.
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

    private var nextItemKey = FIRST_ITEM_KEY

    /** What Retry resends: the contract requires the exact previous request, nothing rebuilt. */
    private var lastInput: UserInput? = null

    /** Which source the confirmed meal records, kept because the photo is dropped on success. */
    private var parsedSource = EntrySource.LLM_TEXT

    /**
     * A question belongs to the text it was asked about, so editing the description drops the
     * pending clarification instead of letting it be attached to a different meal.
     */
    fun onTextChange(text: String) {
        val state = _uiState.value
        if (state.text == text) return
        lastInput = null
        _uiState.value =
            state.copy(
                text = text,
                textMissing = false,
                failure = null,
                clarificationQuestion = null,
                clarificationAnswer = "",
            )
    }

    fun onClarificationAnswerChange(answer: String) {
        _uiState.value = _uiState.value.copy(clarificationAnswer = answer)
    }

    /** Where the camera app writes a capture; only a content URI, so it is cheap enough to call here. */
    fun newCaptureUri(): Uri = photoStore.newCaptureUri()

    /**
     * Turns the picked or captured image into the single upload candidate. A new photo is a new
     * request, so any pending clarification is dropped with it.
     */
    fun onPhotoPicked(source: Uri) {
        lastInput = null
        _uiState.value =
            _uiState.value.copy(
                isAttachingPhoto = true,
                photoFailed = false,
                failure = null,
                clarificationQuestion = null,
                clarificationAnswer = "",
            )
        viewModelScope.launch {
            val path = photoStore.prepareForUpload(source)
            _uiState.value =
                _uiState.value.copy(isAttachingPhoto = false, photoPath = path, photoFailed = path == null)
        }
    }

    fun onRemovePhoto() {
        lastInput = null
        photoStore.clear()
        _uiState.value =
            _uiState.value.copy(
                photoPath = null,
                photoFailed = false,
                clarificationQuestion = null,
                clarificationAnswer = "",
            )
    }

    fun onParse() {
        val state = _uiState.value
        if (!state.canSubmit) return
        if (state.text.isBlank()) {
            _uiState.value = state.copy(textMissing = true)
            return
        }
        parse(inputWith(clarification = null))
    }

    /** Resubmits the original text and photo together with the answer; the proxy keeps no session. */
    fun onSubmitClarification() {
        val state = _uiState.value
        val question = state.clarificationQuestion ?: return
        if (!state.canSubmit || state.clarificationAnswer.isBlank()) return
        parse(inputWith(ClarificationAnswer(question = question, answer = state.clarificationAnswer)))
    }

    fun onRetry() {
        if (!_uiState.value.canSubmit) return
        lastInput?.let(::parse) ?: onParse()
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

    /** Dismissing the sheet discards the parsed draft; the text stays so it can be parsed again. */
    fun onDismissConfirmation() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.value =
            state.copy(isConfirming = false, items = persistentListOf(), note = null, saveFailed = false)
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
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val result =
                    saveMeal(
                        mealId = null,
                        items = foodItems,
                        source = parsedSource,
                        rawUserInput = state.text,
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

    private fun inputWith(clarification: ClarificationAnswer?): UserInput {
        val state = _uiState.value
        return if (state.photoPath == null) {
            UserInput.Text(text = state.text, clarification = clarification)
        } else {
            UserInput.TextWithPhoto(
                text = state.text,
                temporaryPhotoPath = state.photoPath,
                clarification = clarification,
            )
        }
    }

    private fun parse(input: UserInput) {
        lastInput = input
        parsedSource = if (input is UserInput.TextWithPhoto) EntrySource.LLM_PHOTO else EntrySource.LLM_TEXT
        _uiState.value =
            _uiState.value.copy(
                isParsing = true,
                failure = null,
                textMissing = false,
                // The contract only allows the question from the immediately preceding response,
                // so a question-free submission drops any earlier clarification.
                clarificationQuestion = input.clarification?.question,
                clarificationAnswer = input.clarification?.answer.orEmpty(),
            )
        viewModelScope.launch {
            val result =
                try {
                    nutritionParser.parse(input)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (unexpected: Exception) {
                    ParseResult.Failure(FailureReason.UNKNOWN, unexpected)
                }
            _uiState.value = _uiState.value.applyParseResult(result)
            // Contract §8: the photo is deleted as soon as a final answer arrives. A clarification
            // or a failure is not final, so those keep it for the resubmission.
            if (result is ParseResult.Success) photoStore.clear()
        }
    }

    private fun EntryUiState.applyParseResult(result: ParseResult): EntryUiState = when (result) {
        is ParseResult.Success -> {
            val items = result.items.toItemStates(localeProvider.current())
            nextItemKey = items.size.toLong() + FIRST_ITEM_KEY
            copy(
                isParsing = false,
                failure = null,
                clarificationQuestion = null,
                clarificationAnswer = "",
                isConfirming = true,
                note = result.note,
                items = items,
                photoPath = null,
            )
        }

        is ParseResult.NeedsClarification ->
            copy(
                isParsing = false,
                failure = null,
                clarificationQuestion = result.question,
                clarificationAnswer = "",
            )

        is ParseResult.Failure -> copy(isParsing = false, failure = result.reason)
    }

    /** Leaving the flow, by Back, by navigation, or by a saved meal, takes the photo with it. */
    public override fun onCleared() {
        photoStore.clear()
    }
}
