package app.kcal.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.DecimalText
import app.kcal.core.common.TimeProvider
import app.kcal.core.ui.macroProgress
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.WeightEntry
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.AggregateMealMacros
import app.kcal.domain.usecase.BodyMetrics
import app.kcal.domain.usecase.LogWeight
import app.kcal.domain.usecase.UnitConversions
import app.kcal.feature.profile.ProfileFieldError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val dailyTargetRepository: DailyTargetRepository,
    private val profileRepository: ProfileRepository,
    private val logWeight: LogWeight,
    private val aggregateMealMacros: AggregateMealMacros,
    private val timeProvider: TimeProvider,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var today = timeProvider.today()
    private var selectedDate = today
    private var hasLoggedWeightToday = false

    init {
        load(today)
    }

    fun onVisible() {
        val newToday = timeProvider.today()
        if (newToday != today || _uiState.value.hasError) {
            today = newToday
            selectedDate = newToday
            load(selectedDate)
        }
    }

    fun onRetry() {
        load(selectedDate)
    }

    fun onSelectDate(date: LocalDate) {
        if (date > today) return
        selectedDate = date
        load(selectedDate)
    }

    fun onWeightInputChange(text: String) {
        _uiState.value = _uiState.value.copy(weightInput = text, weightInputError = null)
    }

    fun onSaveWeight() {
        val state = _uiState.value
        if (state.isWeightSaving) return
        val kilograms = state.weightInput.toKilograms(state.unitSystem)
        if (kilograms == null) {
            _uiState.value = state.copy(weightInputError = inputError(state))
            return
        }
        _uiState.value = state.copy(weightInputError = null, isWeightSaving = true)
        viewModelScope.launch {
            try {
                val saved = logWeight(WeightEntry(localDate = today, kg = kilograms))
                if (saved) {
                    hasLoggedWeightToday = true
                    _uiState.value = _uiState.value.copy(
                        showWeightInput = false,
                        isWeightSaving = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        weightInputError = ProfileFieldError.OUT_OF_RANGE,
                        isWeightSaving = false,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isWeightSaving = false)
            }
        }
    }

    private fun inputError(state: TodayUiState): ProfileFieldError = when {
        state.weightInput.isBlank() -> ProfileFieldError.REQUIRED
        DecimalText.parse(state.weightInput) == null -> ProfileFieldError.INVALID_NUMBER
        else -> ProfileFieldError.OUT_OF_RANGE
    }

    private fun String.toKilograms(unitSystem: UnitSystem): Double? {
        val entered = DecimalText.parse(this) ?: return null
        val kilograms = if (unitSystem == UnitSystem.METRIC) entered else UnitConversions.poundsToKilograms(entered)
        return kilograms.takeIf { it in BodyMetrics.PLAUSIBLE_WEIGHT_RANGE_KG }
    }

    fun onDeleteMeal(mealId: Long) {
        viewModelScope.launch {
            try {
                mealRepository.delete(mealId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
            }
        }
    }

    private fun buildDayStrip(selected: LocalDate): List<DayStripItem> {
        val locale = localeProvider.current()
        val stripEnd = today
        val stripStart = stripEnd.minusDays(4)
        return (0L..4L).map { offset ->
            val date = stripStart.plusDays(offset)
            DayStripItem(
                date = date,
                dayOfMonth = date.dayOfMonth,
                monthAbbr = date.month.getDisplayName(TextStyle.SHORT, locale),
                isSelected = date == selected,
            )
        }
    }

    private fun load(date: LocalDate) {
        loadJob?.cancel()
        selectedDate = date
        val showWeightInput = date == today && !hasLoggedWeightToday
        _uiState.value = TodayUiState(
            selectedDate = date,
            isToday = date == today,
            showWeightInput = showWeightInput,
            dayStrip = buildDayStrip(date).toPersistentList(),
        )
        loadJob =
            viewModelScope.launch {
                try {
                    combine(
                        mealRepository.observeByDate(date),
                        dailyTargetRepository.observe(date),
                        profileRepository.weights,
                        profileRepository.preferences,
                    ) { meals, snapshot, weights, preferences ->
                        val consumed = aggregateMealMacros(meals)
                        val todayWeight = weights.firstOrNull { it.localDate == today }
                        hasLoggedWeightToday = todayWeight != null
                        val shouldShowWeightInput = date == today && todayWeight == null
                        TodayUiState(
                            isLoading = false,
                            consumed = consumed,
                            progress = snapshot?.targets?.let { target -> macroProgress(consumed, target) },
                            meals =
                            meals.map { meal ->
                                TodayMealUiState(
                                    id = meal.id,
                                    itemNames = meal.items.map { it.name }.toPersistentList(),
                                    totals = aggregateMealMacros.items(meal.items),
                                    summary = meal.summary,
                                )
                            }.toPersistentList(),
                            selectedDate = date,
                            isToday = date == today,
                            showWeightInput = shouldShowWeightInput,
                            unitSystem = preferences.unitSystem,
                            weightInput = _uiState.value.weightInput,
                            dayStrip = buildDayStrip(date).toPersistentList(),
                        )
                    }.collect { _uiState.value = it }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (storageFailure: Exception) {
                    _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
                }
            }
    }
}
