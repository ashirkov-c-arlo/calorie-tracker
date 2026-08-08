package app.kcal.feature.entry

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

data class ManualEntryUiState(
    val isLoading: Boolean = true,
    val mealId: Long? = null,
    val items: PersistentList<MealItemUiState> = persistentListOf(),
    val isSaving: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
)

sealed interface ManualEntryEvent {
    data object Saved : ManualEntryEvent
}

internal val manualEntryEmptyPreviewState = ManualEntryUiState(
    isLoading = false,
    items = persistentListOf(MealItemUiState(key = 1)),
)

internal val manualEntryContentPreviewState = ManualEntryUiState(
    isLoading = false,
    items =
    persistentListOf(
        MealItemUiState(
            key = 1,
            name = "Chicken breast",
            grams = "180.0",
            kcal = "297",
            protein = "55.8",
            fat = "6.5",
            carbs = "0.0",
        ),
        MealItemUiState(
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
        MealItemUiState(
            key = 1,
            name = "",
            grams = "7000",
            kcal = "99999",
            protein = "-1",
            fat = "abc",
            carbs = "",
            errors =
            MealItemErrors(
                name = MealItemFieldError.REQUIRED,
                protein = MealItemFieldError.NEGATIVE,
                fat = MealItemFieldError.INVALID_NUMBER,
                carbs = MealItemFieldError.REQUIRED,
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
