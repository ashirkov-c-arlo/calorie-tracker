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
            _uiState.value =
                ProfileFormUiState(
                    isLoading = false,
                    fields = preferences.profile.toFields(preferences.unitSystem),
                    unitSystem = preferences.unitSystem,
                    appLanguage = preferences.appLanguage,
                    themeMode = preferences.themeMode,
                    target = previewFor(preferences.profile.toFields(preferences.unitSystem), preferences.unitSystem),
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
        _uiState.value =
            state.copy(
                unitSystem = unitSystem,
                fields = canonical.toFields(unitSystem, keepEnumsFrom = state.fields),
                errors = ProfileFormErrors(),
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
            _uiState.value = state.copy(errors = errors)
            return
        }
        _uiState.value = state.copy(errors = ProfileFormErrors())
        viewModelScope.launch {
            val result = saveProfile(state.fields.toStoredProfile(state.unitSystem))
            _uiState.value = _uiState.value.copy(target = result.toTargetPreview())
        }
    }

    private fun updateFields(transform: (ProfileFormFields) -> ProfileFormFields) {
        val state = _uiState.value
        val fields = transform(state.fields)
        _uiState.value = state.copy(fields = fields, target = previewFor(fields, state.unitSystem))
    }

    private fun previewFor(fields: ProfileFormFields, unitSystem: UnitSystem): TargetPreview =
        calculateDailyTargets.forStoredProfile(fields.toStoredProfile(unitSystem)).toTargetPreview()

    private fun validate(fields: ProfileFormFields, unitSystem: UnitSystem): ProfileFormErrors {
        val weightRange = if (unitSystem == UnitSystem.METRIC) METRIC_WEIGHT_RANGE else IMPERIAL_WEIGHT_RANGE
        val rateRange = if (unitSystem == UnitSystem.METRIC) METRIC_RATE_RANGE else IMPERIAL_RATE_RANGE
        return ProfileFormErrors(
            currentWeight = numberError(fields.currentWeight, weightRange),
            height = heightError(fields, unitSystem),
            age = intError(fields.age, AGE_RANGE),
            formulaVariant = ProfileFieldError.REQUIRED.takeIf { fields.energyEquationSex == null },
            activityLevel = ProfileFieldError.REQUIRED.takeIf { fields.activityLevel == null },
            targetWeight = numberError(fields.targetWeight, weightRange),
            lossRate = numberError(fields.lossRate, rateRange, allowZero = true),
        )
    }

    private fun heightError(fields: ProfileFormFields, unitSystem: UnitSystem): ProfileFieldError? =
        if (unitSystem == UnitSystem.METRIC) {
            numberError(fields.height, METRIC_HEIGHT_RANGE)
        } else {
            val feetError = intError(fields.heightFeet, FEET_RANGE)
            val inchesError = numberError(fields.heightInches, INCHES_RANGE, allowZero = true)
            feetError ?: inchesError
        }

    private fun numberError(
        text: String,
        range: ClosedFloatingPointRange<Double>,
        allowZero: Boolean = false,
    ): ProfileFieldError? {
        if (text.isBlank()) return ProfileFieldError.REQUIRED
        val value = DecimalText.parse(text) ?: return ProfileFieldError.INVALID_NUMBER
        if (allowZero && value == 0.0) return null
        return if (value in range) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun intError(text: String, range: IntRange): ProfileFieldError? {
        if (text.isBlank()) return ProfileFieldError.REQUIRED
        val value = DecimalText.parseInt(text) ?: return ProfileFieldError.INVALID_NUMBER
        return if (value in range) null else ProfileFieldError.OUT_OF_RANGE
    }

    private fun ProfileFormFields.toStoredProfile(unitSystem: UnitSystem): StoredProfile {
        val weight = DecimalText.parse(currentWeight)
        val targetWeightValue = DecimalText.parse(targetWeight)
        val rate = DecimalText.parse(lossRate)
        return StoredProfile(
            currentWeightKg = weight?.let {
                if (unitSystem ==
                    UnitSystem.METRIC
                ) {
                    it
                } else {
                    UnitConversions.poundsToKilograms(it)
                }
            },
            heightCm = heightCm(unitSystem),
            ageYears = DecimalText.parseInt(age),
            energyEquationSex = energyEquationSex,
            activityLevel = activityLevel,
            targetWeightKg =
            targetWeightValue?.let {
                if (unitSystem == UnitSystem.METRIC) it else UnitConversions.poundsToKilograms(it)
            },
            requestedLossRateKgPerWeek =
            rate?.let {
                if (unitSystem == UnitSystem.METRIC) it else UnitConversions.poundsPerWeekToKilogramsPerWeek(it)
            },
        )
    }

    private fun ProfileFormFields.heightCm(unitSystem: UnitSystem): Double? = if (unitSystem == UnitSystem.METRIC) {
        DecimalText.parse(height)
    } else {
        val feet = DecimalText.parseInt(heightFeet)
        val inches = DecimalText.parse(heightInches) ?: 0.0
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
            currentWeight =
            currentWeightKg?.let {
                DecimalText.format(if (metric) it else UnitConversions.kilogramsToPounds(it), locale)
            }
                .orEmpty(),
            height = if (metric) heightCm?.let { DecimalText.format(it, locale) }.orEmpty() else "",
            heightFeet = if (metric) "" else feetAndInches?.feet?.toString().orEmpty(),
            heightInches = if (metric) "" else feetAndInches?.inches?.let { DecimalText.format(it, locale) }.orEmpty(),
            age = ageYears?.toString().orEmpty(),
            energyEquationSex = energyEquationSex ?: keepEnumsFrom?.energyEquationSex,
            activityLevel = activityLevel ?: keepEnumsFrom?.activityLevel,
            targetWeight =
            targetWeightKg?.let {
                DecimalText.format(if (metric) it else UnitConversions.kilogramsToPounds(it), locale)
            }
                .orEmpty(),
            lossRate =
            requestedLossRateKgPerWeek
                ?.let {
                    DecimalText.format(
                        if (metric) it else UnitConversions.kilogramsPerWeekToPoundsPerWeek(it),
                        locale,
                        decimals = 2,
                    )
                }
                .orEmpty(),
        )
    }

    private companion object {
        val METRIC_WEIGHT_RANGE = 20.0..400.0
        val IMPERIAL_WEIGHT_RANGE = 44.0..882.0
        val METRIC_HEIGHT_RANGE = 50.0..250.0
        val METRIC_RATE_RANGE = 0.0..5.0
        val IMPERIAL_RATE_RANGE = 0.0..11.0
        val INCHES_RANGE = 0.0..11.9
        val AGE_RANGE = 1..120
        val FEET_RANGE = 1..8
    }
}
