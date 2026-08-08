package app.kcal.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class KcalDatabaseTest {

    private lateinit var database: KcalDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KcalDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `weight entries are unique per local date and the latest one wins`() = runTest {
        val dao = database.weightEntryDao()

        dao.upsert(WeightEntryEntity(localDateEpochDay = 20_000, kg = 82.4))
        dao.upsert(WeightEntryEntity(localDateEpochDay = 20_001, kg = 82.1))
        dao.upsert(WeightEntryEntity(localDateEpochDay = 20_001, kg = 81.9))

        assertEquals(81.9, dao.findByDate(20_001)?.kg)
        assertEquals(WeightEntryEntity(20_001, 81.9), dao.observeLatest().first())
    }

    @Test
    fun `deleting a meal cascades to its items`() = runTest {
        val dao = database.mealEntryDao()
        val mealId =
            dao.insertMealEntry(
                MealEntryEntity(
                    localDateEpochDay = 20_000,
                    atEpochMillis = 1_728_000_000_000,
                    rawUserInput = "omelette",
                    source = "LLM_TEXT",
                ),
            )
        dao.insertFoodItems(
            listOf(
                foodItem(mealId, position = 0, name = "Omelette"),
                foodItem(mealId, position = 1, name = "Cheese"),
            ),
        )

        assertEquals(listOf("Omelette", "Cheese"), dao.findFoodItems(mealId).map { it.name })

        dao.deleteMealEntry(mealId)

        assertTrue(dao.findMealEntriesByDate(20_000).isEmpty())
        assertTrue(dao.findFoodItems(mealId).isEmpty())
    }

    @Test
    fun `meal transaction stores target and returns meals in stable chronological order`() = runTest {
        val dao = database.mealEntryDao()
        val target = targetSnapshot(epochDay = 20_000)
        val laterId =
            dao.save(
                entry = mealEntry(epochDay = 20_000, atMillis = 200),
                items = listOf(foodItem(0, position = 8, name = "Later")),
                targetIfMissing = target,
            )
        val earlierId =
            dao.save(
                entry = mealEntry(epochDay = 20_000, atMillis = 100),
                items = listOf(foodItem(0, position = 4, name = "Earlier")),
                targetIfMissing = target.copy(kcal = 999),
            )

        assertEquals(listOf(earlierId, laterId), dao.observeByDate(20_000).first().map { it.meal.id })
        assertEquals(2100, database.dailyTargetSnapshotDao().findByDate(20_000)?.kcal)
        assertEquals(0, dao.findFoodItems(laterId).single().position)
    }

    @Test
    fun `editing replaces items and deleting still cascades`() = runTest {
        val dao = database.mealEntryDao()
        val id =
            dao.save(
                entry = mealEntry(epochDay = 20_000, atMillis = 100),
                items =
                listOf(
                    foodItem(0, position = 0, name = "Old one"),
                    foodItem(0, position = 1, name = "Old two"),
                ),
                targetIfMissing = targetSnapshot(20_000),
            )

        dao.save(
            entry = mealEntry(epochDay = 20_000, atMillis = 100).copy(id = id),
            items = listOf(foodItem(0, position = 9, name = "Replacement")),
            targetIfMissing = targetSnapshot(20_000),
        )

        assertEquals(listOf("Replacement"), dao.findFoodItems(id).map { it.name })
        assertEquals(listOf(0), dao.findFoodItems(id).map { it.position })
        dao.deleteMealEntry(id)
        assertTrue(dao.findFoodItems(id).isEmpty())
    }

    @Test
    fun `failed meal transaction rolls back its target insert`() = runTest {
        val dao = database.mealEntryDao()

        assertFailsWith<IllegalStateException> {
            dao.save(
                entry = mealEntry(epochDay = 20_000, atMillis = 100).copy(id = 999),
                items = listOf(foodItem(999, position = 0, name = "Missing")),
                targetIfMissing = targetSnapshot(20_000),
            )
        }

        assertNull(database.dailyTargetSnapshotDao().findByDate(20_000))
    }

    @Test
    fun `daily target snapshots are keyed by local date`() = runTest {
        val dao = database.dailyTargetSnapshotDao()
        val snapshot =
            DailyTargetSnapshotEntity(
                localDateEpochDay = 20_000,
                kcal = 2100,
                proteinG = 130.0,
                fatG = 58.0,
                carbsG = 240.0,
                effectiveLossRateKgPerWeek = 0.5,
            )

        dao.upsert(snapshot)
        dao.upsert(snapshot.copy(kcal = 2050))

        assertEquals(2050, dao.findByDate(20_000)?.kcal)
        assertNull(dao.findByDate(19_999))
    }

    private fun mealEntry(epochDay: Int, atMillis: Long) = MealEntryEntity(
        localDateEpochDay = epochDay,
        atEpochMillis = atMillis,
        rawUserInput = null,
        source = "MANUAL",
    )

    private fun targetSnapshot(epochDay: Int) = DailyTargetSnapshotEntity(
        localDateEpochDay = epochDay,
        kcal = 2100,
        proteinG = 130.0,
        fatG = 58.0,
        carbsG = 240.0,
        effectiveLossRateKgPerWeek = 0.5,
    )

    private fun foodItem(mealId: Long, position: Int, name: String) = FoodItemEntity(
        mealEntryId = mealId,
        position = position,
        name = name,
        grams = 120.0,
        kcal = 210,
        proteinG = 14.0,
        fatG = 16.0,
        carbsG = 1.0,
        confidence = 0.8f,
        needsReview = false,
    )
}
