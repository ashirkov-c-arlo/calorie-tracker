package app.kcal.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.kcal.R
import app.kcal.core.common.DecimalText
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.MacroTotals
import app.kcal.domain.model.Macros
import app.kcal.domain.model.ThemeMode
import java.math.BigDecimal
import java.math.MathContext

/**
 * Calories and every macro of one day against the target that applies to it. Fractions are
 * prepared outside Compose so screens stay presentation-only.
 */
data class MacroProgressUiState(
    val consumed: MacroTotals,
    val target: Macros,
    val kcalFraction: Float,
    val proteinFraction: Float,
    val fatFraction: Float,
    val carbsFraction: Float,
)

fun macroProgress(consumed: MacroTotals, target: Macros): MacroProgressUiState = MacroProgressUiState(
    consumed = consumed,
    target = target,
    kcalFraction = ratio(consumed.kcal, target.kcal),
    proteinFraction = ratio(consumed.proteinG, target.proteinG),
    fatFraction = ratio(consumed.fatG, target.fatG),
    carbsFraction = ratio(consumed.carbsG, target.carbsG),
)

/** Today and History render historical and current progress with the same four lines. */
@Composable
fun MacroProgressLines(progress: MacroProgressUiState, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KcalSpacing.small)) {
        ProgressLine(
            label = stringResource(R.string.nutrient_calories),
            value =
            stringResource(
                R.string.nutrient_progress_kcal,
                DecimalText.formatLong(progress.consumed.kcal, locale),
                DecimalText.formatInt(progress.target.kcal, locale),
            ),
            fraction = progress.kcalFraction,
        )
        ProgressLine(
            label = stringResource(R.string.nutrient_protein),
            value = gramsValue(progress.consumed.proteinG, progress.target.proteinG),
            fraction = progress.proteinFraction,
        )
        ProgressLine(
            label = stringResource(R.string.nutrient_fat),
            value = gramsValue(progress.consumed.fatG, progress.target.fatG),
            fraction = progress.fatFraction,
        )
        ProgressLine(
            label = stringResource(R.string.nutrient_carbs),
            value = gramsValue(progress.consumed.carbsG, progress.target.carbsG),
            fraction = progress.carbsFraction,
        )
    }
}

@Composable
private fun gramsValue(consumed: BigDecimal, target: Double): String {
    val locale = currentLocale()
    return stringResource(
        R.string.nutrient_progress_grams,
        DecimalText.format(consumed, locale),
        DecimalText.format(target, locale),
    )
}

@Composable
private fun ProgressLine(label: String, value: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(KcalSpacing.extraSmall)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

private fun ratio(consumed: Long, target: Int): Float = when {
    target <= 0 -> 0f
    consumed >= target.toLong() -> 1f
    else -> consumed.toFloat() / target
}

private fun ratio(consumed: BigDecimal, target: Double): Float {
    if (target <= 0.0) return 0f
    val targetDecimal = BigDecimal.valueOf(target)
    return if (consumed >= targetDecimal) {
        1f
    } else {
        consumed.divide(targetDecimal, MathContext.DECIMAL64).toFloat()
    }
}

@Composable
private fun MacroProgressLinesPreview(themeMode: ThemeMode) {
    KcalTheme(themeMode = themeMode) {
        Surface {
            MacroProgressLines(
                progress =
                macroProgress(
                    consumed = MacroTotals.from(Macros(kcal = 815, proteinG = 54.0, fatG = 31.0, carbsG = 79.0)),
                    target = Macros(kcal = 2_050, proteinG = 105.0, fatG = 56.9, carbsG = 280.0),
                ),
                modifier = Modifier.padding(KcalSpacing.medium),
            )
        }
    }
}

@Preview(name = "Macro progress White")
@Composable
private fun MacroProgressLinesWhitePreview() = MacroProgressLinesPreview(ThemeMode.WHITE)

@Preview(name = "Macro progress Black")
@Composable
private fun MacroProgressLinesBlackPreview() = MacroProgressLinesPreview(ThemeMode.BLACK)
