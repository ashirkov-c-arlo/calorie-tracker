package app.kcal.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.DecimalText
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.repository.ProfileRepository
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.SaveProfile
import app.kcal.domain.usecase.UnitConversions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs both the required first-run form and Settings: identical fields, identical
 * validation. Units, language and theme are applied immediately, while calculator inputs
 * are stored only on save.
 *
 * Every entered value is converted to canonical metric first, so validation limits do not
 * depend on the displayed unit system.
 */
@HiltViewModel
class ProfileFormViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val saveProfile: SaveProfile,
    private val calculateDailyTargets: CalculateDailyTargets,
    private val localeProvider: AppLocaleProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileFormUiState())
    val uiState: StateFlow<ProfileFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val preferences = profileRepository.preferences.first()
            val fields = preferences.profile.toFields(preferences.unitSystem)
            _uiState.value =
                ProfileFormUiState(
                    isLoading = false,
                    fields = fields,
                    unitSystem = preferences.unitSystem,
                    appLanguage = preferences.appLanguage,
                    themeMode = preferences.themeMode,
                    target = previewFor(fields, preferences.unitSystem),
                )
        }
    }

    fun onCurrentWeightChange(text: String) = updateFields { it.copy(currentWeight = text) }

    fun onHeightChange(text: String) = updateFields { it.copy(height = text) }

    fun onHeightFeetChange(text: String) = updateFields { it.copy(heightFeet = text) }

    fun onHeightInchesChange(text: String) = updateFields { it.copy(heightInches = text) }

    fun onAgeChange(text: String) = updateFields { it.copy(age = text) }

    fun onTargetWeightChange(text: String) = updateFields { it.copy(targetWeight = text) }

    fun onLossRateChange(text: String) = updateFields { it.copy(lossRate = text) }

    fun onFormulaVariantSelect(value: EnergyEquationSex) = updateFields { it.copy(energyEquationSex = value) }

    fun onActivityLevelSelect(value: ActivityLevel) = updateFields { it.copy(activityLevel = value) }

    /** Re-renders the entered values in the new units; stored values stay metric. */
    fun onUnitSystemSelect(unitSystem: UnitSystem) {
        val state = _uiState.value
        if (state.unitSystem == unitSystem) return
        val canonical = state.fields.toStoredProfile(state.unitSystem)
        val fields = canonical.toFields(unitSystem, keepEnumsFrom = state.fields)
        _uiState.value =
            state.copy(
                unitSystem = unitSystem,
                fields = fields,
                errors = ProfileFormErrors(),
                target = previewFor(fields, unitSystem),
            )
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
        val errors = validate(state.fields, state.unitSystem)
        if (errors.hasAny) {
            _uiState.value = state.copy(errors = errors, saveFailed = false)
            return
        }
        _uiState.value = state.copy(errors = ProfileFormErrors(), saveFailed = false)
        viewModelScope.launch {
            try {
                val result = saveProfile(state.fields.toStoredProfile(state.unitSystem))
                _uiState.value = _uiState.value.copy(target = result.toTargetPreview())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (storageFailure: Exception) {
                // The profile itself may already be stored; the target is repaired on the
                // next start by ReconcileTodayTarget. Details are never logged in release.
                _uiState.value = _uiState.value.copy(saveFailed = true)
            }
        }
    }

    private fun updateFields(transform: (ProfileFormFields) -> ProfileFormFields) {
        val state = _uiState.value
        val fields = transform(state.fields)
        _uiState.value =
            state.copy(fields = fields, target = previewFor(fields, state.unitSystem), saveFailed = false)
    }

    private fun previewFor(fields: ProfileFormFields, unitSystem: UnitSystem): TargetPreview =
        calculateDailyTargets.forStoredProfile(fields.toStoredProfile(unitSystem)).toTargetPreview()

    /**
     * One validation policy, expressed in canonical metric units. Imperial input is
     * converted first, so the same value is accepted or rejected in both unit systems. The
     * requested loss rate has no upper product limit: it is preserved and the domain
     * guardrails cap the effective pace.
     */
    private fun validate(fields: ProfileFormFields, unitSystem: UnitSystem): ProfileFormErrors {
        val metric = unitSystem == UnitSystem.METRIC
        return ProfileFormErrors(
            currentWeight = weightError(fields.currentWeight, metric),
            height = if (metric) metricHeightError(fields.height) else null,
            heightFeet = if (metric) null else feetError(fields),
            heightInches = if (metric) null else inchesError(fields.heightInches),
            age = intError(fields.age, AGE_RANGE),
            formulaVariant = ProfileFieldError.REQUIRED.takeIf { fields.energyEquationSex == null },
            activityLevel = ProfileFieldError.REQUIRED.takeIf { fields.activityLevel == null },
            targetWeight = weightError(fields.targetWeight, metric),
            lossRate = rateError(fields.lossRate),
        )
    }

    private fun weightError(text: String, metric: Boolean): ProfileFieldError? {
        if (text.isBlank()) return ProfileFieldError.REQUIRED
        val entered = DecimalText.parse(text) ?: return ProfileFieldError.INVALID_NUMBER
        val kg = if (metric) entered else UnitConversions.poundsToKilograms(entered)
        return if (kg in WEIGHT_RANGE_KG) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun metricHeightError(text: String): ProfileFieldError? {
        if (text.isBlank()) return ProfileFieldError.REQUIRED
        val cm = DecimalText.parse(text) ?: return ProfileFieldError.INVALID_NUMBER
        return if (cm in HEIGHT_RANGE_CM) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun feetError(fields: ProfileFormFields): ProfileFieldError? {
        if (fields.heightFeet.isBlank()) return ProfileFieldError.REQUIRED
        val feet = DecimalText.parseInt(fields.heightFeet) ?: return ProfileFieldError.INVALID_NUMBER
        if (feet < 0) return ProfileFieldError.OUT_OF_RANGE
        val inches = DecimalText.parse(fields.heightInches.ifBlank { "0" }) ?: return null
        val cm = UnitConversions.feetAndInchesToCentimetres(feet, inches)
        return if (cm in HEIGHT_RANGE_CM) null else ProfileFieldError.OUT_OF_RANGE
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

    private fun rateError(text: String): ProfileFieldError? {
        if (text.isBlank()) return ProfileFieldError.REQUIRED
        val rate = DecimalText.parse(text) ?: return ProfileFieldError.INVALID_NUMBER
        return if (rate >= 0.0) null else ProfileFieldError.OUT_OF_RANGE
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
            targetWeightKg = DecimalText.parse(targetWeight)?.toKilograms(metric),
            requestedLossRateKgPerWeek =
            DecimalText.parse(lossRate)?.let {
                if (metric) it else UnitConversions.poundsPerWeekToKilogramsPerWeek(it)
            },
        )
    }

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
        keepEnumsFrom: ProfileFormFields? = null,
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
            energyEquationSex = energyEquationSex ?: keepEnumsFrom?.energyEquationSex,
            activityLevel = activityLevel ?: keepEnumsFrom?.activityLevel,
            targetWeight = targetWeightKg.formatWeight(metric, locale),
            lossRate =
            requestedLossRateKgPerWeek
                ?.let { if (metric) it else UnitConversions.kilogramsPerWeekToPoundsPerWeek(it) }
                ?.let { DecimalText.format(it, locale, decimals = 2) }
                .orEmpty(),
        )
    }

    private fun Double?.formatWeight(metric: Boolean, locale: java.util.Locale): String =
        this?.let { DecimalText.format(if (metric) it else UnitConversions.kilogramsToPounds(it), locale) }
            .orEmpty()

    private companion object {
        val WEIGHT_RANGE_KG = 20.0..400.0
        val HEIGHT_RANGE_CM = 50.0..250.0
        val AGE_RANGE = 1..120
    }
}
