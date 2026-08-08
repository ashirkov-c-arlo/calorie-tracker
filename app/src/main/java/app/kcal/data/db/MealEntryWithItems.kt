package app.kcal.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class MealEntryWithItems(
    @Embedded val meal: MealEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "meal_entry_id")
    val items: List<FoodItemEntity>,
)
