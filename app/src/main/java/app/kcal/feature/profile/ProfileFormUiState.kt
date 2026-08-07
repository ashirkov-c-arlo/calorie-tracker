package app.kcal.feature.profile

import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.Macros
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.usecase.DailyTargetResult
import app.kcal.domain.usecase.DailyTargetUnavailableReason
import app.kcal.domain.usecase.DailyTargetWarning

/** Why a single field cannot be accepted. Rendered next to that field. */
enum class ProfileFieldError {
    REQUIRED,
    INVALID_NUMBER,
    OUT_OF_RANGE,
}

data class ProfileFormErrors(
    val currentWeight: ProfileFieldError? = null,
    val height: ProfileFieldError? = null,
    val age: ProfileFieldError? = null,
    val formulaVariant: ProfileFieldError? = null,
    val activityLevel: ProfileFieldError? = null,
    val targetWeight: ProfileFieldError? = null,
    val lossRate: ProfileFieldError? = null,
) {
    val hasAny: Boolean
        get() = currentWeight != null ||
            height != null ||
            age != null ||
            formulaVariant != null ||
            activityLevel != null ||
            targetWeight != null ||
            lossRate != null
}

/**
 * Raw field text exactly as typed, so a partially entered value such as `82,` survives
 * recomposition and locale changes. Canonical metric values are derived on save.
 */
data class ProfileFormFields(
    val currentWeight: String = "",
    val height: String = "",
    val heightFeet: String = "",
    val heightInches: String = "",
    val age: String = "",
    val energyEquationSex: EnergyEquationSex? = null,
    val activityLevel: ActivityLevel? = null,
    val targetWeight: String = "",
    val lossRate: String = "",
)

/** The estimate shown under the form, or the reason there is none. */
sealed interface TargetPreview {

    data class Available(
        val targets: Macros,
        val requestedLossRateKgPerWeek: Double,
        val effectiveLossRateKgPerWeek: Double,
        /** The strictest guardrail that changed the requested pace, if any. */
        val warning: DailyTargetWarning?,
    ) : TargetPreview {
        val paceDiffersFromRequest: Boolean
            get() = warning != null
    }

    data class Unavailable(val reason: DailyTargetUnavailableReason) : TargetPreview
}

data class ProfileFormUiState(
    val isLoading: Boolean = true,
    val fields: ProfileFormFields = ProfileFormFields(),
    val errors: ProfileFormErrors = ProfileFormErrors(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val target: TargetPreview = TargetPreview.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
)

/** The strictest guardrail is the one worth explaining, so warnings are ordered. */
private val WARNING_PRIORITY =
    listOf(
        DailyTargetWarning.TARGET_WEIGHT_REACHED,
        DailyTargetWarning.INTAKE_FLOOR_APPLIED,
        DailyTargetWarning.DEFICIT_CAPPED,
        DailyTargetWarning.RATE_LIMITED,
    )

internal fun DailyTargetResult.toTargetPreview(): TargetPreview = when (this) {
    is DailyTargetResult.Available ->
        TargetPreview.Available(
            targets = targets,
            requestedLossRateKgPerWeek = requestedLossRateKgPerWeek,
            effectiveLossRateKgPerWeek = effectiveLossRateKgPerWeek,
            warning = WARNING_PRIORITY.firstOrNull { it in warnings },
        )

    is DailyTargetResult.Unavailable -> TargetPreview.Unavailable(reason)
}
