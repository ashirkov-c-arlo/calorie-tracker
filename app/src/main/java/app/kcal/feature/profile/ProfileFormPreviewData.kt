package app.kcal.feature.profile

import app.kcal.core.common.DecimalText
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.DeficitBand
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.usecase.BodyMetrics
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.SuggestLossPaces
import java.util.Locale

/**
 * Preview and screenshot fixtures. The offered positions and the shown estimate are produced by
 * the real use cases, so a preview can never display invented numbers.
 */
private val calculate = CalculateDailyTargets()
private val suggestLossPaces = SuggestLossPaces(calculate)

private const val CURRENT_WEIGHT_KG = 82.4
private const val HEIGHT_CM = 176.0
private const val AGE_YEARS = 34
private const val TARGET_WEIGHT_KG = 72.0

internal val emptyProfileFormUiState = ProfileFormUiState(isLoading = false)

internal val filledProfileFormUiState = previewState(LossPace.MODERATE)

/** 130 kg at 176 cm asks for 25% of energy expenditure and is capped at 750 kcal. */
internal val guardedProfileFormUiState = previewState(LossPace.FAST, currentWeightKg = 130.0)

/** 50 kg at 176 cm is below the reference body mass index, so no position is offered. */
internal val noDeficitProfileFormUiState = previewState(pace = null, currentWeightKg = 50.0)

internal val russianProfileFormUiState =
    previewState(LossPace.FAST, currentWeightKg = 130.0, locale = Locale.forLanguageTag("ru"))

/** The state a user sees after pressing Save on an invalid form. */
internal val invalidProfileFormUiState =
    ProfileFormUiState(
        isLoading = false,
        fields = ProfileFormFields(currentWeight = "abc", height = "500", age = "34"),
        errors =
        ProfileFormErrors(
            currentWeight = ProfileFieldError.INVALID_NUMBER,
            height = ProfileFieldError.OUT_OF_RANGE,
            formulaVariant = ProfileFieldError.REQUIRED,
            activityLevel = ProfileFieldError.REQUIRED,
            targetWeight = ProfileFieldError.REQUIRED,
        ),
    )

private fun previewState(
    pace: LossPace?,
    currentWeightKg: Double = CURRENT_WEIGHT_KG,
    locale: Locale = Locale.US,
): ProfileFormUiState {
    val profile =
        StoredProfile(
            currentWeightKg = currentWeightKg,
            heightCm = HEIGHT_CM,
            ageYears = AGE_YEARS,
            energyEquationSex = EnergyEquationSex.MALE,
            activityLevel = ActivityLevel.LIGHT,
            targetWeightKg = TARGET_WEIGHT_KG,
            lossPace = pace,
        )
    val paceOptions = suggestLossPaces(profile)
    return ProfileFormUiState(
        isLoading = false,
        fields =
        ProfileFormFields(
            currentWeight = DecimalText.format(currentWeightKg, locale),
            height = DecimalText.format(HEIGHT_CM, locale),
            age = AGE_YEARS.toString(),
            energyEquationSex = profile.energyEquationSex,
            activityLevel = profile.activityLevel,
            targetWeightKg = TARGET_WEIGHT_KG,
            lossPace = pace,
        ),
        targetWeightRangeKg = BodyMetrics.targetWeightRangeKg(HEIGHT_CM),
        lossPaceOptions = paceOptions,
        noDeficitApplies = paceOptions == null && DeficitBand.bodyMassIndex(currentWeightKg, HEIGHT_CM) != null,
        target = calculate.forStoredProfile(profile).toTargetPreview(),
    )
}
