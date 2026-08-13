package app.kcal.feature.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.kcal.R
import app.kcal.core.common.DecimalText
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.ui.currentLocale
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.usecase.BodyMetrics
import app.kcal.domain.usecase.UnitConversions
import app.kcal.feature.profile.ProfileFieldError
import java.util.Locale

/**
 * Target weight is chosen with a slider. The hint states the weight interval for the
 * reference body mass index range at the entered height, but the value is never coerced into
 * it: a stored value outside that interval keeps its place and simply widens the slider.
 * Nothing is preselected, so the readout stays "not selected" until the user picks a value.
 */
@Composable
fun TargetWeightSlider(
    valueKg: Double?,
    referenceRangeKg: ClosedFloatingPointRange<Double>?,
    unitSystem: UnitSystem,
    error: ProfileFieldError?,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val metric = unitSystem == UnitSystem.METRIC
    val unitLabel = stringResource(if (metric) R.string.unit_kg else R.string.unit_lb)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(R.string.field_target_weight), style = MaterialTheme.typography.titleSmall)
            Text(
                text =
                valueKg?.let { "${formatWeight(it, metric, locale)} $unitLabel" }
                    ?: stringResource(R.string.target_weight_unset),
                style = MaterialTheme.typography.titleSmall,
                color =
                if (valueKg == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        if (referenceRangeKg == null) {
            Text(
                text = stringResource(R.string.target_weight_needs_height),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val sliderRange = referenceRangeKg.widenedFor(valueKg)
            Slider(
                value = (valueKg ?: sliderRange.start).toFloat(),
                onValueChange = { onValueChange(it.toDouble()) },
                valueRange = sliderRange.start.toFloat()..sliderRange.endInclusive.toFloat(),
            )
            Text(
                text =
                stringResource(
                    R.string.target_weight_range_hint,
                    DecimalText.format(BodyMetrics.MIN_REFERENCE_BMI, locale),
                    DecimalText.format(BodyMetrics.MAX_REFERENCE_BMI, locale),
                    formatWeight(referenceRangeKg.start, metric, locale),
                    formatWeight(referenceRangeKg.endInclusive, metric, locale),
                    unitLabel,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Rendered in every branch, so a missing value is reported even without a height.
        if (error != null) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Keeps a value that lies outside the reference range reachable instead of clamping it. */
private fun ClosedFloatingPointRange<Double>.widenedFor(valueKg: Double?): ClosedFloatingPointRange<Double> {
    if (valueKg == null) return this
    return minOf(start, valueKg)..maxOf(endInclusive, valueKg)
}

private fun formatWeight(kilograms: Double, metric: Boolean, locale: Locale): String =
    DecimalText.format(if (metric) kilograms else UnitConversions.kilogramsToPounds(kilograms), locale)

@Composable
private fun TargetWeightSliderPreviewContent(
    valueKg: Double?,
    referenceRangeKg: ClosedFloatingPointRange<Double>?,
    error: ProfileFieldError?,
) {
    TargetWeightSlider(
        valueKg = valueKg,
        referenceRangeKg = referenceRangeKg,
        unitSystem = UnitSystem.METRIC,
        error = error,
        onValueChange = {},
        modifier = Modifier.padding(KcalSpacing.medium),
    )
}

@Preview(name = "Target weight content White")
@Composable
private fun TargetWeightSliderWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        TargetWeightSliderPreviewContent(72.0, BodyMetrics.targetWeightRangeKg(176.0), null)
    }
}

@Preview(name = "Target weight content Black")
@Composable
private fun TargetWeightSliderBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        TargetWeightSliderPreviewContent(72.0, BodyMetrics.targetWeightRangeKg(176.0), null)
    }
}

@Preview(name = "Target weight unselected White")
@Composable
private fun TargetWeightSliderUnselectedWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        TargetWeightSliderPreviewContent(null, BodyMetrics.targetWeightRangeKg(176.0), null)
    }
}

@Preview(name = "Target weight without height Black")
@Composable
private fun TargetWeightSliderNoHeightBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) {
        TargetWeightSliderPreviewContent(null, null, ProfileFieldError.REQUIRED)
    }
}

@Preview(name = "Target weight outside the reference range White")
@Composable
private fun TargetWeightSliderOutsideRangeWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        TargetWeightSliderPreviewContent(95.0, BodyMetrics.targetWeightRangeKg(176.0), null)
    }
}
