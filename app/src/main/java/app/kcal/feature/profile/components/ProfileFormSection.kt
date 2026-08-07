package app.kcal.feature.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.kcal.R
import app.kcal.core.common.DecimalText
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.ui.currentLocale
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.usecase.DailyTargetUnavailableReason
import app.kcal.domain.usecase.DailyTargetWarning
import app.kcal.domain.usecase.UnitConversions
import app.kcal.feature.profile.ProfileFormErrors
import app.kcal.feature.profile.ProfileFormFields
import app.kcal.feature.profile.TargetPreview

/**
 * The calculator inputs. Identical on the required first-run form and in Settings, so both
 * screens share the same fields and the same validation messages.
 */
@Composable
fun ProfileFormSection(
    fields: ProfileFormFields,
    errors: ProfileFormErrors,
    unitSystem: UnitSystem,
    onCurrentWeightChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onHeightFeetChange: (String) -> Unit,
    onHeightInchesChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onFormulaVariantSelect: (EnergyEquationSex) -> Unit,
    onActivityLevelSelect: (ActivityLevel) -> Unit,
    onTargetWeightChange: (String) -> Unit,
    onLossRateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metric = unitSystem == UnitSystem.METRIC
    val weightUnit = stringResource(if (metric) R.string.unit_kg else R.string.unit_lb)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
    ) {
        DecimalField(
            label = stringResource(R.string.field_current_weight),
            value = fields.currentWeight,
            unitLabel = weightUnit,
            error = errors.currentWeight,
            onValueChange = onCurrentWeightChange,
        )

        if (metric) {
            DecimalField(
                label = stringResource(R.string.field_height),
                value = fields.height,
                unitLabel = stringResource(R.string.unit_cm),
                error = errors.height,
                onValueChange = onHeightChange,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small)) {
                DecimalField(
                    label = stringResource(R.string.field_height_feet),
                    value = fields.heightFeet,
                    unitLabel = stringResource(R.string.unit_ft),
                    error = errors.height,
                    onValueChange = onHeightFeetChange,
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                )
                DecimalField(
                    label = stringResource(R.string.field_height_inches),
                    value = fields.heightInches,
                    unitLabel = stringResource(R.string.unit_in),
                    error = null,
                    onValueChange = onHeightInchesChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        DecimalField(
            label = stringResource(R.string.field_age),
            value = fields.age,
            unitLabel = "",
            error = errors.age,
            onValueChange = onAgeChange,
            keyboardType = KeyboardType.Number,
        )

        SingleChoiceField(
            label = stringResource(R.string.field_formula_variant),
            options = EnergyEquationSex.entries,
            selected = fields.energyEquationSex,
            optionLabel = { stringResource(it.labelRes()) },
            error = errors.formulaVariant,
            onSelect = onFormulaVariantSelect,
            supportingText = stringResource(R.string.field_formula_variant_hint),
        )

        SingleChoiceField(
            label = stringResource(R.string.field_activity),
            options = ActivityLevel.entries,
            selected = fields.activityLevel,
            optionLabel = { stringResource(it.labelRes()) },
            error = errors.activityLevel,
            onSelect = onActivityLevelSelect,
        )

        DecimalField(
            label = stringResource(R.string.field_target_weight),
            value = fields.targetWeight,
            unitLabel = weightUnit,
            error = errors.targetWeight,
            onValueChange = onTargetWeightChange,
        )

        DecimalField(
            label = stringResource(R.string.field_loss_rate),
            value = fields.lossRate,
            unitLabel = stringResource(if (metric) R.string.unit_kg_per_week else R.string.unit_lb_per_week),
            error = errors.lossRate,
            onValueChange = onLossRateChange,
        )
    }
}

/** The locally calculated estimate, or a localized explanation of why there is none. */
@Composable
fun TargetPreviewCard(target: TargetPreview, unitSystem: UnitSystem, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.extraSmall),
        ) {
            Text(text = stringResource(R.string.target_title), style = MaterialTheme.typography.titleSmall)
            when (target) {
                is TargetPreview.Unavailable ->
                    Text(
                        text = stringResource(target.reason.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                is TargetPreview.Available -> {
                    Text(
                        text = stringResource(R.string.target_kcal, DecimalText.formatInt(target.targets.kcal, locale)),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text =
                        stringResource(
                            R.string.target_macros,
                            DecimalText.format(target.targets.proteinG, locale),
                            DecimalText.format(target.targets.fatG, locale),
                            DecimalText.format(target.targets.carbsG, locale),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                        stringResource(
                            R.string.target_rate_effective,
                            formatRate(target.effectiveLossRateKgPerWeek, unitSystem, locale),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (target.paceDiffersFromRequest) {
                        Text(
                            text =
                            stringResource(
                                R.string.target_rate_requested,
                                formatRate(target.requestedLossRateKgPerWeek, unitSystem, locale),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    target.warning?.let { warning ->
                        Text(
                            text = stringResource(warning.messageRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatRate(kgPerWeek: Double, unitSystem: UnitSystem, locale: java.util.Locale): String =
    if (unitSystem == UnitSystem.METRIC) {
        "${DecimalText.format(kgPerWeek, locale, decimals = 2)} ${stringResource(R.string.unit_kg)}"
    } else {
        val pounds = UnitConversions.kilogramsPerWeekToPoundsPerWeek(kgPerWeek)
        "${DecimalText.format(pounds, locale, decimals = 2)} ${stringResource(R.string.unit_lb)}"
    }

internal fun EnergyEquationSex.labelRes(): Int = when (this) {
    EnergyEquationSex.FEMALE -> R.string.formula_variant_female
    EnergyEquationSex.MALE -> R.string.formula_variant_male
}

internal fun ActivityLevel.labelRes(): Int = when (this) {
    ActivityLevel.SEDENTARY -> R.string.activity_sedentary
    ActivityLevel.LIGHT -> R.string.activity_light
    ActivityLevel.MODERATE -> R.string.activity_moderate
    ActivityLevel.HIGH -> R.string.activity_high
}

internal fun DailyTargetWarning.messageRes(): Int = when (this) {
    DailyTargetWarning.RATE_LIMITED -> R.string.warning_rate_limited
    DailyTargetWarning.DEFICIT_CAPPED -> R.string.warning_deficit_capped
    DailyTargetWarning.INTAKE_FLOOR_APPLIED -> R.string.warning_intake_floor
    DailyTargetWarning.TARGET_WEIGHT_REACHED -> R.string.warning_target_reached
}

internal fun DailyTargetUnavailableReason.messageRes(): Int = when (this) {
    DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS -> R.string.unavailable_missing_inputs
    DailyTargetUnavailableReason.AGE_BELOW_MINIMUM -> R.string.unavailable_age_below_minimum
    DailyTargetUnavailableReason.INVALID_MEASUREMENTS -> R.string.unavailable_invalid_measurements
    DailyTargetUnavailableReason.NON_POSITIVE_ENERGY -> R.string.unavailable_non_positive_energy
}
