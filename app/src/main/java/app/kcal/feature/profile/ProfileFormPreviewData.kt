package app.kcal.feature.profile

import app.kcal.core.common.DecimalText
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.usecase.BodyMetrics
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.SuggestLossPaces
import java.util.Locale

/**
 * Preview and screenshot fixtures. The offered paces and the shown estimate are produced by
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

internal val guardedProfileFormUiState = previewState(LossPace.FAST)

internal val russianProfileFormUiState = previewState(LossPace.FAST, locale = Locale.forLanguageTag("ru"))

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

private fun previewState(pace: LossPace, locale: Locale = Locale.US): ProfileFormUiState {
    val withoutRate =
        StoredProfile(
            currentWeightKg = CURRENT_WEIGHT_KG,
            heightCm = HEIGHT_CM,
            ageYears = AGE_YEARS,
            energyEquationSex = EnergyEquationSex.MALE,
            activityLevel = ActivityLevel.LIGHT,
            targetWeightKg = TARGET_WEIGHT_KG,
            requestedLossRateKgPerWeek = 0.0,
        )
    val paceOptions = suggestLossPaces(withoutRate)
    val profile = withoutRate.copy(requestedLossRateKgPerWeek = paceOptions?.rateFor(pace))
    return ProfileFormUiState(
        isLoading = false,
        fields =
        ProfileFormFields(
            currentWeight = DecimalText.format(CURRENT_WEIGHT_KG, locale),
            height = DecimalText.format(HEIGHT_CM, locale),
            age = AGE_YEARS.toString(),
            energyEquationSex = profile.energyEquationSex,
            activityLevel = profile.activityLevel,
            targetWeightKg = TARGET_WEIGHT_KG,
            lossPace = pace,
        ),
        targetWeightRangeKg = BodyMetrics.targetWeightRangeKg(HEIGHT_CM),
        lossPaceOptions = paceOptions,
        target = calculate.forStoredProfile(profile).toTargetPreview(),
    )
}
