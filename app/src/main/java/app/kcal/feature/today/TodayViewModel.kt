package app.kcal.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TimeProvider
import app.kcal.core.ui.macroProgress
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.usecase.AggregateMealMacros
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
    private val aggregateMealMacros: AggregateMealMacros,
    private val timeProvider: TimeProvider,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var today = timeProvider.today()
    private var selectedDate = today

    init {
        load(selectedDate)
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
        _uiState.value = TodayUiState(
            selectedDate = date,
            isToday = date == today,
            dayStrip = buildDayStrip(date).toPersistentList(),
        )
        loadJob =
            viewModelScope.launch {
                try {
                    combine(
                        mealRepository.observeByDate(date),
                        dailyTargetRepository.observe(date),
                    ) { meals, snapshot ->
                        val consumed = aggregateMealMacros(meals)
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
