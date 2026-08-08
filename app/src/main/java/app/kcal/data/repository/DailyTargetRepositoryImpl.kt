package app.kcal.data.repository

import app.kcal.data.db.DailyTargetSnapshotDao
import app.kcal.data.db.DailyTargetSnapshotEntity
import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.Macros
import app.kcal.domain.repository.DailyTargetRepository
import java.time.LocalDate
import javax.inject.Inject

class DailyTargetRepositoryImpl @Inject constructor(private val dao: DailyTargetSnapshotDao) : DailyTargetRepository {

    override suspend fun find(localDate: LocalDate): DailyTargetSnapshot? =
        dao.findByDate(localDate.epochDayInt())?.toDomain()

    override suspend fun upsert(snapshot: DailyTargetSnapshot) {
        dao.upsert(
            DailyTargetSnapshotEntity(
                localDateEpochDay = snapshot.localDate.epochDayInt(),
                kcal = snapshot.targets.kcal,
                proteinG = snapshot.targets.proteinG,
                fatG = snapshot.targets.fatG,
                carbsG = snapshot.targets.carbsG,
                effectiveLossRateKgPerWeek = snapshot.effectiveLossRateKgPerWeek,
            ),
        )
    }

    override suspend fun delete(localDate: LocalDate) {
        dao.deleteByDate(localDate.epochDayInt())
    }
}

private fun LocalDate.epochDayInt(): Int = toEpochDay().toInt()

private fun DailyTargetSnapshotEntity.toDomain(): DailyTargetSnapshot = DailyTargetSnapshot(
    localDate = LocalDate.ofEpochDay(localDateEpochDay.toLong()),
    targets = Macros(kcal = kcal, proteinG = proteinG, fatG = fatG, carbsG = carbsG),
    effectiveLossRateKgPerWeek = effectiveLossRateKgPerWeek,
)
