package app.kcal.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.feature.profile.components.ProfileFormSection
import app.kcal.feature.profile.components.TargetPreviewCard

/**
 * The required first-run form. Saving it completes the profile, which is what opens the
 * main navigation; there is no skip and no separate onboarding flag.
 */
@Composable
fun ProfileSetupRoute(viewModel: ProfileFormViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileSetupScreen(
        uiState = uiState,
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
        onSave = viewModel::onSave,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    uiState: ProfileFormUiState,
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
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        LoadingScreen(modifier = modifier)
        return
    }
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            TopAppBar(title = { Text(text = stringResource(R.string.profile_setup_title)) })
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(KcalSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
            ) {
                Text(text = stringResource(R.string.profile_intro), style = MaterialTheme.typography.bodyMedium)
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
            }
        }
    }
}

@Composable
private fun ProfileSetupScreenPreviewContent(uiState: ProfileFormUiState) {
    ProfileSetupScreen(
        uiState = uiState,
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
        onSave = {},
    )
}

@Preview(name = "Profile setup errors White", heightDp = 1400)
@Composable
private fun ProfileSetupErrorsWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) { ProfileSetupScreenPreviewContent(invalidProfileFormUiState) }
}

@Preview(name = "Profile setup errors Black", heightDp = 1400)
@Composable
private fun ProfileSetupErrorsBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) { ProfileSetupScreenPreviewContent(invalidProfileFormUiState) }
}

@Preview(name = "Profile setup empty White", heightDp = 1400)
@Composable
private fun ProfileSetupEmptyWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) { ProfileSetupScreenPreviewContent(emptyProfileFormUiState) }
}

@Preview(name = "Profile setup empty Black", heightDp = 1400)
@Composable
private fun ProfileSetupEmptyBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) { ProfileSetupScreenPreviewContent(emptyProfileFormUiState) }
}

@Preview(name = "Profile setup filled White", heightDp = 1400)
@Composable
private fun ProfileSetupFilledWhitePreview() {
    KcalTheme(themeMode = ThemeMode.WHITE) { ProfileSetupScreenPreviewContent(filledProfileFormUiState) }
}

@Preview(name = "Profile setup guardrail Black", heightDp = 1400)
@Composable
private fun ProfileSetupGuardrailBlackPreview() {
    KcalTheme(themeMode = ThemeMode.BLACK) { ProfileSetupScreenPreviewContent(guardedProfileFormUiState) }
}
