package app.kcal.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.kcal.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canonical profile preferences. Values are stored in metric units only; calculator
 * inputs have no defaults, so a missing key means "not entered yet".
 */
data class StoredProfilePreferences(
    val heightCm: Double?,
    val ageYears: Int?,
    val formulaVariant: String?,
    val activityLevel: String?,
    val targetWeightKg: Double?,
    val requestedLossRateKgPerWeek: Double?,
    val themeMode: ThemeMode,
) {
    /**
     * Every calculator input the daily-target formula needs, except current weight, which
     * lives in Room as the latest weight entry.
     */
    val hasAllRequiredCalculatorInputs: Boolean
        get() = heightCm != null &&
            ageYears != null &&
            !formulaVariant.isNullOrBlank() &&
            !activityLevel.isNullOrBlank() &&
            targetWeightKg != null &&
            requestedLossRateKgPerWeek != null
}

@Singleton
class ProfilePreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<StoredProfilePreferences> =
        dataStore.data.map { stored ->
            StoredProfilePreferences(
                heightCm = stored[Keys.HEIGHT_CM],
                ageYears = stored[Keys.AGE_YEARS],
                formulaVariant = stored[Keys.FORMULA_VARIANT],
                activityLevel = stored[Keys.ACTIVITY_LEVEL],
                targetWeightKg = stored[Keys.TARGET_WEIGHT_KG],
                requestedLossRateKgPerWeek = stored[Keys.REQUESTED_LOSS_RATE_KG_PER_WEEK],
                themeMode = stored[Keys.THEME_MODE]?.let(::parseThemeMode) ?: ThemeMode.SYSTEM,
            )
        }

    private fun parseThemeMode(raw: String): ThemeMode? = ThemeMode.entries.firstOrNull { it.name == raw }

    object Keys {
        val HEIGHT_CM = doublePreferencesKey("height_cm")
        val AGE_YEARS = intPreferencesKey("age_years")
        val FORMULA_VARIANT = stringPreferencesKey("formula_variant")
        val ACTIVITY_LEVEL = stringPreferencesKey("activity_level")
        val TARGET_WEIGHT_KG = doublePreferencesKey("target_weight_kg")
        val REQUESTED_LOSS_RATE_KG_PER_WEEK = doublePreferencesKey("requested_loss_rate_kg_per_week")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
