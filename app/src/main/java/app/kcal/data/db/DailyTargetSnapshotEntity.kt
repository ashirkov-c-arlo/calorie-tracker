package app.kcal.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The target that was active on a given local date. Past rows are immutable. */
@Entity(tableName = "daily_target_snapshots")
data class DailyTargetSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "local_date_epoch_day") val localDateEpochDay: Int,
    @ColumnInfo(name = "kcal") val kcal: Int,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "effective_loss_rate_kg_week") val effectiveLossRateKgPerWeek: Double,
)
