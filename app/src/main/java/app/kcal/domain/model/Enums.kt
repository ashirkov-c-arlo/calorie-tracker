package app.kcal.domain.model

/**
 * Selects one of the two validated Mifflin-St Jeor branches. This is an equation
 * variant, not a gender-identity field, so the constants are never averaged.
 */
enum class EnergyEquationSex(val rmrOffsetKcal: Double) {
    FEMALE(rmrOffsetKcal = -161.0),
    MALE(rmrOffsetKcal = 5.0),
}

/** Habitual physical activity level with its physical activity level (PAL) multiplier. */
enum class ActivityLevel(val pal: Double) {
    SEDENTARY(1.20),
    LIGHT(1.375),
    MODERATE(1.55),
    HIGH(1.725),
}

/** Body measurement units. Nutrition stays in kcal and grams in both systems. */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
}

/** Interface language. `SYSTEM` follows the system language and falls back to English. */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    RUSSIAN("ru"),
}
