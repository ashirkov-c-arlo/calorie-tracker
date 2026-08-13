package app.kcal.domain.model

/** Complete, validated calculator inputs in canonical metric units. */
data class ProfileInputs(
    val currentWeightKg: Double,
    val heightCm: Double,
    val ageYears: Int,
    val energyEquationSex: EnergyEquationSex,
    val activityLevel: ActivityLevel,
    val targetWeightKg: Double,
    val requestedLossRateKgPerWeek: Double,
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
    val requestedLossRateKgPerWeek: Double? = null,
) {
    /** Null while any required input is still missing. */
    fun toInputs(): ProfileInputs? = ProfileInputs(
        currentWeightKg = currentWeightKg ?: return null,
        heightCm = heightCm ?: return null,
        ageYears = ageYears ?: return null,
        energyEquationSex = energyEquationSex ?: return null,
        activityLevel = activityLevel ?: return null,
        targetWeightKg = targetWeightKg ?: return null,
        requestedLossRateKgPerWeek = requestedLossRateKgPerWeek ?: return null,
    )

    val isComplete: Boolean get() = toInputs() != null

    /**
     * Whether the present values may be persisted at all. Stored numbers must be finite,
     * body measurements and age positive, and the requested rate non-negative. Missing
     * values are allowed here: completeness is a separate question.
     */
    val hasValidValues: Boolean
        get() = listOfNotNull(currentWeightKg, heightCm, targetWeightKg).all { it.isFinite() && it > 0.0 } &&
            requestedLossRateKgPerWeek?.let { it.isFinite() && it >= 0.0 } != false &&
            ageYears?.let { it > 0 } != false
}

/** Everything the settings screen and the app shell read from preferences. */
data class UserPreferences(
    val profile: StoredProfile = StoredProfile(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
