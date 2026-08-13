package app.kcal.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.domain.model.HistoryDay
import app.kcal.domain.model.HistoryWeek
import app.kcal.domain.model.MealEntry
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.usecase.AggregateMealMacros
import app.kcal.domain.usecase.BuildHistory
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
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val dailyTargetRepository: DailyTargetRepository,
    private val buildHistory: BuildHistory,
    private val aggregateMealMacros: AggregateMealMacros,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val expandedDay = MutableStateFlow<LocalDate?>(null)
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    /** Expanding a day is the day detail: it reveals that day's meals for edit and delete. */
    fun onDayClick(localDate: LocalDate) {
        expandedDay.value = localDate.takeUnless { it == expandedDay.value }
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

    private fun load() {
        loadJob?.cancel()
        _uiState.value = HistoryUiState()
        loadJob =
            viewModelScope.launch {
                try {
                    combine(
                        mealRepository.observeAll(),
                        dailyTargetRepository.observeAll(),
                        expandedDay,
                    ) { meals, targets, expanded ->
                        HistoryUiState(
                            isLoading = false,
                            weeks =
                            buildHistory(meals, targets)
                                .map { week -> week.toUiState(expanded) }
                                .toPersistentList(),
                        )
                    }.collect { _uiState.value = it }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (storageFailure: Exception) {
                    _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
                }
            }
    }

    private fun HistoryWeek.toUiState(expanded: LocalDate?): HistoryWeekUiState = HistoryWeekUiState(
        weekOfYear = weekOfYear,
        start = start,
        end = end,
        consumed = consumed,
        days = days.map { day -> day.toUiState(expanded) }.toPersistentList(),
    )

    private fun HistoryDay.toUiState(expanded: LocalDate?): HistoryDayUiState = HistoryDayUiState(
        localDate = localDate,
        consumed = consumed,
        target = target,
        kcalFraction = target?.kcal?.takeIf { it > 0 }?.let { kcal ->
            (consumed.kcal.toFloat() / kcal).coerceIn(0f, 1f)
        },
        isExpanded = localDate == expanded,
        meals = meals.map(::toMealUiState).toPersistentList(),
    )

    private fun toMealUiState(meal: MealEntry): HistoryMealUiState = HistoryMealUiState(
        id = meal.id,
        itemNames = meal.items.map { it.name }.toPersistentList(),
        totals = aggregateMealMacros.items(meal.items),
    )
}
