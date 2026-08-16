package app.kcal.domain.model

/** Complete, validated calculator inputs in canonical metric units. */
data class ProfileInputs(
    val currentWeightKg: Double,
    val heightCm: Double,
    val ageYears: Int,
    val energyEquationSex: EnergyEquationSex,
    val activityLevel: ActivityLevel,
    val targetWeightKg: Double,
    /** Null only when no deficit band applies, that is when the goal is maintenance. */
    val lossPace: LossPace?,
)

/**
 * Calculator inputs as stored. Any field may still be missing, because required inputs
 * have no defaults; current weight comes from the latest weight entry.
 */
data class StoredProfile(
    val currentWeightKg: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val energyEquationSex: EnergyEquationSex? = null,
    val activityLevel: ActivityLevel? = null,
    val targetWeightKg: Double? = null,
    val lossPace: LossPace? = null,
) {
    /**
     * Null while any required input is still missing. A pace is required exactly when a
     * [DeficitBand] applies: below the reference body mass index there is no deficit to pick.
     */
    fun toInputs(): ProfileInputs? {
        val weightKg = currentWeightKg ?: return null
        val height = heightCm ?: return null
        val age = ageYears ?: return null
        val sex = energyEquationSex ?: return null
        val activity = activityLevel ?: return null
        val targetKg = targetWeightKg ?: return null
        if (lossPace == null && DeficitBand.forBody(weightKg, height, activity) != null) return null
        return ProfileInputs(
            currentWeightKg = weightKg,
            heightCm = height,
            ageYears = age,
            energyEquationSex = sex,
            activityLevel = activity,
            targetWeightKg = targetKg,
            lossPace = lossPace,
        )
    }

    val isComplete: Boolean get() = toInputs() != null

    /**
     * Whether the present values may be persisted at all. Stored numbers must be finite and
     * body measurements and age positive. Missing values are allowed here: completeness is a
     * separate question.
     */
    val hasValidValues: Boolean
        get() = listOfNotNull(currentWeightKg, heightCm, targetWeightKg).all { it.isFinite() && it > 0.0 } &&
            ageYears?.let { it > 0 } != false
}

/** Everything the settings screen and the app shell read from preferences. */
data class UserPreferences(
    val profile: StoredProfile = StoredProfile(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
