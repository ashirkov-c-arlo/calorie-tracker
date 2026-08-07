package app.kcal.testing

import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.repository.DailyTargetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeDailyTargetRepository : DailyTargetRepository {

    val snapshots = MutableStateFlow(emptyMap<LocalDate, DailyTargetSnapshot>())
    var upsertCount: Int = 0

    override fun observe(localDate: LocalDate): Flow<DailyTargetSnapshot?> = snapshots.map { it[localDate] }

    override suspend fun find(localDate: LocalDate): DailyTargetSnapshot? = snapshots.value[localDate]

    override suspend fun upsert(snapshot: DailyTargetSnapshot) {
        upsertCount++
        snapshots.value = snapshots.value + (snapshot.localDate to snapshot)
    }
}
