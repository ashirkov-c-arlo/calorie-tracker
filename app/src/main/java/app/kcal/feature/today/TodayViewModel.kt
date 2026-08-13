package app.kcal.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.Macros
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
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val dailyTargetRepository: DailyTargetRepository,
    private val aggregateMealMacros: AggregateMealMacros,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var observedDate = timeProvider.today()

    init {
        load(observedDate)
    }

    /** Refreshes the observed local day after returning from another destination. */
    fun onVisible() {
        val today = timeProvider.today()
        if (today != observedDate) load(today)
    }

    fun onRetry() {
        load(timeProvider.today())
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

    private fun load(today: java.time.LocalDate) {
        loadJob?.cancel()
        observedDate = today
        _uiState.value = TodayUiState()
        loadJob =
            viewModelScope.launch {
                try {
                    combine(
                        mealRepository.observeByDate(today),
                        dailyTargetRepository.observe(today),
                    ) { meals, snapshot ->
                        val consumed = aggregateMealMacros(meals)
                        TodayUiState(
                            isLoading = false,
                            consumed = consumed,
                            progress = snapshot?.targets?.let { target -> progress(consumed, target) },
                            meals =
                            meals.map { meal ->
                                TodayMealUiState(
                                    id = meal.id,
                                    itemNames = meal.items.map { it.name }.toPersistentList(),
                                    totals = aggregateMealMacros.items(meal.items),
                                )
                            }.toPersistentList(),
                        )
                    }.collect { _uiState.value = it }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (storageFailure: Exception) {
                    _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
                }
            }
    }

    private fun progress(consumed: Macros, target: Macros): TodayMacroProgressUiState = TodayMacroProgressUiState(
        consumed = consumed,
        target = target,
        kcalFraction = ratio(consumed.kcal.toDouble(), target.kcal.toDouble()),
        proteinFraction = ratio(consumed.proteinG, target.proteinG),
        fatFraction = ratio(consumed.fatG, target.fatG),
        carbsFraction = ratio(consumed.carbsG, target.carbsG),
    )

    private fun ratio(consumed: Double, target: Double): Float =
        if (target > 0.0) (consumed / target).coerceIn(0.0, 1.0).toFloat() else 0f
}
