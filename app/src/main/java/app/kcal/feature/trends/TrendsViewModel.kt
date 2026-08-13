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
import javax.inject.Inject

/**
 * Weight logging and the calendar-window trend. The typed value is converted to canonical
 * kilograms before validation and storage, so limits never depend on the displayed units, and
 * one save upserts the entry of the edited date.
 *
 * The editor follows the current local date until the user picks a logged day, which is how a
 * wrong historical entry is corrected. In follow mode the save date is recomputed from the clock
 * at save time, so a screen left open across midnight logs the new day rather than yesterday. An
 * untouched field always mirrors what is stored for the edited date, so it cannot go stale and
 * write back an outdated weight after a change made elsewhere.
 *
 * Today's target snapshot is not written here: the app shell owns target replacement and
 * reacts to the new latest weight, so a stale calculation cannot overwrite a newer target.
 */
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val logWeight: LogWeight,
    private val buildWeightTrend: BuildWeightTrend,
    private val timeProvider: TimeProvider,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    private val currentDate = MutableStateFlow(timeProvider.today())

    /** Null while the editor follows the current local date. */
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    /** True once the user typed, which is the only state in which the field is not refilled. */
    private var isDraftEdited = false

    /** Bumped by every editor change, so a finished save can tell whether it saved this draft. */
    private var draftRevision = 0

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

    /** Selects a logged day for correction; the field is refilled from its stored value. */
    fun onEntryClick(localDate: LocalDate) {
        editDate(localDate)
    }

    /** Returns the editor to the current local date and to following further day changes. */
    fun onLogTodayClick() {
        editDate(null)
    }

    fun onWeightChange(text: String) {
        isDraftEdited = true
        draftRevision++
        _uiState.value = _uiState.value.copy(weightInput = text, inputError = null, saveFailed = false)
    }

    private fun editDate(localDate: LocalDate?) {
        isDraftEdited = false
        draftRevision++
        selectedDate.value = localDate
    }

    /** One save at a time, so an older write can never land after a newer one. */
    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        val today = timeProvider.today()
        currentDate.value = today
        // Follow mode resolves the date from the clock right here: the screen may have been open
        // across midnight, where no lifecycle event refreshes it. The visible value is what gets
        // logged, and the previous day keeps its own entry untouched.
        val localDate = selectedDate.value ?: today
        val kilograms = state.weightInput.toKilograms(state.unitSystem)
        val dated = state.copy(editedDate = localDate, isEditingToday = localDate == today)
        if (kilograms == null) {
            _uiState.value = dated.copy(inputError = inputError(state), saveFailed = false)
            return
        }
        _uiState.value = dated.copy(inputError = null, saveFailed = false, isSaving = true)
        val savedRevision = draftRevision
        viewModelScope.launch {
            try {
                val stored = logWeight(WeightEntry(localDate = localDate, kg = kilograms))
                if (stored) {
                    // The field tracks storage again, unless the editor moved on meanwhile: that
                    // draft belongs to another date, unit system or value.
                    if (draftRevision == savedRevision) isDraftEdited = false
                } else {
                    _uiState.value = _uiState.value.copy(inputError = ProfileFieldError.OUT_OF_RANGE)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                // Surfaced as a retryable message; nothing about the value is logged.
                _uiState.value = _uiState.value.copy(saveFailed = true)
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.value = TrendsUiState()
        isDraftEdited = false
        loadJob =
            viewModelScope.launch {
                try {
                    combine(
                        profileRepository.weights,
                        profileRepository.preferences,
                        currentDate,
                        selectedDate,
                    ) { weights, preferences, today, selected ->
                        Inputs(weights, preferences.unitSystem, today, selected ?: today)
                    }.collect { inputs -> _uiState.value = reduce(inputs) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (storageFailure: Exception) {
                    _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
                }
            }
    }

    private fun reduce(inputs: Inputs): TrendsUiState {
        val state = _uiState.value
        // A typed value is kept, unless the editor moved to another date or unit system: then it
        // would mean something else than the user typed.
        val refill =
            !isDraftEdited ||
                state.isLoading ||
                state.unitSystem != inputs.unitSystem ||
                state.editedDate != inputs.editedDate
        if (refill) isDraftEdited = false
        val storedKg = inputs.weights.firstOrNull { it.localDate == inputs.editedDate }?.kg
        return state.copy(
            isLoading = false,
            unitSystem = inputs.unitSystem,
            editedDate = inputs.editedDate,
            isEditingToday = inputs.editedDate == inputs.today,
            weightInput = if (refill) storedKg.formatInput(inputs.unitSystem) else state.weightInput,
            inputError = state.inputError.takeUnless { refill },
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

    private data class Inputs(
        val weights: List<WeightEntry>,
        val unitSystem: UnitSystem,
        val today: LocalDate,
        val editedDate: LocalDate,
    )
}
