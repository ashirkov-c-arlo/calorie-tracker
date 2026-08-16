package app.kcal.feature.profile

import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.LossPace
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.usecase.ApplyTodayTarget
import app.kcal.domain.usecase.BodyMetrics
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.DailyTargetUnavailableReason
import app.kcal.domain.usecase.DailyTargetWarning
import app.kcal.domain.usecase.SaveProfile
import app.kcal.domain.usecase.SuggestLossPaces
import app.kcal.testing.FakeDailyTargetRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFormViewModelTest {

    private val profileRepository = FakeProfileRepository()
    private val dailyTargetRepository = FakeDailyTargetRepository()
    private val timeProvider =
        TimeProvider(
            clock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("UTC")),
            zoneId = ZoneId.of("UTC"),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a fresh install shows empty fields with no preselected options`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("", state.fields.currentWeight)
        assertNull(state.fields.energyEquationSex)
        assertNull(state.fields.activityLevel)
        assertNull(state.fields.targetWeightKg)
        assertNull(state.fields.lossPace)
        assertNull(state.targetWeightRangeKg)
        assertNull(state.lossPaceOptions)
        assertEquals(
            TargetPreview.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
            state.target,
        )
    }

    @Test
    fun `stored values prefill the form with the app locale and the selected position`() = runTest {
        val stored = completeProfile(targetWeightKg = 72.0, lossPace = LossPace.MODERATE)
        profileRepository.state.value = UserPreferences(profile = stored)
        val viewModel = viewModel(locale = Locale.forLanguageTag("ru"))
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("82,4", state.fields.currentWeight)
        assertEquals("176,0", state.fields.height)
        assertEquals("34", state.fields.age)
        assertEquals(72.0, state.fields.targetWeightKg)
        assertEquals(EnergyEquationSex.MALE, state.fields.energyEquationSex)
        assertEquals(ActivityLevel.LIGHT, state.fields.activityLevel)
        assertEquals(LossPace.MODERATE, state.fields.lossPace)
    }

    @Test
    fun `below the reference body mass index no position is offered or required`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel, selectPace = false)
        // 50 kg at 176 cm is a body mass index of 16.1, so there is no deficit to choose.
        viewModel.onCurrentWeightChange("50")

        assertNull(viewModel.uiState.value.lossPaceOptions)
        assertTrue(viewModel.uiState.value.noDeficitApplies)

        viewModel.onSave()
        runCurrent()

        assertNull(viewModel.uiState.value.errors.lossPace)
        val saved = profileRepository.savedProfiles.single()
        assertNull(saved.lossPace)
        val preview = viewModel.uiState.value.target
        assertTrue(preview is TargetPreview.Available)
        assertTrue(DailyTargetWarning.NO_DEFICIT_BELOW_REFERENCE_BMI in preview.warnings)
    }

    @Test
    fun `a stored target weight outside the reference range is never changed`() = runTest {
        val stored = completeProfile(targetWeightKg = 95.0)
        profileRepository.state.value = UserPreferences(profile = stored)
        val viewModel = viewModel()
        runCurrent()

        assertEquals(95.0, viewModel.uiState.value.fields.targetWeightKg)

        // Editing the height through intermediate values must not move the target weight.
        viewModel.onHeightChange("1")
        viewModel.onHeightChange("18")
        viewModel.onHeightChange("180")
        assertEquals(95.0, viewModel.uiState.value.fields.targetWeightKg)

        viewModel.onSave()
        runCurrent()
        assertEquals(95.0, profileRepository.savedProfiles.single().targetWeightKg)
    }

    @Test
    fun `the reference range follows the entered height without coercing the value`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onHeightChange("176")
        assertEquals(
            assertNotNull(BodyMetrics.targetWeightRangeKg(176.0)),
            assertNotNull(viewModel.uiState.value.targetWeightRangeKg),
        )

        // The slider quantises to half kilograms and keeps exactly what it reports.
        viewModel.onTargetWeightChange(72.3)
        assertEquals(72.5, viewModel.uiState.value.fields.targetWeightKg)

        viewModel.onHeightChange("150")
        assertEquals(72.5, viewModel.uiState.value.fields.targetWeightKg)
    }

    @Test
    fun `selecting a pace stores that intent and updates the preview`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)

        val options = assertNotNull(viewModel.uiState.value.lossPaceOptions)
        assertTrue(assertNotNull(options.slowKgPerWeek) < assertNotNull(options.moderateKgPerWeek))
        assertTrue(assertNotNull(options.moderateKgPerWeek) < assertNotNull(options.fastKgPerWeek))

        val previews =
            LossPace.entries.associateWith { pace ->
                viewModel.onLossPaceSelect(pace)
                assertEquals(pace, viewModel.uiState.value.fields.lossPace)
                val preview = viewModel.uiState.value.target
                assertTrue(preview is TargetPreview.Available, "expected a target for $pace")
                // The shown estimate is the one the selector offered for that position.
                assertEquals(options.rateFor(pace), preview.effectiveLossRateKgPerWeek)
                preview
            }

        val slow = previews.getValue(LossPace.SLOW)
        val moderate = previews.getValue(LossPace.MODERATE)
        val fast = previews.getValue(LossPace.FAST)
        assertTrue(slow.targets.kcal > moderate.targets.kcal)
        assertTrue(moderate.targets.kcal > fast.targets.kcal)
        assertTrue(slow.deficitKcal < fast.deficitKcal)
    }

    @Test
    fun `saving an empty form reports every required field and stores nothing`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onSave()
        runCurrent()

        val errors = viewModel.uiState.value.errors
        assertEquals(ProfileFieldError.REQUIRED, errors.currentWeight)
        assertEquals(ProfileFieldError.REQUIRED, errors.height)
        assertEquals(ProfileFieldError.REQUIRED, errors.age)
        assertEquals(ProfileFieldError.REQUIRED, errors.formulaVariant)
        assertEquals(ProfileFieldError.REQUIRED, errors.activityLevel)
        assertEquals(ProfileFieldError.REQUIRED, errors.targetWeight)
        assertTrue(profileRepository.savedProfiles.isEmpty())
        assertEquals(0, dailyTargetRepository.upsertCount)
    }

    @Test
    fun `a pace is required once the profile offers one`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel, selectPace = false)

        viewModel.onSave()
        runCurrent()

        assertEquals(ProfileFieldError.REQUIRED, viewModel.uiState.value.errors.lossPace)
        assertTrue(profileRepository.savedProfiles.isEmpty())
    }

    @Test
    fun `reaching the target weight keeps the chosen position and reports maintenance`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)
        // A target above the current weight leaves nothing to lose.
        viewModel.onCurrentWeightChange("60")

        val preview = viewModel.uiState.value.target
        assertTrue(preview is TargetPreview.Available)
        assertTrue(DailyTargetWarning.TARGET_WEIGHT_REACHED in preview.warnings)
        assertEquals(0, preview.deficitKcal)

        viewModel.onSave()
        runCurrent()

        assertNull(viewModel.uiState.value.errors.lossPace)
        // The choice is stored as picked, not dropped because it currently changes nothing.
        assertEquals(LossPace.MODERATE, profileRepository.savedProfiles.single().lossPace)
    }

    @Test
    fun `a position is still offered without an estimate while the target is unavailable`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)
        viewModel.onAgeChange("15")

        val options = assertNotNull(viewModel.uiState.value.lossPaceOptions)
        LossPace.entries.forEach { pace -> assertNull(options.rateFor(pace), "no fabricated rate for $pace") }

        viewModel.onSave()
        runCurrent()

        assertNull(viewModel.uiState.value.errors.lossPace)
        assertEquals(15, profileRepository.savedProfiles.single().ageYears)
    }

    @Test
    fun `a second save is ignored while the first one is still running`() = runTest {
        // A queueing dispatcher keeps the first save in flight while the second tap arrives.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModel()
        advanceUntilIdle()
        fillValidMetricForm(viewModel)

        viewModel.onSave()
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(1, profileRepository.savedProfiles.size)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    @Test
    fun `invalid and out of range values are reported per field`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)

        viewModel.onCurrentWeightChange("abc")
        viewModel.onHeightChange("500")
        viewModel.onSave()
        runCurrent()

        assertEquals(ProfileFieldError.INVALID_NUMBER, viewModel.uiState.value.errors.currentWeight)
        assertEquals(ProfileFieldError.OUT_OF_RANGE, viewModel.uiState.value.errors.height)
        assertTrue(profileRepository.savedProfiles.isEmpty())
    }

    @Test
    fun `a comma separated form is saved as canonical metric values`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onCurrentWeightChange("82,4")
        viewModel.onHeightChange("176,5")
        viewModel.onAgeChange("34")
        viewModel.onFormulaVariantSelect(EnergyEquationSex.FEMALE)
        viewModel.onActivityLevelSelect(ActivityLevel.MODERATE)
        viewModel.onTargetWeightChange(72.0)
        viewModel.onLossPaceSelect(LossPace.SLOW)
        viewModel.onSave()
        runCurrent()

        val saved = profileRepository.savedProfiles.single()
        assertEquals(82.4, saved.currentWeightKg)
        assertEquals(176.5, saved.heightCm)
        assertEquals(34, saved.ageYears)
        assertEquals(EnergyEquationSex.FEMALE, saved.energyEquationSex)
        assertEquals(ActivityLevel.MODERATE, saved.activityLevel)
        assertEquals(72.0, saved.targetWeightKg)
        assertEquals(LossPace.SLOW, saved.lossPace)
        assertEquals(LocalDate.of(2026, 3, 15), profileRepository.savedDates.single())
    }

    @Test
    fun `an imperial form is converted to metric before storage`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onUnitSystemSelect(UnitSystem.IMPERIAL)
        runCurrent()

        viewModel.onCurrentWeightChange("181.7")
        viewModel.onHeightFeetChange("5")
        viewModel.onHeightInchesChange("9.3")
        viewModel.onAgeChange("34")
        viewModel.onFormulaVariantSelect(EnergyEquationSex.MALE)
        viewModel.onActivityLevelSelect(ActivityLevel.LIGHT)
        viewModel.onTargetWeightChange(72.0)
        viewModel.onLossPaceSelect(LossPace.MODERATE)
        viewModel.onSave()
        runCurrent()

        val saved = profileRepository.savedProfiles.single()
        assertEquals(82.4, assertNotNull(saved.currentWeightKg), 0.05)
        assertEquals(176.0, assertNotNull(saved.heightCm), 0.3)
        // The target weight is already canonical, because the slider works in kilograms.
        assertEquals(72.0, saved.targetWeightKg)
    }

    @Test
    fun `switching units re-renders the entered values without changing storage`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)

        viewModel.onUnitSystemSelect(UnitSystem.IMPERIAL)
        runCurrent()

        val imperial = viewModel.uiState.value
        assertEquals(UnitSystem.IMPERIAL, imperial.unitSystem)
        assertEquals("181.7", imperial.fields.currentWeight)
        assertEquals("5", imperial.fields.heightFeet)
        assertEquals("9.3", imperial.fields.heightInches)
        assertEquals(72.0, imperial.fields.targetWeightKg)
        assertEquals(LossPace.MODERATE, imperial.fields.lossPace)
        assertEquals(UnitSystem.IMPERIAL, profileRepository.state.value.unitSystem)
        assertTrue(profileRepository.savedProfiles.isEmpty())

        viewModel.onUnitSystemSelect(UnitSystem.METRIC)
        runCurrent()

        assertEquals("82.4", viewModel.uiState.value.fields.currentWeight)
        assertEquals(ActivityLevel.LIGHT, viewModel.uiState.value.fields.activityLevel)
    }

    @Test
    fun `validation limits do not depend on the displayed unit system`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)

        viewModel.onCurrentWeightChange("19")
        viewModel.onSave()
        runCurrent()
        assertEquals(ProfileFieldError.OUT_OF_RANGE, viewModel.uiState.value.errors.currentWeight)

        viewModel.onUnitSystemSelect(UnitSystem.IMPERIAL)
        runCurrent()
        viewModel.onSave()
        runCurrent()
        assertEquals(ProfileFieldError.OUT_OF_RANGE, viewModel.uiState.value.errors.currentWeight)
    }

    @Test
    fun `feet and inches report their own errors and accept the full inch range`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)
        viewModel.onUnitSystemSelect(UnitSystem.IMPERIAL)
        runCurrent()

        viewModel.onHeightFeetChange("5")
        viewModel.onHeightInchesChange("11.99")
        viewModel.onSave()
        runCurrent()
        assertNull(viewModel.uiState.value.errors.heightFeet)
        assertNull(viewModel.uiState.value.errors.heightInches)

        viewModel.onHeightInchesChange("12.5")
        viewModel.onSave()
        runCurrent()
        assertEquals(ProfileFieldError.OUT_OF_RANGE, viewModel.uiState.value.errors.heightInches)
        assertNull(viewModel.uiState.value.errors.heightFeet)

        viewModel.onHeightInchesChange("0")
        viewModel.onHeightFeetChange("1")
        viewModel.onSave()
        runCurrent()
        assertEquals(ProfileFieldError.OUT_OF_RANGE, viewModel.uiState.value.errors.heightFeet)
    }

    @Test
    fun `the preview reports an out of scope age instead of a target`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)

        viewModel.onAgeChange("15")

        assertEquals(
            TargetPreview.Unavailable(DailyTargetUnavailableReason.AGE_BELOW_MINIMUM),
            viewModel.uiState.value.target,
        )
    }

    @Test
    fun `a failed profile write reports the failure and keeps the entered values`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)
        profileRepository.writeFails = true

        viewModel.onSave()
        runCurrent()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals(false, viewModel.uiState.value.isSaving)
        assertEquals("82.4", viewModel.uiState.value.fields.currentWeight)
        assertTrue(profileRepository.savedProfiles.isEmpty())
    }

    @Test
    fun `language and theme changes are applied immediately`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onAppLanguageSelect(AppLanguage.RUSSIAN)
        viewModel.onThemeModeSelect(ThemeMode.BLACK)
        runCurrent()

        assertEquals(AppLanguage.RUSSIAN, profileRepository.state.value.appLanguage)
        assertEquals(ThemeMode.BLACK, profileRepository.state.value.themeMode)
        assertEquals(AppLanguage.RUSSIAN, viewModel.uiState.value.appLanguage)
        assertEquals(ThemeMode.BLACK, viewModel.uiState.value.themeMode)
    }

    private fun fillValidMetricForm(viewModel: ProfileFormViewModel, selectPace: Boolean = true) {
        viewModel.onCurrentWeightChange("82.4")
        viewModel.onHeightChange("176.0")
        viewModel.onAgeChange("34")
        viewModel.onFormulaVariantSelect(EnergyEquationSex.MALE)
        viewModel.onActivityLevelSelect(ActivityLevel.LIGHT)
        viewModel.onTargetWeightChange(72.0)
        if (selectPace) viewModel.onLossPaceSelect(LossPace.MODERATE)
    }

    private fun viewModel(locale: Locale = Locale.US): ProfileFormViewModel {
        val calculate = CalculateDailyTargets()
        return ProfileFormViewModel(
            profileRepository = profileRepository,
            saveProfile = SaveProfile(profileRepository, calculate, timeProvider),
            calculateDailyTargets = calculate,
            suggestLossPaces = SuggestLossPaces(calculate),
            localeProvider = AppLocaleProvider { locale },
        )
    }
}
