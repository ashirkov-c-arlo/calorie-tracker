package app.kcal.feature.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kcal.R
import app.kcal.core.common.DecimalText
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.ui.currentLocale
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.usecase.BodyMetrics
import app.kcal.feature.profile.ProfileFieldError

/**
 * Target weight is chosen with a slider bounded by the reference body mass index range for
 * the entered height, so the value is always inside that range. Nothing is preselected: the
 * readout stays empty until the user moves the slider or a stored value exists. The view
 * model quantises the reported value, so the slider itself stays continuous.
 */
@Composable
fun TargetWeightSlider(
    valueKg: Double?,
    rangeKg: ClosedFloatingPointRange<Double>?,
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
            )
        }

        if (rangeKg == null) {
            Text(
                text = stringResource(R.string.target_weight_needs_height),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Slider(
            value = (valueKg ?: rangeKg.start).toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = rangeKg.start.toFloat()..rangeKg.endInclusive.toFloat(),
        )
        Text(
            text =
            stringResource(
                R.string.target_weight_range_hint,
                DecimalText.format(BodyMetrics.MIN_REFERENCE_BMI, locale),
                DecimalText.format(BodyMetrics.MAX_REFERENCE_BMI, locale),
                formatWeight(rangeKg.start, metric, locale),
                formatWeight(rangeKg.endInclusive, metric, locale),
                unitLabel,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatWeight(kilograms: Double, metric: Boolean, locale: java.util.Locale): String = DecimalText.format(
    if (metric) kilograms else app.kcal.domain.usecase.UnitConversions.kilogramsToPounds(kilograms),
    locale,
)
