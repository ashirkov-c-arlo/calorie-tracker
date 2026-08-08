package app.kcal.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_items",
    foreignKeys = [
        ForeignKey(
            entity = MealEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["meal_entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["meal_entry_id", "position"], unique = true)],
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_entry_id") val mealEntryId: Long,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "grams") val grams: Double?,
    @ColumnInfo(name = "kcal") val kcal: Int,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "confidence") val confidence: Float,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
)
