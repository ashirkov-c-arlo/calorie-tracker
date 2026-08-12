package app.kcal.feature.entry

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kcal.R
import app.kcal.core.designsystem.KcalSpacing
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import app.kcal.feature.entry.components.MealItemCard
import app.kcal.llm.FailureReason

/**
 * Owns the photo pickers, because launchers need an activity result registry that a preview and
 * a stateless screen do not have.
 */
@Composable
fun EntryRoute(onClose: () -> Unit, onLogManually: () -> Unit, viewModel: EntryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, onClose) {
        viewModel.events.collect { event ->
            if (event == EntryEvent.Saved) onClose()
        }
    }
    var captureTarget by remember { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
            val target = captureTarget
            captureTarget = null
            // A cancelled capture is left alone: the next upload or the closing flow deletes it.
            if (captured && target != null) viewModel.onPhotoPicked(target)
        }
    val pickPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
            picked?.let(viewModel::onPhotoPicked)
        }
    val context = LocalContext.current
    val canTakePhoto =
        remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    EntryScreen(
        uiState = uiState,
        onBackClick = onClose,
        onLogManually = onLogManually,
        onTextChange = viewModel::onTextChange,
        onParse = viewModel::onParse,
        onClarificationAnswerChange = viewModel::onClarificationAnswerChange,
        onSubmitClarification = viewModel::onSubmitClarification,
        onRetry = viewModel::onRetry,
        onItemChange = viewModel::onItemChange,
        onAddItem = viewModel::onAddItem,
        onRemoveItem = viewModel::onRemoveItem,
        onDismissConfirmation = viewModel::onDismissConfirmation,
        onConfirm = viewModel::onConfirm,
        canTakePhoto = canTakePhoto,
        onPickPhoto = {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = {
            val target = viewModel.newCaptureUri()
            captureTarget = target
            takePicture.launch(target)
        },
        onRemovePhoto = viewModel::onRemovePhoto,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    uiState: EntryUiState,
    onBackClick: () -> Unit,
    onLogManually: () -> Unit,
    onTextChange: (String) -> Unit,
    onParse: () -> Unit,
    onClarificationAnswerChange: (String) -> Unit,
    onSubmitClarification: () -> Unit,
    onRetry: () -> Unit,
    onItemChange: (Long, MealItemField, String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Long) -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    canTakePhoto: Boolean = true,
    onPickPhoto: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
) {
    val busy = uiState.isParsing || uiState.isSaving || uiState.isAttachingPhoto
    BackHandler(enabled = busy) {}
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.entry_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, enabled = !busy) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
        ) {
            OutlinedTextField(
                value = uiState.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                label = { Text(text = stringResource(R.string.entry_text_label)) },
                supportingText = {
                    Text(
                        text =
                        stringResource(
                            if (uiState.textMissing) R.string.entry_text_required else R.string.entry_text_hint,
                        ),
                    )
                },
                isError = uiState.textMissing,
                minLines = 3,
            )
            PhotoSection(
                uiState = uiState,
                enabled = !busy,
                canTakePhoto = canTakePhoto,
                onPickPhoto = onPickPhoto,
                onTakePhoto = onTakePhoto,
                onRemovePhoto = onRemovePhoto,
            )
            if (uiState.clarificationQuestion != null) {
                ClarificationCard(
                    question = uiState.clarificationQuestion,
                    answer = uiState.clarificationAnswer,
                    enabled = !busy,
                    onAnswerChange = onClarificationAnswerChange,
                    onSubmit = onSubmitClarification,
                )
            }
            if (uiState.failure != null) {
                FailureCard(reason = uiState.failure, enabled = !busy, onRetry = onRetry)
            }
            if (uiState.isParsing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(text = stringResource(R.string.entry_parsing))
                }
            } else {
                Button(onClick = onParse, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.action_parse))
                }
            }
            TextButton(onClick = onLogManually, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.action_log_manually))
            }
        }
    }

    if (uiState.isConfirming) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissConfirmation,
            sheetState = sheetState,
        ) {
            ConfirmationSheetContent(
                uiState = uiState,
                onItemChange = onItemChange,
                onAddItem = onAddItem,
                onRemoveItem = onRemoveItem,
                onCancel = onDismissConfirmation,
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
private fun PhotoSection(
    uiState: EntryUiState,
    enabled: Boolean,
    canTakePhoto: Boolean,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KcalSpacing.small)) {
        when {
            uiState.isAttachingPhoto ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(text = stringResource(R.string.entry_photo_preparing))
                }

            uiState.photoPath != null ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = KcalSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.entry_photo_attached),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRemovePhoto, enabled = enabled) {
                            Text(text = stringResource(R.string.entry_photo_remove))
                        }
                    }
                }

            else ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KcalSpacing.small),
                ) {
                    OutlinedButton(onClick = onPickPhoto, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.entry_photo_add))
                    }
                    if (canTakePhoto) {
                        OutlinedButton(onClick = onTakePhoto, enabled = enabled, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.entry_photo_take))
                        }
                    }
                }
        }
        if (uiState.photoFailed) {
            Text(
                text = stringResource(R.string.entry_photo_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ClarificationCard(
    question: String,
    answer: String,
    enabled: Boolean,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.small),
        ) {
            Text(text = question, style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswerChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(text = stringResource(R.string.entry_clarification_answer_label)) },
            )
            Button(
                onClick = onSubmit,
                enabled = enabled && answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.action_send_answer))
            }
        }
    }
}

@Composable
private fun FailureCard(reason: FailureReason, enabled: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(KcalSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KcalSpacing.small),
        ) {
            Text(
                text = stringResource(reason.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onRetry, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun ConfirmationSheetContent(
    uiState: EntryUiState,
    onItemChange: (Long, MealItemField, String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Long) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KcalSpacing.medium)
            .padding(bottom = KcalSpacing.large),
        verticalArrangement = Arrangement.spacedBy(KcalSpacing.medium),
    ) {
        Text(text = stringResource(R.string.entry_confirm_title), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.entry_confirm_subtitle), style = MaterialTheme.typography.bodyMedium)
        if (uiState.note != null) {
            Text(text = uiState.note, style = MaterialTheme.typography.bodyMedium)
        }
        uiState.items.forEachIndexed { index, item ->
            MealItemCard(
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
            OutlinedButton(onClick = onCancel, enabled = !uiState.isSaving, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.action_cancel))
            }
            Button(onClick = onConfirm, enabled = !uiState.isSaving, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.action_confirm))
            }
        }
    }
}

private fun FailureReason.messageRes(): Int = when (this) {
    FailureReason.NO_NETWORK -> R.string.entry_failure_no_network

    FailureReason.TIMEOUT -> R.string.entry_failure_timeout

    FailureReason.THROTTLED, FailureReason.QUOTA -> R.string.entry_failure_busy

    FailureReason.CONTENT_BLOCKED -> R.string.entry_failure_content_blocked

    FailureReason.AUTH, FailureReason.INVALID_REQUEST, FailureReason.PAYLOAD_TOO_LARGE ->
        R.string.entry_failure_unavailable

    FailureReason.INVALID_RESPONSE, FailureReason.UNKNOWN -> R.string.entry_failure_unknown
}

@Composable
private fun EntryPreview(themeMode: ThemeMode, uiState: EntryUiState) {
    KcalTheme(themeMode = themeMode) {
        EntryScreen(
            uiState = uiState,
            onBackClick = {},
            onLogManually = {},
            onTextChange = {},
            onParse = {},
            onClarificationAnswerChange = {},
            onSubmitClarification = {},
            onRetry = {},
            onItemChange = { _, _, _ -> },
            onAddItem = {},
            onRemoveItem = {},
            onDismissConfirmation = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Entry idle White")
@Composable
private fun EntryIdleWhitePreview() = EntryPreview(ThemeMode.WHITE, EntryUiState())

@Preview(name = "Entry idle Black")
@Composable
private fun EntryIdleBlackPreview() = EntryPreview(ThemeMode.BLACK, EntryUiState())

@Preview(name = "Entry typed White")
@Composable
private fun EntryTypedWhitePreview() = EntryPreview(ThemeMode.WHITE, entryIdlePreviewState)

@Preview(name = "Entry parsing White")
@Composable
private fun EntryParsingWhitePreview() = EntryPreview(ThemeMode.WHITE, entryParsingPreviewState)

@Preview(name = "Entry parsing Black")
@Composable
private fun EntryParsingBlackPreview() = EntryPreview(ThemeMode.BLACK, entryParsingPreviewState)

@Preview(name = "Entry failure White")
@Composable
private fun EntryFailureWhitePreview() = EntryPreview(ThemeMode.WHITE, entryFailurePreviewState)

@Preview(name = "Entry failure Black")
@Composable
private fun EntryFailureBlackPreview() = EntryPreview(ThemeMode.BLACK, entryFailurePreviewState)

@Preview(name = "Entry photo attached White")
@Composable
private fun EntryPhotoAttachedWhitePreview() = EntryPreview(ThemeMode.WHITE, entryPhotoAttachedPreviewState)

@Preview(name = "Entry photo attached Black")
@Composable
private fun EntryPhotoAttachedBlackPreview() = EntryPreview(ThemeMode.BLACK, entryPhotoAttachedPreviewState)

@Preview(name = "Entry photo preparing White")
@Composable
private fun EntryPhotoPreparingWhitePreview() = EntryPreview(ThemeMode.WHITE, entryPhotoPreparingPreviewState)

@Preview(name = "Entry photo failed White")
@Composable
private fun EntryPhotoFailedWhitePreview() = EntryPreview(ThemeMode.WHITE, entryPhotoFailedPreviewState)

@Preview(name = "Entry photo failed Black")
@Composable
private fun EntryPhotoFailedBlackPreview() = EntryPreview(ThemeMode.BLACK, entryPhotoFailedPreviewState)

@Preview(name = "Entry clarification White", heightDp = 1000)
@Composable
private fun EntryClarificationWhitePreview() = EntryPreview(ThemeMode.WHITE, entryClarificationPreviewState)

@Preview(name = "Entry clarification Black", heightDp = 1000)
@Composable
private fun EntryClarificationBlackPreview() = EntryPreview(ThemeMode.BLACK, entryClarificationPreviewState)

@Preview(name = "Entry confirmation White", heightDp = 2400)
@Composable
private fun EntryConfirmationWhitePreview() = EntryPreview(ThemeMode.WHITE, entryConfirmationPreviewState)

@Preview(name = "Entry confirmation Black", heightDp = 2400)
@Composable
private fun EntryConfirmationBlackPreview() = EntryPreview(ThemeMode.BLACK, entryConfirmationPreviewState)
