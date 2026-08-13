package app.kcal.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.data.db.KcalDatabase
import app.kcal.domain.model.HistoryWeek
import app.kcal.domain.usecase.AggregateMealMacros
import app.kcal.domain.usecase.BuildHistory
import app.kcal.testing.foodItem
import app.kcal.testing.mealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * History over real Room: editing a stored meal must reach the open history subscription and
 * update the day and its ISO week. Uses `runBlocking` so Room's invalidation tracker runs on
 * real threads and real timeouts apply.
 */
@RunWith(AndroidJUnit4::class)
class HistoryIntegrationTest {

    private val monday = LocalDate.of(2026, 3, 16)
    private val wednesday = LocalDate.of(2026, 3, 18)
    private lateinit var database: KcalDatabase
    private lateinit var repository: MealRepositoryImpl
    private val buildHistory = BuildHistory(AggregateMealMacros())

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
    fun `editing a stored meal updates the day and week totals of an open history`() = runBlocking {
        val editedId =
            repository.save(mealEntry(id = 0, localDate = monday, items = listOf(foodItem(kcal = 400))), null)
        repository.save(mealEntry(id = 0, localDate = wednesday, items = listOf(foodItem(kcal = 600))), null)
        val emissions = Channel<List<HistoryWeek>>(Channel.UNLIMITED)
        val collector =
            launch(Dispatchers.Default) {
                repository.observeAll().map { meals -> buildHistory(meals, emptyList()) }.collect { weeks ->
                    emissions.send(weeks)
                }
            }

        // Receiving the initial value proves the subscription is live before the edit, and a
        // missing emission fails on the timeout instead of hanging the test.
        val initial = withTimeout(TIMEOUT_MILLIS) { emissions.receive() }.single()
        assertEquals(1_000L, initial.consumed.kcal)
        assertEquals(400L, initial.days.last().consumed.kcal)

        val stored = assertNotNull(repository.findById(editedId))
        repository.save(stored.copy(items = listOf(foodItem(kcal = 900))), null)

        val updated =
            withTimeout(TIMEOUT_MILLIS) {
                var weeks = emissions.receive().single()
                while (weeks.consumed.kcal == 1_000L) weeks = emissions.receive().single()
                weeks
            }
        collector.cancel()

        assertEquals(1_500L, updated.consumed.kcal)
        assertEquals(monday, updated.days.last().localDate)
        assertEquals(900L, updated.days.last().consumed.kcal)
        assertEquals(600L, updated.days.first().consumed.kcal)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
