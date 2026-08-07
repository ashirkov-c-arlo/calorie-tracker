package app.kcal.feature.profile

import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.usecase.ApplyTodayTarget
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.DailyTargetUnavailableReason
import app.kcal.domain.usecase.DailyTargetWarning
import app.kcal.domain.usecase.SaveProfile
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
        assertEquals("", state.fields.age)
        assertNull(state.fields.energyEquationSex)
        assertNull(state.fields.activityLevel)
        assertEquals(UnitSystem.METRIC, state.unitSystem)
        assertEquals(AppLanguage.SYSTEM, state.appLanguage)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(
            TargetPreview.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
            state.target,
        )
    }

    @Test
    fun `stored metric values prefill the form with the app locale`() = runTest {
        profileRepository.state.value = UserPreferences(profile = completeProfile())
        val viewModel = viewModel(locale = Locale.forLanguageTag("ru"))
        runCurrent()

        val fields = viewModel.uiState.value.fields
        assertEquals("82,4", fields.currentWeight)
        assertEquals("176,0", fields.height)
        assertEquals("34", fields.age)
        assertEquals("0,50", fields.lossRate)
        assertEquals(EnergyEquationSex.MALE, fields.energyEquationSex)
        assertEquals(ActivityLevel.LIGHT, fields.activityLevel)
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
        assertEquals(ProfileFieldError.REQUIRED, errors.lossRate)
        assertTrue(profileRepository.savedProfiles.isEmpty())
        assertEquals(0, dailyTargetRepository.upsertCount)
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
        viewModel.onTargetWeightChange("78")
        viewModel.onLossRateChange("0,5")
        viewModel.onSave()
        runCurrent()

        val saved = profileRepository.savedProfiles.single()
        assertEquals(82.4, saved.currentWeightKg)
        assertEquals(176.5, saved.heightCm)
        assertEquals(34, saved.ageYears)
        assertEquals(EnergyEquationSex.FEMALE, saved.energyEquationSex)
        assertEquals(ActivityLevel.MODERATE, saved.activityLevel)
        assertEquals(78.0, saved.targetWeightKg)
        assertEquals(0.5, saved.requestedLossRateKgPerWeek)
        assertEquals(1, dailyTargetRepository.upsertCount)
        assertTrue(dailyTargetRepository.find(LocalDate.of(2026, 3, 15)) != null)
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
        viewModel.onTargetWeightChange("172.0")
        viewModel.onLossRateChange("1.1")
        viewModel.onSave()
        runCurrent()

        val saved = profileRepository.savedProfiles.single()
        assertEquals(82.4, assertNotNull(saved.currentWeightKg), 0.05)
        assertEquals(176.0, assertNotNull(saved.heightCm), 0.3)
        assertEquals(78.0, assertNotNull(saved.targetWeightKg), 0.05)
        assertEquals(0.5, assertNotNull(saved.requestedLossRateKgPerWeek), 0.005)
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
        assertEquals(UnitSystem.IMPERIAL, profileRepository.state.value.unitSystem)
        assertTrue(profileRepository.savedProfiles.isEmpty())

        viewModel.onUnitSystemSelect(UnitSystem.METRIC)
        runCurrent()

        val metric = viewModel.uiState.value
        assertEquals("82.4", metric.fields.currentWeight)
        assertEquals(ActivityLevel.LIGHT, metric.fields.activityLevel)
    }

    @Test
    fun `the preview follows the entered values and reports guardrails`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)

        viewModel.onLossRateChange("2.0")
        val guarded = viewModel.uiState.value.target
        assertTrue(guarded is TargetPreview.Available)
        assertEquals(
            listOf(DailyTargetWarning.DEFICIT_CAPPED, DailyTargetWarning.RATE_LIMITED),
            guarded.warnings,
        )
        assertTrue(guarded.paceDiffersFromRequest)

        viewModel.onAgeChange("15")
        assertEquals(
            TargetPreview.Unavailable(DailyTargetUnavailableReason.AGE_BELOW_MINIMUM),
            viewModel.uiState.value.target,
        )
    }

    @Test
    fun `validation limits do not depend on the displayed unit system`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        fillValidMetricForm(viewModel)

        // 5 kg per week is accepted as a request; guardrails cap the effective pace instead.
        viewModel.onLossRateChange("5")
        viewModel.onSave()
        runCurrent()
        assertNull(viewModel.uiState.value.errors.lossRate)

        // The same value stays accepted after switching to pounds per week.
        viewModel.onUnitSystemSelect(UnitSystem.IMPERIAL)
        runCurrent()
        viewModel.onSave()
        runCurrent()
        assertNull(viewModel.uiState.value.errors.lossRate)
        // Switching units re-renders the value with display precision, hence the tolerance.
        assertEquals(
            5.0,
            assertNotNull(profileRepository.savedProfiles.last().requestedLossRateKgPerWeek),
            0.005,
        )
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
        // 1 ft is below the shared 50 cm limit, and the combined error belongs to feet.
        assertEquals(ProfileFieldError.OUT_OF_RANGE, viewModel.uiState.value.errors.heightFeet)
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

    private fun fillValidMetricForm(viewModel: ProfileFormViewModel) {
        viewModel.onCurrentWeightChange("82.4")
        viewModel.onHeightChange("176.0")
        viewModel.onAgeChange("34")
        viewModel.onFormulaVariantSelect(EnergyEquationSex.MALE)
        viewModel.onActivityLevelSelect(ActivityLevel.LIGHT)
        viewModel.onTargetWeightChange("78.0")
        viewModel.onLossRateChange("0.5")
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
            localeProvider = AppLocaleProvider { locale },
        )
    }
}
