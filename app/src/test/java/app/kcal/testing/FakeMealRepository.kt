package app.kcal.testing

import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.MealEntry
import app.kcal.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

class FakeMealRepository(initial: List<MealEntry> = emptyList()) : MealRepository {

    val meals = MutableStateFlow(initial)
    val targetsEnsured = mutableListOf<DailyTargetSnapshot?>()
    var readFails: Boolean = false
    var writeFails: Boolean = false
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeByDate(localDate: LocalDate): Flow<List<MealEntry>> = flow {
        if (readFails) throw IOException("meal storage unavailable")
        emitAll(
            meals.map { entries ->
                entries
                    .filter { it.localDate == localDate }
                    .sortedWith(compareBy(MealEntry::at, MealEntry::id))
            },
        )
    }

    override suspend fun findById(id: Long): MealEntry? {
        if (readFails) throw IOException("meal storage unavailable")
        return meals.value.firstOrNull { it.id == id }
    }

    override suspend fun save(meal: MealEntry, targetIfMissing: DailyTargetSnapshot?): Long {
        if (writeFails) throw IOException("meal storage unavailable")
        val id = meal.id.takeUnless { it == 0L } ?: nextId++
        meals.value = meals.value.filterNot { it.id == id } + meal.copy(id = id)
        targetsEnsured += targetIfMissing
        return id
    }

    override suspend fun delete(id: Long) {
        if (writeFails) throw IOException("meal storage unavailable")
        meals.value = meals.value.filterNot { it.id == id }
    }
}
