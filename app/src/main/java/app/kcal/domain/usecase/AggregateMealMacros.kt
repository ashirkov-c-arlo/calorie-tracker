package app.kcal.domain.usecase

import app.kcal.domain.model.FoodItem
import app.kcal.domain.model.MacroTotals
import app.kcal.domain.model.MealEntry
import java.math.BigDecimal

/** Sums locally stored nutrition; an LLM never performs journal arithmetic. */
class AggregateMealMacros {

    operator fun invoke(meals: Iterable<MealEntry>): MacroTotals = items(meals.flatMap { it.items })

    fun items(items: Iterable<FoodItem>): MacroTotals = items.fold(MacroTotals.ZERO) { total, item ->
        MacroTotals(
            kcal = total.kcal + item.macros.kcal.toLong(),
            proteinG = total.proteinG + item.macros.proteinG.toExactDecimal(),
            fatG = total.fatG + item.macros.fatG.toExactDecimal(),
            carbsG = total.carbsG + item.macros.carbsG.toExactDecimal(),
        )
    }

    private fun Double.toExactDecimal(): BigDecimal = BigDecimal.valueOf(this)
}
