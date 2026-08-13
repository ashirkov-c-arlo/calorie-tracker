package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.domain.model.UserPreferences
import app.kcal.testing.FakeMealRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import app.kcal.testing.foodItem
import app.kcal.testing.mealEntry
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveManualMealTest {

    private val instant = Instant.parse("2026-03-15T00:30:00Z")

    @Test
    fun `new meal uses one injected instant and the user's local day`() = runTest {
        val repository = FakeMealRepository()
        val save = saveUseCase(repository, ZoneId.of("America/Los_Angeles"))

        val result = save(null, listOf(foodItem(name = " Oatmeal ")))

        assertIs<SaveMealResult.Saved>(result)
        val stored = repository.meals.value.single()
        assertEquals(instant, stored.at)
        assertEquals(LocalDate.of(2026, 3, 14), stored.localDate)
        assertEquals("Oatmeal", stored.items.single().name)
        assertEquals(stored.localDate, assertNotNull(repository.targetsEnsured.single()).localDate)
    }

    @Test
    fun `hard invalid meal is rejected before storage`() = runTest {
        val repository = FakeMealRepository()
        val result = saveUseCase(repository)(null, listOf(foodItem(name = "")))

        val invalid = assertIs<SaveMealResult.Invalid>(result)
        assertTrue(MealValidationError.BLANK_NAME in invalid.errors)
        assertTrue(repository.meals.value.isEmpty())
    }

    @Test
    fun `editing replaces items but preserves the original time and identity`() = runTest {
        val original = mealEntry(id = 7, at = Instant.parse("2026-03-14T08:00:00Z"))
        val repository = FakeMealRepository(listOf(original))

        val result = saveUseCase(repository)(7, listOf(foodItem(name = "Rice", kcal = 200)))

        assertEquals(SaveMealResult.Saved(7), result)
        val stored = repository.meals.value.single()
        assertEquals(original.at, stored.at)
        assertEquals("Rice", stored.items.single().name)
        assertEquals(200, stored.items.single().macros.kcal)
    }

    @Test
    fun `editing a missing meal reports not found`() = runTest {
        val repository = FakeMealRepository()

        assertEquals(SaveMealResult.NotFound, saveUseCase(repository)(99, listOf(foodItem())))
        assertTrue(repository.meals.value.isEmpty())
    }

    @Test
    fun `an unavailable target never fabricates a snapshot or blocks manual logging`() = runTest {
        val repository = FakeMealRepository()
        val profileRepository =
            FakeProfileRepository(UserPreferences(profile = completeProfile(ageYears = 15)))
        val save = saveUseCase(repository, profileRepository = profileRepository)

        assertIs<SaveMealResult.Saved>(save(null, listOf(foodItem())))
        assertNull(repository.targetsEnsured.single())
        assertEquals(1, repository.meals.value.size)
    }

    private fun saveUseCase(
        mealRepository: FakeMealRepository,
        zoneId: ZoneId = ZoneId.of("UTC"),
        profileRepository: FakeProfileRepository =
            FakeProfileRepository(UserPreferences(profile = completeProfile())),
    ): SaveManualMeal = SaveManualMeal(
        mealRepository = mealRepository,
        profileRepository = profileRepository,
        calculateDailyTargets = CalculateDailyTargets(),
        validateMeal = ValidateMeal(),
        timeProvider = TimeProvider(Clock.fixed(instant, ZoneId.of("UTC")), zoneId),
    )
}
