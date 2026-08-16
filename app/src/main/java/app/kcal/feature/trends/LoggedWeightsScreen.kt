package app.kcal.feature.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kcal.R
import app.kcal.core.common.DecimalText
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.core.ui.currentLocale
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun LoggedWeightsRoute(
    onBackClick: () -> Unit,
    viewModel: LoggedWeightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoggedWeightsScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onEntryClick = viewModel::onEntryClick,
        onDeleteEntry = viewModel::onDeleteEntry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggedWeightsScreen(
    uiState: LoggedWeightsUiState,
    onBackClick: () -> Unit,
    onEntryClick: (LocalDate) -> Unit,
    onDeleteEntry: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.trends_entries_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back_content_description),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LoggedWeightsList(
            uiState = uiState,
            onEntryClick = onEntryClick,
            onDeleteEntry = onDeleteEntry,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun LoggedWeightsList(
    uiState: LoggedWeightsUiState,
    onEntryClick: (LocalDate) -> Unit,
    onDeleteEntry: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val unit = stringResource(uiState.unitSystem.weightUnitRes())
    val editLabel = stringResource(R.string.trends_edit_entry_content_description)
    val deleteLabel = stringResource(R.string.trends_delete_entry_content_description)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(KcalSpacing.medium)),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.extraSmall),
    ) {
        if (uiState.points.isEmpty()) {
            Text(text = stringResource(R.string.trends_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            uiState.points.asReversed().forEachIndexed { index, point ->
                if (index > 0) HorizontalDivider()
                val isSelected = point.localDate == uiState.editedDate
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = KcalSpacing.minTouchTarget)
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(onClickLabel = editLabel) { onEntryClick(point.localDate) }
                        .semantics { selected = isSelected }
                        .padding(horizontal = KcalSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small),
                ) {
                    Text(
                        text = mediumDate(point.localDate),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            R.string.trends_entry_value,
                            DecimalText.format(point.value, locale),
                            unit,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = { onEntryClick(point.localDate) }) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = editLabel)
                    }
                    IconButton(onClick = { onDeleteEntry(point.localDate) }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = deleteLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun mediumDate(localDate: LocalDate): String {
    val locale = currentLocale()
    val formatter = remember(locale) { mediumDateFormatter(locale) }
    return formatter.format(localDate)
}

private fun mediumDateFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

private fun UnitSystem.weightUnitRes(): Int = when (this) {
    UnitSystem.METRIC -> R.string.unit_kg
    UnitSystem.IMPERIAL -> R.string.unit_lb
}

@Composable
private fun LoggedWeightsPreview(themeMode: ThemeMode, uiState: LoggedWeightsUiState) {
    KcalTheme(themeMode = themeMode) {
        LoggedWeightsScreen(
            uiState = uiState,
            onBackClick = {},
            onEntryClick = {},
            onDeleteEntry = {},
        )
    }
}

@Preview(name = "Logged weights White", heightDp = 2400)
@Composable
private fun LoggedWeightsWhitePreview() = LoggedWeightsPreview(
    ThemeMode.WHITE,
    loggedWeightsManyPointsPreviewState,
)

@Preview(name = "Logged weights Black", heightDp = 2400)
@Composable
private fun LoggedWeightsBlackPreview() = LoggedWeightsPreview(
    ThemeMode.BLACK,
    loggedWeightsManyPointsPreviewState,
)

@Preview(name = "Logged weights editing White", heightDp = 2400)
@Composable
private fun LoggedWeightsEditingWhitePreview() = LoggedWeightsPreview(
    ThemeMode.WHITE,
    loggedWeightsEditingPreviewState,
)

@Preview(name = "Logged weights empty White")
@Composable
private fun LoggedWeightsEmptyWhitePreview() = LoggedWeightsPreview(
    ThemeMode.WHITE,
    LoggedWeightsUiState(isLoading = false),
)
