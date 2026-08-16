package app.kcal.feature.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.BuildWeightTrend
import app.kcal.domain.usecase.UnitConversions
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

/**
 * Weight trend viewing only. Weight input moved to Today screen.
 * This ViewModel now only displays the chart and handles deletion of entries.
 */
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val buildWeightTrend: BuildWeightTrend,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    private val currentDate = MutableStateFlow(timeProvider.today())
    private var loadJob: Job? = null

    init {
        load()
    }

    /** Picks up a day change after navigation or app resume. */
    fun onVisible() {
        currentDate.value = timeProvider.today()
    }

    fun onRetry() {
        load()
    }

    /** Selects a logged day for viewing (for potential deletion). */
    fun onEntryClick(localDate: LocalDate) {
        _uiState.value = _uiState.value.copy(editedDate = localDate)
    }

    fun onDeleteEntry(localDate: LocalDate) {
        viewModelScope.launch {
            try {
                profileRepository.deleteWeight(localDate)
                // Clear selection if the deleted date was selected
                if (_uiState.value.editedDate == localDate) {
                    _uiState.value = _uiState.value.copy(editedDate = null)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Ignored; the entry stays visible and the user can retry
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = TrendsUiState()
        loadJob =
            viewModelScope.launch {
                try {
                    combine(
                        profileRepository.weights,
                        profileRepository.preferences,
                        currentDate,
                    ) { weights, preferences, today ->
                        Inputs(weights, preferences.unitSystem, today)
                    }.collect { inputs -> _uiState.value = reduce(inputs) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (storageFailure: Exception) {
                    _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
                }
            }
    }

    private fun reduce(inputs: Inputs): TrendsUiState {
        return TrendsUiState(
            isLoading = false,
            unitSystem = inputs.unitSystem,
            editedDate = null,
            points =
            buildWeightTrend(inputs.weights)
                .map { point ->
                    WeightPointUiState(
                        localDate = point.localDate,
                        value = point.kg.toDisplayed(inputs.unitSystem),
                        trendValue = point.trendKg.toDisplayed(inputs.unitSystem),
                    )
                }
                .toPersistentList(),
        )
    }

    private fun Double.toDisplayed(unitSystem: UnitSystem): Double =
        if (unitSystem == UnitSystem.METRIC) this else UnitConversions.kilogramsToPounds(this)

    private data class Inputs(
        val weights: List<app.kcal.domain.model.WeightEntry>,
        val unitSystem: UnitSystem,
        val today: LocalDate,
    )
}
