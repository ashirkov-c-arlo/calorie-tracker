package app.kcal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    @Upsert
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries ORDER BY local_date_epoch_day DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entries WHERE local_date_epoch_day = :epochDay")
    suspend fun findByDate(epochDay: Int): WeightEntryEntity?
}

@Dao
interface DailyTargetSnapshotDao {

    @Upsert
    suspend fun upsert(snapshot: DailyTargetSnapshotEntity)

    @Query("SELECT * FROM daily_target_snapshots WHERE local_date_epoch_day = :epochDay")
    fun observeByDate(epochDay: Int): Flow<DailyTargetSnapshotEntity?>

    @Query("SELECT * FROM daily_target_snapshots WHERE local_date_epoch_day = :epochDay")
    suspend fun findByDate(epochDay: Int): DailyTargetSnapshotEntity?

    @Query("SELECT * FROM daily_target_snapshots")
    fun observeAll(): Flow<List<DailyTargetSnapshotEntity>>

    @Query("DELETE FROM daily_target_snapshots WHERE local_date_epoch_day = :epochDay")
    suspend fun deleteByDate(epochDay: Int)
}

@Dao
interface MealEntryDao {

    @Insert
    suspend fun insertMealEntry(entry: MealEntryEntity): Long

    @Update
    suspend fun updateMealEntry(entry: MealEntryEntity): Int

    @Insert
    suspend fun insertFoodItems(items: List<FoodItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTargetIfMissing(target: DailyTargetSnapshotEntity)

    @Transaction
    @Query(
        "SELECT * FROM meal_entries WHERE local_date_epoch_day = :epochDay " +
            "ORDER BY at_epoch_millis, id",
    )
    fun observeByDate(epochDay: Int): Flow<List<MealEntryWithItems>>

    // ponytail: History observes the whole journal; add a date-range query if it ever gets slow.
    @Transaction
    @Query("SELECT * FROM meal_entries ORDER BY local_date_epoch_day DESC, at_epoch_millis, id")
    fun observeAll(): Flow<List<MealEntryWithItems>>

    @Transaction
    @Query("SELECT * FROM meal_entries WHERE id = :id")
    suspend fun findById(id: Long): MealEntryWithItems?

    @Query(
        "SELECT * FROM meal_entries WHERE local_date_epoch_day = :epochDay " +
            "ORDER BY at_epoch_millis, id",
    )
    suspend fun findMealEntriesByDate(epochDay: Int): List<MealEntryEntity>

    @Query("SELECT * FROM food_items WHERE meal_entry_id = :mealEntryId ORDER BY position")
    suspend fun findFoodItems(mealEntryId: Long): List<FoodItemEntity>

    @Query("DELETE FROM food_items WHERE meal_entry_id = :mealEntryId")
    suspend fun deleteFoodItems(mealEntryId: Long)

    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun deleteMealEntry(id: Long)

    /** Meal, ordered items and a missing current-day target commit or roll back together. */
    @Transaction
    suspend fun save(
        entry: MealEntryEntity,
        items: List<FoodItemEntity>,
        targetIfMissing: DailyTargetSnapshotEntity?,
    ): Long {
        targetIfMissing?.let { insertTargetIfMissing(it) }
        val mealId = if (entry.id == 0L) {
            insertMealEntry(entry)
        } else {
            check(updateMealEntry(entry) == 1) { "Meal does not exist" }
            deleteFoodItems(entry.id)
            entry.id
        }
        insertFoodItems(
            items.mapIndexed { position, item ->
                item.copy(mealEntryId = mealId, position = position)
            },
        )
        return mealId
    }
}
