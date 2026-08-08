package app.kcal.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
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
 * Text parsing and its confirmation. The typed text survives every clarification, failure, and
 * retry, and only an explicit confirmation writes to storage.
 */
@HiltViewModel
class EntryViewModel @Inject constructor(
    private val nutritionParser: NutritionParser,
    private val saveMeal: SaveMeal,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EntryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var nextItemKey = FIRST_ITEM_KEY

    /** What Retry resends: the contract requires the exact previous request, nothing rebuilt. */
    private var lastInput: UserInput? = null

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

    fun onParse() {
        val state = _uiState.value
        if (!state.canSubmit) return
        if (state.text.isBlank()) {
            _uiState.value = state.copy(textMissing = true)
            return
        }
        parse(UserInput.Text(text = state.text))
    }

    /** Resubmits the original text together with the answer; the proxy keeps no session. */
    fun onSubmitClarification() {
        val state = _uiState.value
        val question = state.clarificationQuestion ?: return
        if (!state.canSubmit || state.clarificationAnswer.isBlank()) return
        parse(
            UserInput.Text(
                text = state.text,
                clarification = ClarificationAnswer(question = question, answer = state.clarificationAnswer),
            ),
        )
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
                        source = EntrySource.LLM_TEXT,
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

    private fun parse(input: UserInput) {
        lastInput = input
        _uiState.value = _uiState.value.copy(isParsing = true, failure = null, textMissing = false)
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
}
