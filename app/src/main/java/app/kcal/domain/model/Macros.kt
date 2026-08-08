package app.kcal.domain.model

/** Energy and macronutrient amounts. Always kcal and grams. */
data class Macros(val kcal: Int, val proteinG: Double, val fatG: Double, val carbsG: Double) {
    companion object {
        val ZERO = Macros(kcal = 0, proteinG = 0.0, fatG = 0.0, carbsG = 0.0)
    }
}
