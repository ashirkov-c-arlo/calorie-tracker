package app.kcal.domain.model

/** Energy and macronutrient amounts. Always kcal and grams. */
data class Macros(val kcal: Int, val proteinG: Double, val fatG: Double, val carbsG: Double)
