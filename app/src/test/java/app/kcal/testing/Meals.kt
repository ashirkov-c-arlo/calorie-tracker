package app.kcal.testing

import app.kcal.domain.model.EntrySource
import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.Macros
import app.kcal.domain.model.MealEntry
import java.time.Instant
import java.time.LocalDate

fun foodItem(
    name: String = "Oatmeal",
    grams: Double? = 100.0,
    kcal: Int = 300,
    proteinG: Double = 10.0,
    fatG: Double = 6.0,
    carbsG: Double = 50.0,
    confidence: Float = 1f,
): FoodItem = FoodItem(
    name = name,
    grams = grams,
    macros = Macros(kcal = kcal, proteinG = proteinG, fatG = fatG, carbsG = carbsG),
    confidence = confidence,
)

fun mealEntry(
    id: Long = 1,
    localDate: LocalDate = LocalDate.of(2026, 3, 15),
    at: Instant = Instant.parse("2026-03-15T09:00:00Z"),
    items: List<FoodItem> = listOf(foodItem()),
): MealEntry = MealEntry(
    id = id,
    localDate = localDate,
    at = at,
    items = items,
    rawUserInput = null,
    source = EntrySource.MANUAL,
)
