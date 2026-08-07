package app.kcal.domain.repository

import app.kcal.domain.model.DailyTargetSnapshot
import java.time.LocalDate

/** Immutable per-date target storage. Only the current date may be replaced or removed. */
interface DailyTargetRepository {

    suspend fun find(localDate: LocalDate): DailyTargetSnapshot?

    suspend fun upsert(snapshot: DailyTargetSnapshot)

    suspend fun delete(localDate: LocalDate)
}
