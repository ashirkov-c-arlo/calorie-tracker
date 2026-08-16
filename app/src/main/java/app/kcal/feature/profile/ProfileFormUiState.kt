package app.kcal.feature.profile

import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.LossPaceOptions
import app.kcal.domain.model.Macros
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.usecase.DailyTargetResult
import app.kcal.domain.usecase.DailyTargetUnavailableReason
import app.kcal.domain.usecase.DailyTargetWarning
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

/** Why a single field cannot be accepted. Rendered next to that field. */
enum class ProfileFieldError {
    REQUIRED,
    INVALID_NUMBER,
    OUT_OF_RANGE,
}

data class ProfileFormErrors(
    val currentWeight: ProfileFieldError? = null,
    val height: ProfileFieldError? = null,
    val heightFeet: ProfileFieldError? = null,
    val heightInches: ProfileFieldError? = null,
    val age: ProfileFieldError? = null,
    val formulaVariant: ProfileFieldError? = null,
    val activityLevel: ProfileFieldError? = null,
    val targetWeight: ProfileFieldError? = null,
    val lossPace: ProfileFieldError? = null,
) {
    val hasAny: Boolean
        get() = currentWeight != null ||
            height != null ||
            heightFeet != null ||
            heightInches != null ||
            age != null ||
            formulaVariant != null ||
            activityLevel != null ||
            targetWeight != null ||
            lossPace != null
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
    /** Canonical kilograms picked with the slider; null until the user chooses a value. */
    val targetWeightKg: Double? = null,
    /** Which position of the offered deficit range is selected. */
    val lossPace: LossPace? = null,
)

/** The estimate shown under the form, or the reason there is none. */
sealed interface TargetPreview {

    data class Available(
        val targets: Macros,
        val deficitKcal: Int,
        val effectiveLossRateKgPerWeek: Double,
        /** Every guardrail that changed the deficit, in decreasing severity. */
        val warnings: PersistentList<DailyTargetWarning>,
    ) : TargetPreview

    data class Unavailable(val reason: DailyTargetUnavailableReason) : TargetPreview
}

data class ProfileFormUiState(
    val isLoading: Boolean = true,
    val fields: ProfileFormFields = ProfileFormFields(),
    val errors: ProfileFormErrors = ProfileFormErrors(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /** Target weights within the reference body mass index range for the entered height. */
    val targetWeightRangeKg: ClosedFloatingPointRange<Double>? = null,
    /** The three offered positions with their estimated weekly loss, or null when none applies. */
    val lossPaceOptions: LossPaceOptions? = null,
    /** The body mass index is below the reference range: no deficit, so no position to pick. */
    val noDeficitApplies: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val target: TargetPreview = TargetPreview.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
    /** A save is in flight; further taps are ignored so two saves cannot interleave. */
    val isSaving: Boolean = false,
    /** The profile was stored, but keeping the target in sync failed. */
    val saveFailed: Boolean = false,
)

/** Guardrails are rendered from the strictest to the mildest, and none of them is dropped. */
private val WARNING_ORDER =
    persistentListOf(
        DailyTargetWarning.NO_DEFICIT_BELOW_REFERENCE_BMI,
        DailyTargetWarning.TARGET_WEIGHT_REACHED,
        DailyTargetWarning.DEFICIT_CAPPED,
    )

internal fun DailyTargetResult.toTargetPreview(): TargetPreview = when (this) {
    is DailyTargetResult.Available ->
        TargetPreview.Available(
            targets = targets,
            deficitKcal = deficitKcal,
            effectiveLossRateKgPerWeek = effectiveLossRateKgPerWeek,
            warnings = WARNING_ORDER.filter { it in warnings }.toPersistentList(),
        )

    is DailyTargetResult.Unavailable -> TargetPreview.Unavailable(reason)
}
