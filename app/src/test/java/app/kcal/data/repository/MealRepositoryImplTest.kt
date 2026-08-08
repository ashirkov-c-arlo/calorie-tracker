package app.kcal.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.data.db.KcalDatabase
import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.Macros
import app.kcal.testing.foodItem
import app.kcal.testing.mealEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MealRepositoryImplTest {

    private lateinit var database: KcalDatabase
    private lateinit var repository: MealRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KcalDatabase::class.java).build()
        repository = MealRepositoryImpl(database.mealEntryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save maps the domain meal and marks soft review values`() = runTest {
        val date = LocalDate.of(2026, 3, 15)
        val target =
            DailyTargetSnapshot(
                localDate = date,
                targets = Macros(kcal = 2000, proteinG = 100.0, fatG = 55.0, carbsG = 275.0),
                effectiveLossRateKgPerWeek = 0.4,
            )
        val id =
            repository.save(
                mealEntry(id = 0, localDate = date, items = listOf(foodItem(kcal = 5001))),
                target,
            )

        val stored = repository.observeByDate(date).first().single()
        assertEquals(id, stored.id)
        assertEquals(5001, stored.items.single().macros.kcal)
        assertTrue(database.mealEntryDao().findFoodItems(id).single().needsReview)
        assertEquals(2000, database.dailyTargetSnapshotDao().findByDate(date.toEpochDay().toInt())?.kcal)
    }

    @Test
    fun `delete removes the meal and its relational items`() = runTest {
        val date = LocalDate.of(2026, 3, 15)
        val id = repository.save(mealEntry(id = 0, localDate = date), targetIfMissing = null)

        repository.delete(id)

        assertNull(repository.findById(id))
        assertTrue(database.mealEntryDao().findFoodItems(id).isEmpty())
    }
}
