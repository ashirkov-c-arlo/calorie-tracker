package app.kcal.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.DecimalText
import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.Macros
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.usecase.SaveManualMeal
import app.kcal.domain.usecase.SaveMealResult
import app.kcal.domain.usecase.needsReview
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

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val saveManualMeal: SaveManualMeal,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ManualEntryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var requestedMealId: Long? = null
    private var hasRequestedLoad = false
    private var nextItemKey = 1L

    fun load(mealId: Long?) {
        if (hasRequestedLoad && requestedMealId == mealId) return
        requestedMealId = mealId
        hasRequestedLoad = true
        loadRequestedMeal()
    }

    fun onRetryLoad() {
        loadRequestedMeal()
    }

    fun onItemChange(key: Long, field: ManualEntryField, value: String) {
        val state = _uiState.value
        val index = state.items.indexOfFirst { it.key == key }
        if (index < 0) return
        val item = state.items[index]
        val updated =
            when (field) {
                ManualEntryField.NAME -> item.copy(name = value)
                ManualEntryField.GRAMS -> item.copy(grams = value)
                ManualEntryField.KCAL -> item.copy(kcal = value)
                ManualEntryField.PROTEIN -> item.copy(protein = value)
                ManualEntryField.FAT -> item.copy(fat = value)
                ManualEntryField.CARBS -> item.copy(carbs = value)
            }.withDerivedState(showErrors = item.errors.hasAny)
        _uiState.value = state.copy(items = state.items.replacingAt(index, updated), saveFailed = false)
    }

    fun onAddItem() {
        val state = _uiState.value
        _uiState.value =
            state.copy(
                items = state.items.adding(ManualEntryItemUiState(key = nextItemKey++)),
                saveFailed = false,
            )
    }

    fun onRemoveItem(key: Long) {
        val state = _uiState.value
        if (state.items.size == 1) return
        val index = state.items.indexOfFirst { it.key == key }
        if (index >= 0) {
            _uiState.value = state.copy(items = state.items.removingAt(index), saveFailed = false)
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        val validatedItems = state.items.map { it.withDerivedState(showErrors = true) }.toPersistentList()
        _uiState.value = state.copy(items = validatedItems, saveFailed = false)
        if (validatedItems.any { it.errors.hasAny }) return

        val foodItems = validatedItems.mapNotNull { it.toFoodItem() }
        if (foodItems.size != validatedItems.size) {
            _uiState.value = _uiState.value.copy(saveFailed = true)
            return
        }
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                when (saveManualMeal(state.mealId, foodItems)) {
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
            nextItemKey = 2
            _uiState.value =
                ManualEntryUiState(
                    isLoading = false,
                    items = persistentListOf(ManualEntryItemUiState(key = 1)),
                )
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
                val locale = localeProvider.current()
                val items =
                    meal.items.mapIndexed { index, item ->
                        ManualEntryItemUiState(
                            key = index.toLong() + 1,
                            name = item.name,
                            grams = item.grams?.let { DecimalText.format(it, locale) }.orEmpty(),
                            kcal = item.macros.kcal.toString(),
                            protein = DecimalText.format(item.macros.proteinG, locale),
                            fat = DecimalText.format(item.macros.fatG, locale),
                            carbs = DecimalText.format(item.macros.carbsG, locale),
                            needsReview = item.needsReview(),
                        )
                    }.toPersistentList()
                nextItemKey = items.size.toLong() + 1
                _uiState.value = ManualEntryUiState(isLoading = false, mealId = mealId, items = items)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                _uiState.value = ManualEntryUiState(isLoading = false, mealId = mealId, loadFailed = true)
            }
        }
    }

    private fun ManualEntryItemUiState.withDerivedState(showErrors: Boolean): ManualEntryItemUiState {
        val foodItem = toFoodItem()
        return copy(
            errors = if (showErrors) fieldErrors() else ManualEntryItemErrors(),
            needsReview = foodItem?.needsReview() == true,
        )
    }

    private fun ManualEntryItemUiState.fieldErrors(): ManualEntryItemErrors = ManualEntryItemErrors(
        name = ManualEntryFieldError.REQUIRED.takeIf { name.isBlank() },
        grams = optionalDecimalError(grams),
        kcal = integerError(kcal),
        protein = requiredDecimalError(protein),
        fat = requiredDecimalError(fat),
        carbs = requiredDecimalError(carbs),
    )

    private fun ManualEntryItemUiState.toFoodItem(): FoodItem? {
        if (name.isBlank()) return null
        val parsedGrams = if (grams.isBlank()) null else DecimalText.parse(grams) ?: return null
        val parsedKcal = DecimalText.parseInt(kcal) ?: return null
        val parsedProtein = DecimalText.parse(protein) ?: return null
        val parsedFat = DecimalText.parse(fat) ?: return null
        val parsedCarbs = DecimalText.parse(carbs) ?: return null
        if (
            parsedGrams?.let { it < 0.0 } == true ||
            parsedKcal < 0 ||
            parsedProtein < 0.0 ||
            parsedFat < 0.0 ||
            parsedCarbs < 0.0
        ) {
            return null
        }
        return FoodItem(
            name = name.trim(),
            grams = parsedGrams,
            macros =
            Macros(
                kcal = parsedKcal,
                proteinG = parsedProtein,
                fatG = parsedFat,
                carbsG = parsedCarbs,
            ),
            confidence = 1f,
        )
    }

    private fun requiredDecimalError(text: String): ManualEntryFieldError? {
        if (text.isBlank()) return ManualEntryFieldError.REQUIRED
        val number = DecimalText.parse(text) ?: return ManualEntryFieldError.INVALID_NUMBER
        return ManualEntryFieldError.NEGATIVE.takeIf { number < 0.0 }
    }

    private fun optionalDecimalError(text: String): ManualEntryFieldError? =
        if (text.isBlank()) null else requiredDecimalError(text)

    private fun integerError(text: String): ManualEntryFieldError? {
        if (text.isBlank()) return ManualEntryFieldError.REQUIRED
        val number = DecimalText.parseInt(text) ?: return ManualEntryFieldError.INVALID_NUMBER
        return ManualEntryFieldError.NEGATIVE.takeIf { number < 0 }
    }
}
