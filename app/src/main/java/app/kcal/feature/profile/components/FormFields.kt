package app.kcal.feature.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.kcal.R
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.feature.profile.ProfileFieldError

/** A decimal input row with its unit suffix and inline validation message. */
@Composable
fun DecimalField(
    label: String,
    value: String,
    unitLabel: String,
    error: ProfileFieldError?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        suffix = { Text(text = unitLabel) },
        isError = error != null,
        supportingText = error?.let { { Text(text = stringResource(it.messageRes())) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
    )
}

/** A labelled single-choice group. Nothing is preselected until the user picks a value. */
@Composable
fun <T> SingleChoiceField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    error: ProfileFieldError?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = KcalSpacing.minTouchTarget)
                        .selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .padding(vertical = KcalSpacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small),
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Text(text = optionLabel(option), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (error != null) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal fun ProfileFieldError.messageRes(): Int = when (this) {
    ProfileFieldError.REQUIRED -> R.string.error_required
    ProfileFieldError.INVALID_NUMBER -> R.string.error_invalid_number
    ProfileFieldError.OUT_OF_RANGE -> R.string.error_out_of_range
}
