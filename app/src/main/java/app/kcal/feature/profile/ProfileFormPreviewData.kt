package app.kcal.feature.profile

import app.kcal.core.common.DecimalText
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.usecase.CalculateDailyTargets
import java.util.Locale

/**
 * Preview and screenshot fixtures. The shown estimate is produced by the real use case, so
 * a preview can never display invented numbers.
 */
private val calculate = CalculateDailyTargets()

private val honouredPaceProfile =
    StoredProfile(
        currentWeightKg = 82.4,
        heightCm = 176.0,
        ageYears = 34,
        energyEquationSex = EnergyEquationSex.MALE,
        activityLevel = ActivityLevel.LIGHT,
        targetWeightKg = 78.0,
        requestedLossRateKgPerWeek = 0.3,
    )

private val guardedPaceProfile = honouredPaceProfile.copy(requestedLossRateKgPerWeek = 2.0)

internal val emptyProfileFormUiState = ProfileFormUiState(isLoading = false)

internal val filledProfileFormUiState = honouredPaceProfile.toPreviewState()

internal val guardedProfileFormUiState = guardedPaceProfile.toPreviewState()

private fun StoredProfile.toPreviewState(): ProfileFormUiState {
    val locale = Locale.US
    return ProfileFormUiState(
        isLoading = false,
        fields =
        ProfileFormFields(
            currentWeight = DecimalText.format(currentWeightKg!!, locale),
            height = DecimalText.format(heightCm!!, locale),
            age = ageYears!!.toString(),
            energyEquationSex = energyEquationSex,
            activityLevel = activityLevel,
            targetWeight = DecimalText.format(targetWeightKg!!, locale),
            lossRate = DecimalText.format(requestedLossRateKgPerWeek!!, locale, decimals = 2),
        ),
        target = calculate.forStoredProfile(this).toTargetPreview(),
    )
}
