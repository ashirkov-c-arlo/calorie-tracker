package app.kcal.domain.usecase

import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.Macros
import app.kcal.domain.model.MealEntry

/** Sums locally stored nutrition; an LLM never performs journal arithmetic. */
class AggregateMealMacros {

    operator fun invoke(meals: Iterable<MealEntry>): Macros = items(meals.flatMap { it.items })

    fun items(items: Iterable<FoodItem>): Macros = items.fold(Macros.ZERO) { total, item ->
        Macros(
            kcal = Math.addExact(total.kcal, item.macros.kcal),
            proteinG = total.proteinG + item.macros.proteinG,
            fatG = total.fatG + item.macros.fatG,
            carbsG = total.carbsG + item.macros.carbsG,
        )
    }
}
