package app.kcal.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.DecimalText
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.BodyMetrics
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.SaveProfile
import app.kcal.domain.usecase.SuggestLossPaces
import app.kcal.domain.usecase.UnitConversions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Backs both the required first-run form and Settings: identical fields, identical
 * validation. Units, language and theme are applied immediately, while calculator inputs
 * are stored only on save.
 *
 * Typed values are converted to canonical metric before validation, so limits do not depend
 * on the displayed unit system. The target weight is picked from the reference body mass
 * index range for the entered height, and the loss pace is one of three options derived from
 * the profile, so every choice visibly changes the calculated target.
 */
@HiltViewModel
class ProfileFormViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val saveProfile: SaveProfile,
    private val calculateDailyTargets: CalculateDailyTargets,
    private val suggestLossPaces: SuggestLossPaces,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileFormUiState())
    val uiState: StateFlow<ProfileFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val preferences = profileRepository.preferences.first()
            _uiState.value =
                ProfileFormUiState(
                    isLoading = false,
                    unitSystem = preferences.unitSystem,
                    appLanguage = preferences.appLanguage,
                    themeMode = preferences.themeMode,
                ).withFields(preferences.profile.toFields(preferences.unitSystem))
        }
    }

    fun onCurrentWeightChange(text: String) = updateFields { it.copy(currentWeight = text) }

    fun onHeightChange(text: String) = updateFields { it.copy(height = text) }

    fun onHeightFeetChange(text: String) = updateFields { it.copy(heightFeet = text) }

    fun onHeightInchesChange(text: String) = updateFields { it.copy(heightInches = text) }

    fun onAgeChange(text: String) = updateFields { it.copy(age = text) }

    /**
     * The slider reports canonical kilograms, so no unit parsing is involved. The value is
     * quantised to half kilograms, which is finer than the displayed precision.
     */
    fun onTargetWeightChange(kilograms: Double) =
        updateFields { it.copy(targetWeightKg = kilograms.roundToStep(TARGET_WEIGHT_STEP_KG)) }

    fun onLossPaceSelect(pace: LossPace) = updateFields { fields ->
        val rate = _uiState.value.lossPaceOptions?.rateFor(pace)
        fields.copy(lossPace = pace, requestedLossRateKgPerWeek = rate ?: fields.requestedLossRateKgPerWeek)
    }

    fun onFormulaVariantSelect(value: EnergyEquationSex) = updateFields { it.copy(energyEquationSex = value) }

    fun onActivityLevelSelect(value: ActivityLevel) = updateFields { it.copy(activityLevel = value) }

    /** Re-renders the entered values in the new units; stored values stay metric. */
    fun onUnitSystemSelect(unitSystem: UnitSystem) {
        val state = _uiState.value
        if (state.unitSystem == unitSystem) return
        val canonical = state.fields.toStoredProfile(state.unitSystem)
        _uiState.value =
            state.copy(unitSystem = unitSystem, errors = ProfileFormErrors())
                .withFields(canonical.toFields(unitSystem, keepFrom = state.fields))
        viewModelScope.launch { profileRepository.setUnitSystem(unitSystem) }
    }

    fun onAppLanguageSelect(appLanguage: AppLanguage) {
        _uiState.value = _uiState.value.copy(appLanguage = appLanguage)
        viewModelScope.launch { profileRepository.setAppLanguage(appLanguage) }
    }

    fun onThemeModeSelect(themeMode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = themeMode)
        viewModelScope.launch { profileRepository.setThemeMode(themeMode) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving) return
        val errors = validate(state)
        if (errors.hasAny) {
            _uiState.value = state.copy(errors = errors, saveFailed = false)
            return
        }
        _uiState.value = state.copy(errors = ProfileFormErrors(), saveFailed = false, isSaving = true)
        viewModelScope.launch {
            try {
                val result = saveProfile(state.fields.toStoredProfile(state.unitSystem))
                _uiState.value = _uiState.value.copy(target = result.toTargetPreview())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                // Part of the save may already be stored; the app shell writes today's target
                // from the stored profile. Nothing is logged.
                _uiState.value = _uiState.value.copy(saveFailed = true)
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    private fun updateFields(transform: (ProfileFormFields) -> ProfileFormFields) {
        val state = _uiState.value
        _uiState.value = state.copy(saveFailed = false).withFields(transform(state.fields))
    }

    /**
     * Recomputes everything derived from the entered values: the reference target weight
     * range, the three offered paces and the estimate itself.
     */
    private fun ProfileFormUiState.withFields(fields: ProfileFormFields): ProfileFormUiState {
        val profile = fields.toStoredProfile(unitSystem)
        // Entered values are never silently changed: the reference range is advisory and the
        // offered paces depend on body weight alone.
        val paceOptions = suggestLossPaces(profile)
        return copy(
            fields = fields.copy(lossPace = fields.requestedLossRateKgPerWeek?.let { paceOptions?.paceFor(it) }),
            targetWeightRangeKg = BodyMetrics.targetWeightRangeKg(profile.heightCm),
            lossPaceOptions = paceOptions,
            target = calculateDailyTargets.forStoredProfile(profile).toTargetPreview(),
        )
    }

    /**
     * One validation policy, expressed in canonical metric units. The requested pace has no
     * numeric limit of its own: the offered options already respect the domain guardrails.
     */
    private fun validate(state: ProfileFormUiState): ProfileFormErrors {
        val fields = state.fields
        val metric = state.unitSystem == UnitSystem.METRIC
        return ProfileFormErrors(
            currentWeight = weightError(fields.currentWeight, metric),
            height = if (metric) metricHeightError(fields.height) else null,
            heightFeet = if (metric) null else feetError(fields),
            heightInches = if (metric) null else inchesError(fields.heightInches),
            age = intError(fields.age, AGE_RANGE),
            formulaVariant = ProfileFieldError.REQUIRED.takeIf { fields.energyEquationSex == null },
            activityLevel = ProfileFieldError.REQUIRED.takeIf { fields.activityLevel == null },
            targetWeight = targetWeightError(fields),
            lossRate = rateError(fields),
        )
    }

    private fun targetWeightError(fields: ProfileFormFields): ProfileFieldError? {
        val kilograms = fields.targetWeightKg ?: return ProfileFieldError.REQUIRED
        return if (kilograms in WEIGHT_RANGE_KG) null else ProfileFieldError.OUT_OF_RANGE
    }

    /** The intended pace is required and is stored exactly as chosen. */
    private fun rateError(fields: ProfileFormFields): ProfileFieldError? {
        val rate = fields.requestedLossRateKgPerWeek ?: return ProfileFieldError.REQUIRED
        return if (rate.isFinite() && rate >= 0.0) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun weightError(text: String, metric: Boolean): ProfileFieldError? {
        if (text.isBlank()) return ProfileFieldError.REQUIRED
        val entered = DecimalText.parse(text) ?: return ProfileFieldError.INVALID_NUMBER
        val kilograms = if (metric) entered else UnitConversions.poundsToKilograms(entered)
        return if (kilograms in WEIGHT_RANGE_KG) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun metricHeightError(text: String): ProfileFieldError? {
        if (text.isBlank()) return ProfileFieldError.REQUIRED
        val centimetres = DecimalText.parse(text) ?: return ProfileFieldError.INVALID_NUMBER
        return if (centimetres in HEIGHT_RANGE_CM) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun feetError(fields: ProfileFormFields): ProfileFieldError? {
        if (fields.heightFeet.isBlank()) return ProfileFieldError.REQUIRED
        val feet = DecimalText.parseInt(fields.heightFeet) ?: return ProfileFieldError.INVALID_NUMBER
        if (feet < 0) return ProfileFieldError.OUT_OF_RANGE
        val inches = DecimalText.parse(fields.heightInches.ifBlank { "0" }) ?: return null
        val centimetres = UnitConversions.feetAndInchesToCentimetres(feet, inches)
        return if (centimetres in HEIGHT_RANGE_CM) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun inchesError(text: String): ProfileFieldError? {
        if (text.isBlank()) return null
        val inches = DecimalText.parse(text) ?: return ProfileFieldError.INVALID_NUMBER
        return if (inches >= 0.0 && inches < UnitConversions.INCHES_PER_FOOT) {
            null
        } else {
            ProfileFieldError.OUT_OF_RANGE
        }
    }

    private fun intError(text: String, range: IntRange): ProfileFieldError? {
        if (text.isBlank()) return ProfileFieldError.REQUIRED
        val value = DecimalText.parseInt(text) ?: return ProfileFieldError.INVALID_NUMBER
        return if (value in range) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun ProfileFormFields.toStoredProfile(unitSystem: UnitSystem): StoredProfile {
        val metric = unitSystem == UnitSystem.METRIC
        return StoredProfile(
            currentWeightKg = DecimalText.parse(currentWeight)?.toKilograms(metric),
            heightCm = heightCm(metric),
            ageYears = DecimalText.parseInt(age),
            energyEquationSex = energyEquationSex,
            activityLevel = activityLevel,
            targetWeightKg = targetWeightKg,
            requestedLossRateKgPerWeek = requestedLossRateKgPerWeek,
        )
    }

    private fun Double.roundToStep(step: Double): Double = kotlin.math.round(this / step) * step

    private fun Double.toKilograms(metric: Boolean): Double =
        if (metric) this else UnitConversions.poundsToKilograms(this)

    private fun ProfileFormFields.heightCm(metric: Boolean): Double? = if (metric) {
        DecimalText.parse(height)
    } else {
        val feet = DecimalText.parseInt(heightFeet)
        val inches = DecimalText.parse(heightInches.ifBlank { "0" }) ?: 0.0
        feet?.let { UnitConversions.feetAndInchesToCentimetres(it, inches) }
    }

    private fun StoredProfile.toFields(
        unitSystem: UnitSystem,
        keepFrom: ProfileFormFields? = null,
    ): ProfileFormFields {
        val locale = localeProvider.current()
        val metric = unitSystem == UnitSystem.METRIC
        val feetAndInches = heightCm?.let(UnitConversions::centimetresToFeetAndInches)
        return ProfileFormFields(
            currentWeight = currentWeightKg.formatWeight(metric, locale),
            height = if (metric) heightCm?.let { DecimalText.format(it, locale) }.orEmpty() else "",
            heightFeet = if (metric) "" else feetAndInches?.feet?.toString().orEmpty(),
            heightInches = if (metric) "" else feetAndInches?.inches?.let { DecimalText.format(it, locale) }.orEmpty(),
            age = ageYears?.toString().orEmpty(),
            energyEquationSex = energyEquationSex ?: keepFrom?.energyEquationSex,
            activityLevel = activityLevel ?: keepFrom?.activityLevel,
            targetWeightKg = targetWeightKg ?: keepFrom?.targetWeightKg,
            requestedLossRateKgPerWeek = requestedLossRateKgPerWeek ?: keepFrom?.requestedLossRateKgPerWeek,
            lossPace = null,
        )
    }

    private fun Double?.formatWeight(metric: Boolean, locale: Locale): String =
        this?.let { DecimalText.format(if (metric) it else UnitConversions.kilogramsToPounds(it), locale) }
            .orEmpty()

    private companion object {
        val WEIGHT_RANGE_KG = BodyMetrics.PLAUSIBLE_WEIGHT_RANGE_KG
        val HEIGHT_RANGE_CM = 50.0..250.0
        val AGE_RANGE = 1..120
        const val TARGET_WEIGHT_STEP_KG = 0.5
    }
}
