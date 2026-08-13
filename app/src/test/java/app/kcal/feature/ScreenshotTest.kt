package app.kcal.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.kcal.KcalApp
import app.kcal.MainUiState
import app.kcal.core.designsystem.KcalTheme
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.feature.entry.EntryScreen
import app.kcal.feature.entry.EntryUiState
import app.kcal.feature.entry.ManualEntryScreen
import app.kcal.feature.entry.ManualEntryUiState
import app.kcal.feature.entry.entryClarificationPreviewState
import app.kcal.feature.entry.entryConfirmationPreviewState
import app.kcal.feature.entry.entryConfirmationRussianPreviewState
import app.kcal.feature.entry.entryFailurePreviewState
import app.kcal.feature.entry.entryIdlePreviewState
import app.kcal.feature.entry.entryParsingPreviewState
import app.kcal.feature.entry.entryPhotoAttachedPreviewState
import app.kcal.feature.entry.entryPhotoFailedPreviewState
import app.kcal.feature.entry.entryPhotoPreparingPreviewState
import app.kcal.feature.entry.manualEntryContentPreviewState
import app.kcal.feature.entry.manualEntryEmptyPreviewState
import app.kcal.feature.entry.manualEntryErrorPreviewState
import app.kcal.feature.entry.manualEntrySaveFailedPreviewState
import app.kcal.feature.entry.manualEntryValidationPreviewState
import app.kcal.feature.history.HistoryScreen
import app.kcal.feature.history.HistoryUiState
import app.kcal.feature.history.historyContentPreviewState
import app.kcal.feature.history.historyEmptyPreviewState
import app.kcal.feature.history.historyErrorPreviewState
import app.kcal.feature.history.historyExpandedPreviewState
import app.kcal.feature.profile.ProfileFormUiState
import app.kcal.feature.profile.ProfileSetupScreen
import app.kcal.feature.profile.emptyProfileFormUiState
import app.kcal.feature.profile.filledProfileFormUiState
import app.kcal.feature.profile.guardedProfileFormUiState
import app.kcal.feature.profile.invalidProfileFormUiState
import app.kcal.feature.profile.russianProfileFormUiState
import app.kcal.feature.settings.SettingsScreen
import app.kcal.feature.today.TodayScreen
import app.kcal.feature.today.TodayUiState
import app.kcal.feature.today.todayContentPreviewState
import app.kcal.feature.today.todayEmptyPreviewState
import app.kcal.feature.today.todayErrorPreviewState
import app.kcal.feature.today.todayNoTargetPreviewState
import app.kcal.feature.trends.TrendsScreen
import app.kcal.feature.trends.TrendsUiState
import app.kcal.feature.trends.trendsEmptyPreviewState
import app.kcal.feature.trends.trendsErrorPreviewState
import app.kcal.feature.trends.trendsImperialPreviewState
import app.kcal.feature.trends.trendsInvalidInputPreviewState
import app.kcal.feature.trends.trendsManyPointsPreviewState
import app.kcal.feature.trends.trendsSinglePointPreviewState
import app.kcal.feature.trends.trendsTwoPointsPreviewState
import app.kcal.navigation.KcalNavHost
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * White/Black visual regression for every screen, plus Russian text-heavy states for the
 * screens that carry most of the wording.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayWhite() = capture(ThemeMode.WHITE) { TodayFixture(todayContentPreviewState) }

    @Test
    fun todayBlack() = capture(ThemeMode.BLACK) { TodayFixture(todayContentPreviewState) }

    @Test
    @Config(qualifiers = "+ru")
    fun todayWhiteRussian() = capture(ThemeMode.WHITE) { TodayFixture(todayContentPreviewState) }

    @Test
    fun todayLoadingWhite() = capture(ThemeMode.WHITE) { TodayFixture(TodayUiState()) }

    @Test
    fun todayLoadingBlack() = capture(ThemeMode.BLACK) { TodayFixture(TodayUiState()) }

    @Test
    fun todayEmptyWhite() = capture(ThemeMode.WHITE) { TodayFixture(todayEmptyPreviewState) }

    @Test
    fun todayEmptyBlack() = capture(ThemeMode.BLACK) { TodayFixture(todayEmptyPreviewState) }

    @Test
    fun todayNoTargetWhite() = capture(ThemeMode.WHITE) { TodayFixture(todayNoTargetPreviewState) }

    @Test
    fun todayNoTargetBlack() = capture(ThemeMode.BLACK) { TodayFixture(todayNoTargetPreviewState) }

    @Test
    fun todayErrorWhite() = capture(ThemeMode.WHITE) { TodayFixture(todayErrorPreviewState) }

    @Test
    fun todayErrorBlack() = capture(ThemeMode.BLACK) { TodayFixture(todayErrorPreviewState) }

    @Test
    fun manualEntryLoadingWhite() = capture(ThemeMode.WHITE) { ManualEntryFixture(ManualEntryUiState()) }

    @Test
    fun manualEntryLoadingBlack() = capture(ThemeMode.BLACK) { ManualEntryFixture(ManualEntryUiState()) }

    @Test
    @Config(qualifiers = "+h1400dp")
    fun manualEntryEmptyWhite() = capture(ThemeMode.WHITE) { ManualEntryFixture(manualEntryEmptyPreviewState) }

    @Test
    @Config(qualifiers = "+h1400dp")
    fun manualEntryEmptyBlack() = capture(ThemeMode.BLACK) { ManualEntryFixture(manualEntryEmptyPreviewState) }

    @Test
    fun manualEntryErrorWhite() = capture(ThemeMode.WHITE) { ManualEntryFixture(manualEntryErrorPreviewState) }

    @Test
    fun manualEntryErrorBlack() = capture(ThemeMode.BLACK) { ManualEntryFixture(manualEntryErrorPreviewState) }

    @Test
    @Config(qualifiers = "+h1600dp")
    fun manualEntrySaveFailedWhite() =
        capture(ThemeMode.WHITE) { ManualEntryFixture(manualEntrySaveFailedPreviewState) }

    @Test
    @Config(qualifiers = "+h1600dp")
    fun manualEntrySaveFailedBlack() =
        capture(ThemeMode.BLACK) { ManualEntryFixture(manualEntrySaveFailedPreviewState) }

    @Test
    @Config(qualifiers = "+h2400dp")
    fun manualEntryContentWhite() = capture(ThemeMode.WHITE) { ManualEntryFixture(manualEntryContentPreviewState) }

    @Test
    @Config(qualifiers = "+h2400dp")
    fun manualEntryContentBlack() = capture(ThemeMode.BLACK) { ManualEntryFixture(manualEntryContentPreviewState) }

    @Test
    @Config(qualifiers = "+h1600dp")
    fun manualEntryValidationWhite() =
        capture(ThemeMode.WHITE) { ManualEntryFixture(manualEntryValidationPreviewState) }

    @Test
    @Config(qualifiers = "+h1600dp")
    fun manualEntryValidationBlack() =
        capture(ThemeMode.BLACK) { ManualEntryFixture(manualEntryValidationPreviewState) }

    @Test
    @Config(qualifiers = "+ru-h1600dp")
    fun manualEntryValidationWhiteRussian() =
        capture(ThemeMode.WHITE) { ManualEntryFixture(manualEntryValidationPreviewState) }

    @Test
    fun entryIdleWhite() = capture(ThemeMode.WHITE) { EntryFixture(EntryUiState()) }

    @Test
    fun entryIdleBlack() = capture(ThemeMode.BLACK) { EntryFixture(EntryUiState()) }

    @Test
    fun entryTypedWhite() = capture(ThemeMode.WHITE) { EntryFixture(entryIdlePreviewState) }

    @Test
    fun entryParsingWhite() = capture(ThemeMode.WHITE) { EntryFixture(entryParsingPreviewState) }

    @Test
    fun entryParsingBlack() = capture(ThemeMode.BLACK) { EntryFixture(entryParsingPreviewState) }

    @Test
    fun entryFailureWhite() = capture(ThemeMode.WHITE) { EntryFixture(entryFailurePreviewState) }

    @Test
    fun entryFailureBlack() = capture(ThemeMode.BLACK) { EntryFixture(entryFailurePreviewState) }

    @Test
    @Config(qualifiers = "+ru")
    fun entryFailureWhiteRussian() = capture(ThemeMode.WHITE) { EntryFixture(entryFailurePreviewState) }

    @Test
    fun entryPhotoAttachedWhite() = capture(ThemeMode.WHITE) { EntryFixture(entryPhotoAttachedPreviewState) }

    @Test
    fun entryPhotoAttachedBlack() = capture(ThemeMode.BLACK) { EntryFixture(entryPhotoAttachedPreviewState) }

    @Test
    @Config(qualifiers = "+ru")
    fun entryPhotoAttachedWhiteRussian() = capture(ThemeMode.WHITE) { EntryFixture(entryPhotoAttachedPreviewState) }

    @Test
    fun entryPhotoPreparingWhite() = capture(ThemeMode.WHITE) { EntryFixture(entryPhotoPreparingPreviewState) }

    @Test
    fun entryPhotoPreparingBlack() = capture(ThemeMode.BLACK) { EntryFixture(entryPhotoPreparingPreviewState) }

    @Test
    fun entryPhotoFailedWhite() = capture(ThemeMode.WHITE) { EntryFixture(entryPhotoFailedPreviewState) }

    @Test
    fun entryPhotoFailedBlack() = capture(ThemeMode.BLACK) { EntryFixture(entryPhotoFailedPreviewState) }

    @Test
    @Config(qualifiers = "+h1200dp")
    fun entryClarificationWhite() = capture(ThemeMode.WHITE) { EntryFixture(entryClarificationPreviewState) }

    @Test
    @Config(qualifiers = "+h1200dp")
    fun entryClarificationBlack() = capture(ThemeMode.BLACK) { EntryFixture(entryClarificationPreviewState) }

    @Test
    @Config(qualifiers = "+h1600dp")
    fun entryConfirmationWhite() = capture(ThemeMode.WHITE) { EntryFixture(entryConfirmationPreviewState) }

    @Test
    @Config(qualifiers = "+h1600dp")
    fun entryConfirmationBlack() = capture(ThemeMode.BLACK) { EntryFixture(entryConfirmationPreviewState) }

    @Test
    @Config(qualifiers = "+ru-h1600dp")
    fun entryConfirmationWhiteRussian() =
        capture(ThemeMode.WHITE) { EntryFixture(entryConfirmationRussianPreviewState) }

    @Test
    fun trendsWhite() = capture(ThemeMode.WHITE) { TrendsFixture(trendsManyPointsPreviewState) }

    @Test
    fun trendsBlack() = capture(ThemeMode.BLACK) { TrendsFixture(trendsManyPointsPreviewState) }

    @Test
    @Config(qualifiers = "+ru")
    fun trendsWhiteRussian() = capture(ThemeMode.WHITE) { TrendsFixture(trendsManyPointsPreviewState) }

    @Test
    fun trendsImperialWhite() = capture(ThemeMode.WHITE) { TrendsFixture(trendsImperialPreviewState) }

    @Test
    fun trendsLoadingWhite() = capture(ThemeMode.WHITE) { TrendsFixture(TrendsUiState()) }

    @Test
    fun trendsLoadingBlack() = capture(ThemeMode.BLACK) { TrendsFixture(TrendsUiState()) }

    @Test
    fun trendsEmptyWhite() = capture(ThemeMode.WHITE) { TrendsFixture(trendsEmptyPreviewState) }

    @Test
    fun trendsEmptyBlack() = capture(ThemeMode.BLACK) { TrendsFixture(trendsEmptyPreviewState) }

    @Test
    fun trendsSinglePointWhite() = capture(ThemeMode.WHITE) { TrendsFixture(trendsSinglePointPreviewState) }

    @Test
    fun trendsSinglePointBlack() = capture(ThemeMode.BLACK) { TrendsFixture(trendsSinglePointPreviewState) }

    @Test
    fun trendsTwoPointsWhite() = capture(ThemeMode.WHITE) { TrendsFixture(trendsTwoPointsPreviewState) }

    @Test
    fun trendsTwoPointsBlack() = capture(ThemeMode.BLACK) { TrendsFixture(trendsTwoPointsPreviewState) }

    @Test
    fun trendsInvalidInputWhite() = capture(ThemeMode.WHITE) { TrendsFixture(trendsInvalidInputPreviewState) }

    @Test
    fun trendsErrorWhite() = capture(ThemeMode.WHITE) { TrendsFixture(trendsErrorPreviewState) }

    @Test
    fun trendsErrorBlack() = capture(ThemeMode.BLACK) { TrendsFixture(trendsErrorPreviewState) }

    @Test
    fun historyWhite() = capture(ThemeMode.WHITE) { HistoryFixture(historyContentPreviewState) }

    @Test
    fun historyBlack() = capture(ThemeMode.BLACK) { HistoryFixture(historyContentPreviewState) }

    @Test
    @Config(qualifiers = "+ru")
    fun historyWhiteRussian() = capture(ThemeMode.WHITE) { HistoryFixture(historyContentPreviewState) }

    @Test
    @Config(qualifiers = "+h1200dp")
    fun historyExpandedDayWhite() = capture(ThemeMode.WHITE) { HistoryFixture(historyExpandedPreviewState) }

    @Test
    @Config(qualifiers = "+h1200dp")
    fun historyExpandedDayBlack() = capture(ThemeMode.BLACK) { HistoryFixture(historyExpandedPreviewState) }

    @Test
    fun historyLoadingWhite() = capture(ThemeMode.WHITE) { HistoryFixture(HistoryUiState()) }

    @Test
    fun historyLoadingBlack() = capture(ThemeMode.BLACK) { HistoryFixture(HistoryUiState()) }

    @Test
    fun historyEmptyWhite() = capture(ThemeMode.WHITE) { HistoryFixture(historyEmptyPreviewState) }

    @Test
    fun historyEmptyBlack() = capture(ThemeMode.BLACK) { HistoryFixture(historyEmptyPreviewState) }

    @Test
    fun historyErrorWhite() = capture(ThemeMode.WHITE) { HistoryFixture(historyErrorPreviewState) }

    @Test
    fun historyErrorBlack() = capture(ThemeMode.BLACK) { HistoryFixture(historyErrorPreviewState) }

    @Test
    fun navHostWhite() = capture(ThemeMode.WHITE) { NavHostFixture() }

    @Test
    fun navHostBlack() = capture(ThemeMode.BLACK) { NavHostFixture() }

    @Test
    fun appLoadingWhite() = capture(ThemeMode.WHITE) { KcalApp(uiState = MainUiState()) }

    @Test
    fun appLoadingBlack() = capture(ThemeMode.BLACK) { KcalApp(uiState = MainUiState()) }

    @Test
    fun appStartupErrorWhite() =
        capture(ThemeMode.WHITE) { KcalApp(uiState = MainUiState(isLoading = false, startupFailed = true)) }

    @Test
    fun appStartupErrorBlack() =
        capture(ThemeMode.BLACK) { KcalApp(uiState = MainUiState(isLoading = false, startupFailed = true)) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupEmptyWhite() = capture(ThemeMode.WHITE) { ProfileSetupFixture(emptyProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupEmptyBlack() = capture(ThemeMode.BLACK) { ProfileSetupFixture(emptyProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupFilledWhite() = capture(ThemeMode.WHITE) { ProfileSetupFixture(filledProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupGuardrailBlack() = capture(ThemeMode.BLACK) { ProfileSetupFixture(guardedProfileFormUiState) }

    @Test
    @Config(qualifiers = "+ru-h2000dp")
    fun profileSetupGuardrailWhiteRussian() = capture(ThemeMode.WHITE) {
        ProfileSetupFixture(russianProfileFormUiState)
    }

    @Test
    @Config(qualifiers = "+ru")
    fun appStartupErrorWhiteRussian() =
        capture(ThemeMode.WHITE) { KcalApp(uiState = MainUiState(isLoading = false, startupFailed = true)) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupErrorsWhite() = capture(ThemeMode.WHITE) { ProfileSetupFixture(invalidProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2000dp")
    fun profileSetupErrorsBlack() = capture(ThemeMode.BLACK) { ProfileSetupFixture(invalidProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2600dp")
    fun settingsWhite() = capture(ThemeMode.WHITE) { SettingsFixture(filledProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2600dp")
    fun settingsBlack() = capture(ThemeMode.BLACK) { SettingsFixture(filledProfileFormUiState) }

    @Test
    @Config(qualifiers = "+h2600dp")
    fun settingsImperialWhite() = capture(ThemeMode.WHITE) {
        SettingsFixture(
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

    @Test
    @Config(qualifiers = "+ru-h2600dp")
    fun settingsWhiteRussian() = capture(ThemeMode.WHITE) { SettingsFixture(russianProfileFormUiState) }

    @Composable
    private fun TodayFixture(uiState: TodayUiState) {
        TodayScreen(
            uiState = uiState,
            onSettingsClick = {},
            onAddMealClick = {},
            onEditMealClick = {},
            onDeleteMealClick = {},
            onRetry = {},
        )
    }

    @Composable
    private fun ManualEntryFixture(uiState: ManualEntryUiState) {
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

    @Composable
    private fun EntryFixture(uiState: EntryUiState) {
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

    @Composable
    private fun HistoryFixture(uiState: HistoryUiState) {
        HistoryScreen(
            uiState = uiState,
            onDayClick = {},
            onEditMealClick = {},
            onDeleteMealClick = {},
            onRetry = {},
        )
    }

    @Composable
    private fun TrendsFixture(uiState: TrendsUiState) {
        TrendsScreen(uiState = uiState, onWeightChange = {}, onSave = {}, onRetry = {})
    }

    @Composable
    private fun NavHostFixture() {
        KcalNavHost(
            todayContent = { onSettingsClick, onAddMealClick, onEditMealClick ->
                TodayScreen(
                    uiState = todayContentPreviewState,
                    onSettingsClick = onSettingsClick,
                    onAddMealClick = onAddMealClick,
                    onEditMealClick = onEditMealClick,
                    onDeleteMealClick = {},
                    onRetry = {},
                )
            },
            trendsContent = { TrendsFixture(trendsManyPointsPreviewState) },
        )
    }

    @Composable
    private fun ProfileSetupFixture(uiState: ProfileFormUiState) {
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

    @Composable
    private fun SettingsFixture(uiState: ProfileFormUiState) {
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

    private fun capture(themeMode: ThemeMode, content: @Composable () -> Unit) {
        composeRule.setContent {
            KcalTheme(themeMode = themeMode) { content() }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
