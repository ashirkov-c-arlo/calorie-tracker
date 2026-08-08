package app.kcal.feature.entry

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

enum class ManualEntryField {
    NAME,
    GRAMS,
    KCAL,
    PROTEIN,
    FAT,
    CARBS,
}

enum class ManualEntryFieldError {
    REQUIRED,
    INVALID_NUMBER,
    NEGATIVE,
}

data class ManualEntryItemErrors(
    val name: ManualEntryFieldError? = null,
    val grams: ManualEntryFieldError? = null,
    val kcal: ManualEntryFieldError? = null,
    val protein: ManualEntryFieldError? = null,
    val fat: ManualEntryFieldError? = null,
    val carbs: ManualEntryFieldError? = null,
) {
    val hasAny: Boolean
        get() = name != null || grams != null || kcal != null || protein != null || fat != null || carbs != null
}

data class ManualEntryItemUiState(
    val key: Long,
    val name: String = "",
    val grams: String = "",
    val kcal: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val errors: ManualEntryItemErrors = ManualEntryItemErrors(),
    val needsReview: Boolean = false,
)

data class ManualEntryUiState(
    val isLoading: Boolean = true,
    val mealId: Long? = null,
    val items: PersistentList<ManualEntryItemUiState> = persistentListOf(),
    val isSaving: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
)

sealed interface ManualEntryEvent {
    data object Saved : ManualEntryEvent
}

internal val manualEntryEmptyPreviewState = ManualEntryUiState(
    isLoading = false,
    items = persistentListOf(ManualEntryItemUiState(key = 1)),
)

internal val manualEntryContentPreviewState = ManualEntryUiState(
    isLoading = false,
    items =
    persistentListOf(
        ManualEntryItemUiState(
            key = 1,
            name = "Chicken breast",
            grams = "180.0",
            kcal = "297",
            protein = "55.8",
            fat = "6.5",
            carbs = "0.0",
        ),
        ManualEntryItemUiState(
            key = 2,
            name = "Rice",
            grams = "220.0",
            kcal = "286",
            protein = "5.9",
            fat = "0.7",
            carbs = "62.9",
        ),
    ),
)

internal val manualEntryValidationPreviewState = ManualEntryUiState(
    isLoading = false,
    items =
    persistentListOf(
        ManualEntryItemUiState(
            key = 1,
            name = "",
            grams = "7000",
            kcal = "99999",
            protein = "-1",
            fat = "abc",
            carbs = "",
            errors =
            ManualEntryItemErrors(
                name = ManualEntryFieldError.REQUIRED,
                protein = ManualEntryFieldError.NEGATIVE,
                fat = ManualEntryFieldError.INVALID_NUMBER,
                carbs = ManualEntryFieldError.REQUIRED,
            ),
            needsReview = true,
        ),
    ),
)

internal val manualEntryErrorPreviewState = ManualEntryUiState(isLoading = false, loadFailed = true)

internal val manualEntrySaveFailedPreviewState = manualEntryContentPreviewState.copy(
    items = persistentListOf(manualEntryContentPreviewState.items.first()),
    saveFailed = true,
)
