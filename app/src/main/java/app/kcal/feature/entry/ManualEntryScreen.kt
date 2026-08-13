package app.kcal.feature.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kcal.R
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.ui.ErrorScreen
import app.kcal.core.ui.LoadingScreen
import app.kcal.domain.model.ThemeMode

@Composable
fun ManualEntryRoute(mealId: Long?, onClose: () -> Unit, viewModel: ManualEntryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(mealId) { viewModel.load(mealId) }
    LaunchedEffect(viewModel, onClose) {
        viewModel.events.collect { event ->
            if (event == ManualEntryEvent.Saved) onClose()
        }
    }
    ManualEntryScreen(
        uiState = uiState,
        onBackClick = onClose,
        onItemChange = viewModel::onItemChange,
        onAddItem = viewModel::onAddItem,
        onRemoveItem = viewModel::onRemoveItem,
        onSave = viewModel::onSave,
        onRetry = viewModel::onRetryLoad,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    uiState: ManualEntryUiState,
    onBackClick: () -> Unit,
    onItemChange: (Long, ManualEntryField, String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Long) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                        stringResource(
                            if (uiState.mealId == null) {
                                R.string.manual_entry_add_title
                            } else {
                                R.string.manual_entry_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when {
            uiState.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))

            uiState.loadFailed ->
                ErrorScreen(
                    message = stringResource(R.string.manual_entry_load_failed),
                    onRetry = onRetry,
                    modifier = Modifier.padding(contentPadding),
                )

            else ->
                ManualEntryForm(
                    uiState = uiState,
                    onBackClick = onBackClick,
                    onItemChange = onItemChange,
                    onAddItem = onAddItem,
                    onRemoveItem = onRemoveItem,
                    onSave = onSave,
                    modifier = Modifier.padding(contentPadding),
                )
        }
    }
}

@Composable
private fun ManualEntryForm(
    uiState: ManualEntryUiState,
    onBackClick: () -> Unit,
    onItemChange: (Long, ManualEntryField, String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Long) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(KcalSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
    ) {
        uiState.items.forEachIndexed { index, item ->
            ManualItemCard(
                number = index + 1,
                item = item,
                canRemove = uiState.items.size > 1 && !uiState.isSaving,
                enabled = !uiState.isSaving,
                onChange = { field, value -> onItemChange(item.key, field, value) },
                onRemove = { onRemoveItem(item.key) },
            )
        }
        OutlinedButton(onClick = onAddItem, enabled = !uiState.isSaving, modifier = Modifier.fillMaxWidth()) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Text(text = stringResource(R.string.action_add_item))
        }
        if (uiState.saveFailed) {
            Text(
                text = stringResource(R.string.manual_entry_save_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small),
        ) {
            OutlinedButton(
                onClick = onBackClick,
                enabled = !uiState.isSaving,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
            Button(onClick = onSave, enabled = !uiState.isSaving, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun ManualItemCard(
    number: Int,
    item: ManualEntryItemUiState,
    canRemove: Boolean,
    enabled: Boolean,
    onChange: (ManualEntryField, String) -> Unit,
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
            EntryTextField(
                value = item.name,
                label = stringResource(R.string.field_food_name),
                error = item.errors.name,
                enabled = enabled,
                onValueChange = { onChange(ManualEntryField.NAME, it) },
            )
            EntryTextField(
                value = item.grams,
                label = stringResource(R.string.field_food_grams_optional),
                suffix = stringResource(R.string.unit_g),
                error = item.errors.grams,
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onChange(ManualEntryField.GRAMS, it) },
            )
            EntryTextField(
                value = item.kcal,
                label = stringResource(R.string.nutrient_calories),
                suffix = stringResource(R.string.unit_kcal),
                error = item.errors.kcal,
                enabled = enabled,
                keyboardType = KeyboardType.Number,
                onValueChange = { onChange(ManualEntryField.KCAL, it) },
            )
            EntryTextField(
                value = item.protein,
                label = stringResource(R.string.nutrient_protein),
                suffix = stringResource(R.string.unit_g),
                error = item.errors.protein,
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onChange(ManualEntryField.PROTEIN, it) },
            )
            EntryTextField(
                value = item.fat,
                label = stringResource(R.string.nutrient_fat),
                suffix = stringResource(R.string.unit_g),
                error = item.errors.fat,
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onChange(ManualEntryField.FAT, it) },
            )
            EntryTextField(
                value = item.carbs,
                label = stringResource(R.string.nutrient_carbs),
                suffix = stringResource(R.string.unit_g),
                error = item.errors.carbs,
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onChange(ManualEntryField.CARBS, it) },
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
private fun EntryTextField(
    value: String,
    label: String,
    error: ManualEntryFieldError?,
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

private fun ManualEntryFieldError.messageRes(): Int = when (this) {
    ManualEntryFieldError.REQUIRED -> R.string.error_required
    ManualEntryFieldError.INVALID_NUMBER -> R.string.error_invalid_number
    ManualEntryFieldError.NEGATIVE -> R.string.error_non_negative
}

@Composable
private fun ManualEntryPreview(themeMode: ThemeMode, uiState: ManualEntryUiState) {
    KcalTheme(themeMode = themeMode) {
        ManualEntryScreen(
            uiState = uiState,
            onBackClick = {},
            onItemChange = { _, _, _ -> },
            onAddItem = {},
            onRemoveItem = {},
            onSave = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Manual entry loading White")
@Composable
private fun ManualEntryLoadingWhitePreview() = ManualEntryPreview(ThemeMode.WHITE, ManualEntryUiState())

@Preview(name = "Manual entry loading Black")
@Composable
private fun ManualEntryLoadingBlackPreview() = ManualEntryPreview(ThemeMode.BLACK, ManualEntryUiState())

@Preview(name = "Manual entry empty White", heightDp = 1200)
@Composable
private fun ManualEntryEmptyWhitePreview() = ManualEntryPreview(ThemeMode.WHITE, manualEntryEmptyPreviewState)

@Preview(name = "Manual entry empty Black", heightDp = 1200)
@Composable
private fun ManualEntryEmptyBlackPreview() = ManualEntryPreview(ThemeMode.BLACK, manualEntryEmptyPreviewState)

@Preview(name = "Manual entry error White")
@Composable
private fun ManualEntryErrorWhitePreview() = ManualEntryPreview(ThemeMode.WHITE, manualEntryErrorPreviewState)

@Preview(name = "Manual entry error Black")
@Composable
private fun ManualEntryErrorBlackPreview() = ManualEntryPreview(ThemeMode.BLACK, manualEntryErrorPreviewState)

@Preview(name = "Manual entry content White", heightDp = 2200)
@Composable
private fun ManualEntryContentWhitePreview() = ManualEntryPreview(ThemeMode.WHITE, manualEntryContentPreviewState)

@Preview(name = "Manual entry content Black", heightDp = 2200)
@Composable
private fun ManualEntryContentBlackPreview() = ManualEntryPreview(ThemeMode.BLACK, manualEntryContentPreviewState)

@Preview(name = "Manual entry validation White", heightDp = 1400)
@Composable
private fun ManualEntryValidationWhitePreview() = ManualEntryPreview(ThemeMode.WHITE, manualEntryValidationPreviewState)

@Preview(name = "Manual entry validation Black", heightDp = 1400)
@Composable
private fun ManualEntryValidationBlackPreview() = ManualEntryPreview(ThemeMode.BLACK, manualEntryValidationPreviewState)
