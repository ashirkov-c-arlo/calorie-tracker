package app.kcal.data.repository

import app.kcal.data.db.DailyTargetSnapshotEntity
import app.kcal.data.db.FoodItemEntity
import app.kcal.data.db.MealEntryDao
import app.kcal.data.db.MealEntryEntity
import app.kcal.data.db.MealEntryWithItems
import app.kcal.domain.model.DailyTargetSnapshot
import app.kcal.domain.model.EntrySource
import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.Macros
import app.kcal.domain.model.MealEntry
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.usecase.needsReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class MealRepositoryImpl @Inject constructor(private val dao: MealEntryDao) : MealRepository {

    override fun observeByDate(localDate: LocalDate): Flow<List<MealEntry>> =
        dao.observeByDate(localDate.epochDayInt()).map { meals -> meals.map(MealEntryWithItems::toDomain) }

    override fun observeAll(): Flow<List<MealEntry>> =
        dao.observeAll().map { meals -> meals.map(MealEntryWithItems::toDomain) }

    override suspend fun findById(id: Long): MealEntry? = dao.findById(id)?.toDomain()

    override suspend fun save(meal: MealEntry, targetIfMissing: DailyTargetSnapshot?): Long = dao.save(
        entry = meal.toEntity(),
        items = meal.items.map(FoodItem::toEntity),
        targetIfMissing = targetIfMissing?.toEntity(),
    )

    override suspend fun delete(id: Long) {
        dao.deleteMealEntry(id)
    }
}

private fun MealEntry.toEntity(): MealEntryEntity = MealEntryEntity(
    id = id,
    localDateEpochDay = localDate.epochDayInt(),
    atEpochMillis = at.toEpochMilli(),
    rawUserInput = rawUserInput,
    source = source.name,
    summary = summary,
)

private fun FoodItem.toEntity(): FoodItemEntity = FoodItemEntity(
    mealEntryId = 0,
    position = 0,
    name = name,
    grams = grams,
    kcal = macros.kcal,
    proteinG = macros.proteinG,
    fatG = macros.fatG,
    carbsG = macros.carbsG,
    confidence = confidence,
    needsReview = needsReview(),
)

private fun DailyTargetSnapshot.toEntity(): DailyTargetSnapshotEntity = DailyTargetSnapshotEntity(
    localDateEpochDay = localDate.epochDayInt(),
    kcal = targets.kcal,
    proteinG = targets.proteinG,
    fatG = targets.fatG,
    carbsG = targets.carbsG,
    effectiveLossRateKgPerWeek = effectiveLossRateKgPerWeek,
)

private fun MealEntryWithItems.toDomain(): MealEntry = MealEntry(
    id = meal.id,
    localDate = LocalDate.ofEpochDay(meal.localDateEpochDay.toLong()),
    at = Instant.ofEpochMilli(meal.atEpochMillis),
    items = items.sortedBy(FoodItemEntity::position).map(FoodItemEntity::toDomain),
    rawUserInput = meal.rawUserInput,
    source = EntrySource.valueOf(meal.source),
    summary = meal.summary,
)

private fun FoodItemEntity.toDomain(): FoodItem = FoodItem(
    name = name,
    grams = grams,
    macros = Macros(kcal = kcal, proteinG = proteinG, fatG = fatG, carbsG = carbsG),
    confidence = confidence,
)

private fun LocalDate.epochDayInt(): Int = toEpochDay().toInt()
