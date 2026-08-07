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
import app.kcal.domain.usecase.SaveProfile
import app.kcal.domain.usecase.SuggestLossPaces
import app.kcal.testing.FakeDailyTargetRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun `stored values prefill the form with the app locale and the matching pace`() = runTest {
        val stored = completeProfile(targetWeightKg = 72.0, requestedLossRateKgPerWeek = 0.29)
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
        // 0.29 kg per week is the moderate pace for this profile.
        assertEquals(LossPace.MODERATE, state.fields.lossPace)
    }

    @Test
    fun `the target weight slider is bounded by the reference range for the entered height`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onHeightChange("176")
        val range = assertNotNull(viewModel.uiState.value.targetWeightRangeKg)
        assertEquals(assertNotNull(BodyMetrics.targetWeightRangeKg(176.0)), range)

        // A value outside the range is pulled back into it instead of being stored as is.
        viewModel.onTargetWeightChange(40.0)
        assertEquals(range.start, viewModel.uiState.value.fields.targetWeightKg)

        viewModel.onTargetWeightChange(200.0)
        assertEquals(range.endInclusive, viewModel.uiState.value.fields.targetWeightKg)
    }

    @Test
    fun `every offered pace produces a different calorie target`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)

        val options = assertNotNull(viewModel.uiState.value.lossPaceOptions)
        assertTrue(options.slowKgPerWeek < options.moderateKgPerWeek)
        assertTrue(options.moderateKgPerWeek < options.fastKgPerWeek)

        val targets =
            LossPace.entries.map { pace ->
                viewModel.onLossPaceSelect(pace)
                val preview = viewModel.uiState.value.target
                assertTrue(preview is TargetPreview.Available, "expected a target for $pace")
                // The offered paces stay inside the guardrails, so nothing is capped.
                assertTrue(preview.warnings.isEmpty(), "pace $pace should not hit a guardrail")
                preview.targets.kcal
            }

        assertEquals(targets.distinct().size, targets.size)
        assertEquals(targets.sortedDescending(), targets)
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

        assertEquals(ProfileFieldError.REQUIRED, viewModel.uiState.value.errors.lossRate)
        assertTrue(profileRepository.savedProfiles.isEmpty())
    }

    @Test
    fun `reaching the target weight needs no pace and stores a zero rate`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel, selectPace = false)
        // A target above the current weight leaves nothing to lose.
        viewModel.onCurrentWeightChange("60")

        assertNull(viewModel.uiState.value.lossPaceOptions)

        viewModel.onSave()
        runCurrent()

        assertNull(viewModel.uiState.value.errors.lossRate)
        assertEquals(0.0, assertNotNull(profileRepository.savedProfiles.single().requestedLossRateKgPerWeek))
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
        val options = assertNotNull(viewModel.uiState.value.lossPaceOptions)
        assertEquals(options.slowKgPerWeek, saved.requestedLossRateKgPerWeek)
        assertEquals(1, dailyTargetRepository.upsertCount)
        assertNotNull(dailyTargetRepository.find(LocalDate.of(2026, 3, 15)))
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
        assertNull(viewModel.uiState.value.lossPaceOptions)
    }

    @Test
    fun `a failed target write reports the failure and keeps the entered values`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)
        dailyTargetRepository.failNextWrites(true)

        viewModel.onSave()
        runCurrent()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals("82.4", viewModel.uiState.value.fields.currentWeight)
        assertEquals(1, profileRepository.savedProfiles.size)
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
            saveProfile =
            SaveProfile(
                profileRepository,
                ApplyTodayTarget(dailyTargetRepository, calculate),
                timeProvider,
            ),
            calculateDailyTargets = calculate,
            suggestLossPaces = SuggestLossPaces(calculate),
            localeProvider = AppLocaleProvider { locale },
        )
    }
}
