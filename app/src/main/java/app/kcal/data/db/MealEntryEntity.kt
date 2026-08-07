package app.kcal.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A chronological meal. There is deliberately no meal-type column and no photo reference:
 * photos are transient request inputs only.
 */
@Entity(
    tableName = "meal_entries",
    indices = [Index("local_date_epoch_day")],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_date_epoch_day") val localDateEpochDay: Int,
    @ColumnInfo(name = "at_epoch_millis") val atEpochMillis: Long,
    @ColumnInfo(name = "raw_user_input") val rawUserInput: String?,
    @ColumnInfo(name = "source") val source: String,
)
