package app.kcal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
    suspend fun findByDate(epochDay: Int): DailyTargetSnapshotEntity?

    @Query("DELETE FROM daily_target_snapshots WHERE local_date_epoch_day = :epochDay")
    suspend fun deleteByDate(epochDay: Int)
}

@Dao
interface MealEntryDao {

    @Insert
    suspend fun insertMealEntry(entry: MealEntryEntity): Long

    @Insert
    suspend fun insertFoodItems(items: List<FoodItemEntity>)

    @Query("SELECT * FROM meal_entries WHERE local_date_epoch_day = :epochDay ORDER BY at_epoch_millis")
    suspend fun findMealEntriesByDate(epochDay: Int): List<MealEntryEntity>

    @Query("SELECT * FROM food_items WHERE meal_entry_id = :mealEntryId ORDER BY position")
    suspend fun findFoodItems(mealEntryId: Long): List<FoodItemEntity>

    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun deleteMealEntry(id: Long)
}
