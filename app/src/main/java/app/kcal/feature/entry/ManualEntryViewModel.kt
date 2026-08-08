package app.kcal.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.usecase.SaveMeal
import app.kcal.domain.usecase.SaveMealResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val saveMeal: SaveMeal,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ManualEntryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var requestedMealId: Long? = null
    private var hasRequestedLoad = false
    private var nextItemKey = FIRST_ITEM_KEY + 1

    fun load(mealId: Long?) {
        if (hasRequestedLoad && requestedMealId == mealId) return
        requestedMealId = mealId
        hasRequestedLoad = true
        loadRequestedMeal()
    }

    fun onRetryLoad() {
        loadRequestedMeal()
    }

    fun onItemChange(key: Long, field: MealItemField, value: String) {
        val state = _uiState.value
        _uiState.value = state.copy(items = state.items.changingItem(key, field, value), saveFailed = false)
    }

    fun onAddItem() {
        val state = _uiState.value
        _uiState.value =
            state.copy(
                items = state.items.adding(MealItemUiState(key = nextItemKey++)),
                saveFailed = false,
            )
    }

    fun onRemoveItem(key: Long) {
        val state = _uiState.value
        _uiState.value = state.copy(items = state.items.removingItem(key), saveFailed = false)
    }

    fun onSave() {
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
                when (saveMeal(state.mealId, foodItems)) {
                    is SaveMealResult.Saved -> eventChannel.send(ManualEntryEvent.Saved)
                    is SaveMealResult.Invalid -> _uiState.value = _uiState.value.copy(saveFailed = true)
                    SaveMealResult.NotFound -> _uiState.value = _uiState.value.copy(loadFailed = true)
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

    private fun loadRequestedMeal() {
        val mealId = requestedMealId
        if (mealId == null) {
            nextItemKey = FIRST_ITEM_KEY + 1
            _uiState.value = ManualEntryUiState(isLoading = false, items = emptyMealItems())
            return
        }
        _uiState.value = ManualEntryUiState(isLoading = true, mealId = mealId)
        viewModelScope.launch {
            try {
                val meal = mealRepository.findById(mealId)
                if (meal == null) {
                    _uiState.value = ManualEntryUiState(isLoading = false, mealId = mealId, loadFailed = true)
                    return@launch
                }
                val items = meal.items.toItemStates(localeProvider.current())
                nextItemKey = items.size.toLong() + FIRST_ITEM_KEY
                _uiState.value = ManualEntryUiState(isLoading = false, mealId = mealId, items = items)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                _uiState.value = ManualEntryUiState(isLoading = false, mealId = mealId, loadFailed = true)
            }
        }
    }
}
