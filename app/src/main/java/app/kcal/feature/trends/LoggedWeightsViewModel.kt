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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LoggedWeightsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val buildWeightTrend: BuildWeightTrend,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoggedWeightsUiState())
    val uiState: StateFlow<LoggedWeightsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(
                    profileRepository.weights,
                    profileRepository.preferences,
                ) { weights, preferences ->
                    Pair(weights, preferences.unitSystem)
                }.collect { (weights, unitSystem) ->
                    _uiState.value = reduce(weights, unitSystem)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onEntryClick(localDate: LocalDate) {
        _uiState.value = _uiState.value.copy(editedDate = localDate)
    }

    fun onDeleteEntry(localDate: LocalDate) {
        viewModelScope.launch {
            try {
                profileRepository.deleteWeight(localDate)
                if (_uiState.value.editedDate == localDate) {
                    _uiState.value = _uiState.value.copy(editedDate = null)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Ignored; user can retry
            }
        }
    }

    private fun reduce(
        weights: List<app.kcal.domain.model.WeightEntry>,
        unitSystem: UnitSystem,
    ): LoggedWeightsUiState {
        return LoggedWeightsUiState(
            isLoading = false,
            unitSystem = unitSystem,
            editedDate = _uiState.value.editedDate,
            points = buildWeightTrend(weights)
                .map { point ->
                    WeightPointUiState(
                        localDate = point.localDate,
                        value = point.kg.toDisplayed(unitSystem),
                        trendValue = point.trendKg.toDisplayed(unitSystem),
                    )
                }
                .toPersistentList(),
        )
    }

    private fun Double.toDisplayed(unitSystem: UnitSystem): Double =
        if (unitSystem == UnitSystem.METRIC) this else UnitConversions.kilogramsToPounds(this)
}
