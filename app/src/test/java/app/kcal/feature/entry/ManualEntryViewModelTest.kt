package app.kcal.feature.entry

import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.SaveMeal
import app.kcal.domain.usecase.ValidateMeal
import app.kcal.testing.FakeMealRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import app.kcal.testing.foodItem
import app.kcal.testing.mealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new entry starts with one row and add remove keeps at least one`() = runTest {
        val viewModel = viewModel(FakeMealRepository())

        viewModel.load(null)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.items.size)

        viewModel.onAddItem()
        assertEquals(2, viewModel.uiState.value.items.size)
        viewModel.onRemoveItem(viewModel.uiState.value.items.last().key)
        viewModel.onRemoveItem(viewModel.uiState.value.items.single().key)
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun `save reports hard field errors and stores nothing`() = runTest {
        val repository = FakeMealRepository()
        val viewModel = viewModel(repository)
        viewModel.load(null)
        val key = viewModel.uiState.value.items.single().key
        viewModel.onItemChange(key, MealItemField.PROTEIN, "-1")

        viewModel.onSave()
        runCurrent()

        val errors = viewModel.uiState.value.items.single().errors
        assertEquals(MealItemFieldError.REQUIRED, errors.name)
        assertEquals(MealItemFieldError.REQUIRED, errors.kcal)
        assertEquals(MealItemFieldError.NEGATIVE, errors.protein)
        assertTrue(repository.meals.value.isEmpty())
    }

    @Test
    fun `comma decimals save canonical values and emit one navigation event`() = runTest {
        val repository = FakeMealRepository()
        val viewModel = viewModel(repository)
        viewModel.load(null)
        val key = viewModel.uiState.value.items.single().key
        fillItem(viewModel, key, grams = "100,5", kcal = "321", protein = "12,5")
        val event = async { viewModel.events.first() }

        viewModel.onSave()
        runCurrent()

        assertEquals(ManualEntryEvent.Saved, event.await())
        val item = repository.meals.value.single().items.single()
        assertEquals(100.5, item.grams)
        assertEquals(12.5, item.macros.proteinG)
        assertEquals(1f, item.confidence)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `a second save is ignored while the first is in flight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeMealRepository()
        val viewModel = viewModel(repository)
        viewModel.load(null)
        fillItem(viewModel, viewModel.uiState.value.items.single().key)

        viewModel.onSave()
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(1, repository.meals.value.size)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `soft review value remains saveable`() = runTest {
        val repository = FakeMealRepository()
        val viewModel = viewModel(repository)
        viewModel.load(null)
        val key = viewModel.uiState.value.items.single().key
        fillItem(viewModel, key, kcal = "5001")

        assertTrue(viewModel.uiState.value.items.single().needsReview)
        viewModel.onSave()
        runCurrent()

        assertEquals(5001, repository.meals.value.single().items.single().macros.kcal)
    }

    @Test
    fun `editing loads locale formatted values and replaces the same meal`() = runTest {
        val original =
            mealEntry(
                id = 8,
                items = listOf(foodItem(name = "Rice", grams = 125.5, proteinG = 4.5)),
            )
        val repository = FakeMealRepository(listOf(original))
        val viewModel = viewModel(repository, Locale.forLanguageTag("ru"))

        viewModel.load(8)
        runCurrent()
        val item = viewModel.uiState.value.items.single()
        assertEquals("125,5", item.grams)
        assertEquals("4,5", item.protein)

        viewModel.onItemChange(item.key, MealItemField.NAME, "Brown rice")
        viewModel.onSave()
        runCurrent()

        assertEquals(8, repository.meals.value.single().id)
        assertEquals("Brown rice", repository.meals.value.single().items.single().name)
        assertEquals(original.at, repository.meals.value.single().at)
    }

    @Test
    fun `editing only the name preserves every exact stored decimal`() = runTest {
        val originalItem =
            foodItem(
                name = "Original",
                grams = 100.05,
                proteinG = 12.55,
                fatG = 0.04,
                carbsG = 33.333333333333336,
            )
        val repository = FakeMealRepository(listOf(mealEntry(id = 9, items = listOf(originalItem))))
        val viewModel = viewModel(repository, Locale.forLanguageTag("ru"))

        viewModel.load(9)
        runCurrent()
        val loaded = viewModel.uiState.value.items.single()
        assertEquals("100,05", loaded.grams)
        assertEquals("12,55", loaded.protein)
        assertEquals("0,04", loaded.fat)

        viewModel.onItemChange(loaded.key, MealItemField.NAME, "Renamed")
        viewModel.onSave()
        runCurrent()

        val saved = repository.meals.value.single().items.single()
        assertEquals(originalItem.grams, saved.grams)
        assertEquals(originalItem.macros.proteinG, saved.macros.proteinG)
        assertEquals(originalItem.macros.fatG, saved.macros.fatG)
        assertEquals(originalItem.macros.carbsG, saved.macros.carbsG)
    }

    @Test
    fun `load and save failures retain a retryable form`() = runTest {
        val repository = FakeMealRepository().apply { readFails = true }
        val viewModel = viewModel(repository)
        viewModel.load(12)
        runCurrent()
        assertTrue(viewModel.uiState.value.loadFailed)

        repository.readFails = false
        repository.meals.value = listOf(mealEntry(id = 12))
        viewModel.onRetryLoad()
        runCurrent()
        assertFalse(viewModel.uiState.value.loadFailed)

        repository.writeFails = true
        viewModel.onSave()
        runCurrent()
        assertTrue(viewModel.uiState.value.saveFailed)
        assertEquals("Oatmeal", viewModel.uiState.value.items.single().name)
    }

    @Test
    fun `missing edit becomes a load error`() = runTest {
        val viewModel = viewModel(FakeMealRepository())

        viewModel.load(404)
        runCurrent()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertNull(viewModel.uiState.value.items.singleOrNull())
    }

    private fun fillItem(
        viewModel: ManualEntryViewModel,
        key: Long,
        grams: String = "100.0",
        kcal: String = "300",
        protein: String = "10.0",
    ) {
        viewModel.onItemChange(key, MealItemField.NAME, "Oatmeal")
        viewModel.onItemChange(key, MealItemField.GRAMS, grams)
        viewModel.onItemChange(key, MealItemField.KCAL, kcal)
        viewModel.onItemChange(key, MealItemField.PROTEIN, protein)
        viewModel.onItemChange(key, MealItemField.FAT, "6.0")
        viewModel.onItemChange(key, MealItemField.CARBS, "50.0")
    }

    private fun viewModel(mealRepository: FakeMealRepository, locale: Locale = Locale.US): ManualEntryViewModel {
        val profileRepository =
            FakeProfileRepository(UserPreferences(profile = completeProfile()))
        val timeProvider =
            TimeProvider(
                Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("UTC")),
                ZoneId.of("UTC"),
            )
        return ManualEntryViewModel(
            mealRepository = mealRepository,
            saveMeal =
            SaveMeal(
                mealRepository = mealRepository,
                profileRepository = profileRepository,
                calculateDailyTargets = CalculateDailyTargets(),
                validateMeal = ValidateMeal(),
                timeProvider = timeProvider,
            ),
            localeProvider = AppLocaleProvider { locale },
        )
    }
}
