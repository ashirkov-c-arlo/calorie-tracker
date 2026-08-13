package app.kcal.testing

import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.repository.DailyTargetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

class FakeDailyTargetRepository(private var failOnWrite: Boolean = false) : DailyTargetRepository {

    val snapshots = MutableStateFlow(emptyMap<LocalDate, DailyTargetSnapshot>())
    var upsertCount: Int = 0

    /** Simulates a Room write that fails after the profile was already stored. */
    fun failNextWrites(fail: Boolean) {
        failOnWrite = fail
    }

    override fun observe(localDate: LocalDate): Flow<DailyTargetSnapshot?> = snapshots.map { it[localDate] }

    override fun observeAll(): Flow<List<DailyTargetSnapshot>> = snapshots.map { it.values.toList() }

    override suspend fun find(localDate: LocalDate): DailyTargetSnapshot? = snapshots.value[localDate]

    override suspend fun upsert(snapshot: DailyTargetSnapshot) {
        if (failOnWrite) throw IOException("target storage unavailable")
        upsertCount++
        snapshots.value = snapshots.value + (snapshot.localDate to snapshot)
    }

    override suspend fun delete(localDate: LocalDate) {
        if (failOnWrite) throw IOException("target storage unavailable")
        snapshots.value = snapshots.value - localDate
    }
}
