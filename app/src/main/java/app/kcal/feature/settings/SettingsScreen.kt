package app.kcal.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kcal.R
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.ui.LoadingScreen
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.feature.profile.ProfileFormUiState
import app.kcal.feature.profile.ProfileFormViewModel
import app.kcal.feature.profile.components.ProfileFormSection
import app.kcal.feature.profile.components.SingleChoiceField
import app.kcal.feature.profile.components.TargetPreviewCard
import app.kcal.feature.profile.filledProfileFormUiState

/**
 * Settings reuses the first-run form for profile fields and adds the interface
 * preferences. Units, language and theme apply immediately; profile changes need Save.
 */
@Composable
fun SettingsRoute(onBackClick: () -> Unit, viewModel: ProfileFormViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onCurrentWeightChange = viewModel::onCurrentWeightChange,
        onHeightChange = viewModel::onHeightChange,
        onHeightFeetChange = viewModel::onHeightFeetChange,
        onHeightInchesChange = viewModel::onHeightInchesChange,
        onAgeChange = viewModel::onAgeChange,
        onFormulaVariantSelect = viewModel::onFormulaVariantSelect,
        onActivityLevelSelect = viewModel::onActivityLevelSelect,
        onTargetWeightChange = viewModel::onTargetWeightChange,
        onLossPaceSelect = viewModel::onLossPaceSelect,
        onUnitSystemSelect = viewModel::onUnitSystemSelect,
        onAppLanguageSelect = viewModel::onAppLanguageSelect,
        onThemeModeSelect = viewModel::onThemeModeSelect,
        onSave = viewModel::onSave,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: ProfileFormUiState,
    onBackClick: () -> Unit,
    onCurrentWeightChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onHeightFeetChange: (String) -> Unit,
    onHeightInchesChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onFormulaVariantSelect: (EnergyEquationSex) -> Unit,
    onActivityLevelSelect: (ActivityLevel) -> Unit,
    onTargetWeightChange: (Double) -> Unit,
    onLossPaceSelect: (LossPace) -> Unit,
    onUnitSystemSelect: (UnitSystem) -> Unit,
    onAppLanguageSelect: (AppLanguage) -> Unit,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back_content_description),
                        )
                    }
                },
            )
            if (uiState.isLoading) {
                LoadingScreen()
                return@Column
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KcalSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
            ) {
                Text(
                    text = stringResource(R.string.settings_section_profile),
                    style = MaterialTheme.typography.titleMedium,
                )
                ProfileFormSection(
                    fields = uiState.fields,
                    errors = uiState.errors,
                    unitSystem = uiState.unitSystem,
                    onCurrentWeightChange = onCurrentWeightChange,
                    onHeightChange = onHeightChange,
                    onHeightFeetChange = onHeightFeetChange,
                    onHeightInchesChange = onHeightInchesChange,
                    onAgeChange = onAgeChange,
                    onFormulaVariantSelect = onFormulaVariantSelect,
                    onActivityLevelSelect = onActivityLevelSelect,
                    onTargetWeightChange = onTargetWeightChange,
                    onLossPaceSelect = onLossPaceSelect,
                    onUnitSystemSelect = onUnitSystemSelect,
                    targetWeightRangeKg = uiState.targetWeightRangeKg,
                    lossPaceOptions = uiState.lossPaceOptions,
                    noDeficitApplies = uiState.noDeficitApplies,
                )
                TargetPreviewCard(target = uiState.target, unitSystem = uiState.unitSystem)
                Text(
                    text = stringResource(R.string.profile_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.saveFailed) {
                    Text(
                        text = stringResource(R.string.error_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.action_save))
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.settings_section_appearance),
                    style = MaterialTheme.typography.titleMedium,
                )
                SingleChoiceField(
                    label = stringResource(R.string.field_language),
                    options = AppLanguage.entries,
                    selected = uiState.appLanguage,
                    optionLabel = { stringResource(it.labelRes()) },
                    error = null,
                    onSelect = onAppLanguageSelect,
                )
                SingleChoiceField(
                    label = stringResource(R.string.field_theme),
                    options = ThemeMode.entries,
                    selected = uiState.themeMode,
                    optionLabel = { stringResource(it.labelRes()) },
                    error = null,
                    onSelect = onThemeModeSelect,
                )
            }
        }
    }
}

private fun UnitSystem.labelRes(): Int = when (this) {
    UnitSystem.METRIC -> R.string.unit_system_metric
    UnitSystem.IMPERIAL -> R.string.unit_system_imperial
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.RUSSIAN -> R.string.language_russian
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.WHITE -> R.string.theme_white
    ThemeMode.BLACK -> R.string.theme_black
}

@Composable
private fun SettingsScreenPreviewContent(uiState: ProfileFormUiState) {
    SettingsScreen(
        uiState = uiState,
        onBackClick = {},
        onCurrentWeightChange = {},
        onHeightChange = {},
        onHeightFeetChange = {},
        onHeightInchesChange = {},
        onAgeChange = {},
        onFormulaVariantSelect = {},
        onActivityLevelSelect = {},
        onTargetWeightChange = {},
        onLossPaceSelect = {},
        onUnitSystemSelect = {},
        onAppLanguageSelect = {},
        onThemeModeSelect = {},
        onSave = {},
    )
}

@Preview(name = "Settings White", heightDp = 2000)
@Composable
private fun SettingsWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) { SettingsScreenPreviewContent(filledProfileFormUiState) }
}

@Preview(name = "Settings Black", heightDp = 2000)
@Composable
private fun SettingsBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) { SettingsScreenPreviewContent(filledProfileFormUiState) }
}

@Preview(name = "Settings imperial White", heightDp = 2000)
@Composable
private fun SettingsImperialWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) {
        SettingsScreenPreviewContent(
            filledProfileFormUiState.copy(
                unitSystem = UnitSystem.IMPERIAL,
                fields =
                filledProfileFormUiState.fields.copy(
                    currentWeight = "181.7",
                    heightFeet = "5",
                    heightInches = "9.3",
                ),
            ),
        )
    }
}
