package app.kcal.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MealEntryEntity::class,
        FoodItemEntity::class,
        WeightEntryEntity::class,
        DailyTargetSnapshotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class KcalDatabase : RoomDatabase() {

    abstract fun mealEntryDao(): MealEntryDao

    abstract fun weightEntryDao(): WeightEntryDao

    abstract fun dailyTargetSnapshotDao(): DailyTargetSnapshotDao

    companion object {
        const val NAME: String = "kcal.db"
    }
}
