package app.kcal.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One weight measurement per local date; re-entry upserts by date. */
@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey @ColumnInfo(name = "local_date_epoch_day") val localDateEpochDay: Int,
    @ColumnInfo(name = "kg") val kg: Double,
)
