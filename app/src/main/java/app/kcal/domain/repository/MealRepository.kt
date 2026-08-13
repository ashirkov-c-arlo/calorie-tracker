package app.kcal.domain.repository

import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.MealEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MealRepository {

    fun observeByDate(localDate: LocalDate): Flow<List<MealEntry>>

    /** The whole journal, newest day first, for day and ISO-week history. */
    fun observeAll(): Flow<List<MealEntry>>

    suspend fun findById(id: Long): MealEntry?

    /** Saves the meal and inserts [targetIfMissing] in the same atomic storage operation. */
    suspend fun save(meal: MealEntry, targetIfMissing: DailyTargetSnapshot?): Long

    suspend fun delete(id: Long)
}
