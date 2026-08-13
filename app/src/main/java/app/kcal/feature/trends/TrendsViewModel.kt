package app.kcal.feature.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.DecimalText
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.WeightEntry
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.BodyMetrics
import app.kcal.domain.usecase.BuildWeightTrend
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
import javax.inject.Inject

/**
 * Weight logging and the calendar-window trend. The typed value is converted to canonical
 * kilograms before validation and storage, so limits never depend on the displayed units, and
 * one save upserts the entry of the current local date.
 *
 * Today's target snapshot is not written here: the app shell owns target replacement and
 * reacts to the new latest weight, so a stale calculation cannot overwrite a newer target.
 */
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val buildWeightTrend: BuildWeightTrend,
    private val timeProvider: TimeProvider,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onRetry() {
        load()
    }

    fun onWeightChange(text: String) {
        _uiState.value = _uiState.value.copy(weightInput = text, inputError = null, saveFailed = false)
    }

    fun onSave() {
        val state = _uiState.value
        val kilograms = state.weightInput.toKilograms(state.unitSystem)
        if (kilograms == null) {
            _uiState.value = state.copy(inputError = inputError(state), saveFailed = false)
            return
        }
        _uiState.value = state.copy(inputError = null, saveFailed = false)
        viewModelScope.launch {
            try {
                profileRepository.logWeight(WeightEntry(localDate = timeProvider.today(), kg = kilograms))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                // Surfaced as a retryable message; nothing about the value is logged.
                _uiState.value = _uiState.value.copy(saveFailed = true)
            }
        }
    }

    /**
     * The field is filled from the entry of the current local date, and refilled whenever the
     * unit system changes, so a value typed in one unit can never be stored as another.
     */
    private fun load() {
        loadJob?.cancel()
        _uiState.value = TrendsUiState()
        loadJob =
            viewModelScope.launch {
                try {
                    combine(profileRepository.weights, profileRepository.preferences) { weights, preferences ->
                        weights to preferences.unitSystem
                    }.collect { (weights, unitSystem) ->
                        val state = _uiState.value
                        val refill = state.isLoading || state.unitSystem != unitSystem
                        val today = weights.firstOrNull { it.localDate == timeProvider.today() }
                        _uiState.value =
                            state.copy(
                                isLoading = false,
                                unitSystem = unitSystem,
                                weightInput = if (refill) today?.kg.formatInput(unitSystem) else state.weightInput,
                                inputError = state.inputError.takeUnless { refill },
                                points =
                                buildWeightTrend(weights)
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
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (storageFailure: Exception) {
                    _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
                }
            }
    }

    private fun inputError(state: TrendsUiState): ProfileFieldError = when {
        state.weightInput.isBlank() -> ProfileFieldError.REQUIRED
        DecimalText.parse(state.weightInput) == null -> ProfileFieldError.INVALID_NUMBER
        else -> ProfileFieldError.OUT_OF_RANGE
    }

    /** Null when the value is missing, unparseable or outside the plausible weight range. */
    private fun String.toKilograms(unitSystem: UnitSystem): Double? {
        val entered = DecimalText.parse(this) ?: return null
        val kilograms = if (unitSystem == UnitSystem.METRIC) entered else UnitConversions.poundsToKilograms(entered)
        return kilograms.takeIf { it in BodyMetrics.PLAUSIBLE_WEIGHT_RANGE_KG }
    }

    private fun Double.toDisplayed(unitSystem: UnitSystem): Double =
        if (unitSystem == UnitSystem.METRIC) this else UnitConversions.kilogramsToPounds(this)

    private fun Double?.formatInput(unitSystem: UnitSystem): String =
        this?.let { DecimalText.format(it.toDisplayed(unitSystem), localeProvider.current()) }.orEmpty()
}
