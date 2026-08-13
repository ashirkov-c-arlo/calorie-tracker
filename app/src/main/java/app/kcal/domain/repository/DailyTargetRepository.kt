package app.kcal.domain.repository

import app.kcal.domain.model.DailyTargetSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Immutable per-date target storage. Only the current date may be replaced or removed. */
interface DailyTargetRepository {

    fun observe(localDate: LocalDate): Flow<DailyTargetSnapshot?>

    fun observeAll(): Flow<List<DailyTargetSnapshot>>

    suspend fun find(localDate: LocalDate): DailyTargetSnapshot?

    suspend fun upsert(snapshot: DailyTargetSnapshot)

    suspend fun delete(localDate: LocalDate)
}
