package app.kcal.feature.entry.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import app.kcal.R
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import app.kcal.feature.entry.MealItemField
import app.kcal.feature.entry.MealItemFieldError
import app.kcal.feature.entry.MealItemUiState

/** One editable meal item, shared by manual logging and parsed-food confirmation. */
@Composable
fun MealItemCard(
    number: Int,
    item: MealItemUiState,
    canRemove: Boolean,
    enabled: Boolean,
    onChange: (MealItemField, String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.manual_entry_item_number, number),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onRemove, enabled = canRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_remove_item),
                    )
                }
            }
            MealItemTextField(
                value = item.name,
                label = stringResource(R.string.field_food_name),
                error = item.errors.name,
                enabled = enabled,
                onValueChange = { onChange(MealItemField.NAME, it) },
            )
            MealItemTextField(
                value = item.grams,
                label = stringResource(R.string.field_food_grams_optional),
                suffix = stringResource(R.string.unit_g),
                error = item.errors.grams,
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onChange(MealItemField.GRAMS, it) },
            )
            MealItemTextField(
                value = item.kcal,
                label = stringResource(R.string.nutrient_calories),
                suffix = stringResource(R.string.unit_kcal),
                error = item.errors.kcal,
                enabled = enabled,
                keyboardType = KeyboardType.Number,
                onValueChange = { onChange(MealItemField.KCAL, it) },
            )
            MealItemTextField(
                value = item.protein,
                label = stringResource(R.string.nutrient_protein),
                suffix = stringResource(R.string.unit_g),
                error = item.errors.protein,
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onChange(MealItemField.PROTEIN, it) },
            )
            MealItemTextField(
                value = item.fat,
                label = stringResource(R.string.nutrient_fat),
                suffix = stringResource(R.string.unit_g),
                error = item.errors.fat,
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onChange(MealItemField.FAT, it) },
            )
            MealItemTextField(
                value = item.carbs,
                label = stringResource(R.string.nutrient_carbs),
                suffix = stringResource(R.string.unit_g),
                error = item.errors.carbs,
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onChange(MealItemField.CARBS, it) },
            )
            if (item.needsReview) {
                Text(
                    text = stringResource(R.string.manual_entry_review_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun MealItemTextField(
    value: String,
    label: String,
    error: MealItemFieldError?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(text = label) },
        suffix = suffix?.let { { Text(text = it) } },
        isError = error != null,
        supportingText = error?.let { { Text(text = stringResource(it.messageRes())) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private fun MealItemFieldError.messageRes(): Int = when (this) {
    MealItemFieldError.REQUIRED -> R.string.error_required
    MealItemFieldError.INVALID_NUMBER -> R.string.error_invalid_number
    MealItemFieldError.NEGATIVE -> R.string.error_non_negative
}

private val previewItem = MealItemUiState(
    key = 1,
    name = "Chicken breast",
    grams = "180.0",
    kcal = "297",
    protein = "55.8",
    fat = "6.5",
    carbs = "0.0",
)

@Preview(name = "Meal item card White", uiMode = Configuration.UI_MODE_NIGHT_NO, heightDp = 1200)
@Preview(name = "Meal item card Black", uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 1200)
@Composable
private fun MealItemCardPreview() {
    KcalTheme(themeMode = ThemeMode.SYSTEM) {
        MealItemCard(
            number = 1,
            item = previewItem,
            canRemove = true,
            enabled = true,
            onChange = { _, _ -> },
            onRemove = {},
            modifier = Modifier.padding(KcalSpacing.medium),
        )
    }
}

@Preview(name = "Meal item field White", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Meal item field Black", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MealItemTextFieldPreview() {
    KcalTheme(themeMode = ThemeMode.SYSTEM) {
        MealItemTextField(
            value = "12.55",
            label = stringResource(R.string.nutrient_protein),
            suffix = stringResource(R.string.unit_g),
            error = null,
            enabled = true,
            onValueChange = {},
            modifier = Modifier.padding(KcalSpacing.medium),
        )
    }
}
