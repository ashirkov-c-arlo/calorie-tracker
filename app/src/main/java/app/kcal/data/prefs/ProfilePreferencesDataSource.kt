package app.kcal.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canonical preference storage: kilograms, centimetres and whole years only. Calculator
 * inputs have no defaults, so a missing key means "not entered yet". Current weight is not
 * stored here; it lives in the weight entry table.
 */
@Singleton
class ProfilePreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> =
        dataStore.data.map { stored ->
            UserPreferences(
                profile =
                StoredProfile(
                    currentWeightKg = null,
                    heightCm = stored[Keys.HEIGHT_CM],
                    ageYears = stored[Keys.AGE_YEARS],
                    energyEquationSex = stored[Keys.FORMULA_VARIANT].toEnumOrNull(),
                    activityLevel = stored[Keys.ACTIVITY_LEVEL].toEnumOrNull(),
                    targetWeightKg = stored[Keys.TARGET_WEIGHT_KG],
                    requestedLossRateKgPerWeek = stored[Keys.REQUESTED_LOSS_RATE_KG_PER_WEEK],
                ),
                unitSystem = stored[Keys.UNIT_SYSTEM].toEnumOrNull() ?: UnitSystem.METRIC,
                appLanguage = stored[Keys.APP_LANGUAGE].toEnumOrNull() ?: AppLanguage.SYSTEM,
                themeMode = stored[Keys.THEME_MODE].toEnumOrNull() ?: ThemeMode.SYSTEM,
            )
        }

    /** Writes every calculator input that is present. Current weight is ignored here. */
    suspend fun saveProfile(profile: StoredProfile) {
        dataStore.edit { preferences ->
            profile.heightCm?.let { preferences[Keys.HEIGHT_CM] = it }
            profile.ageYears?.let { preferences[Keys.AGE_YEARS] = it }
            profile.energyEquationSex?.let { preferences[Keys.FORMULA_VARIANT] = it.name }
            profile.activityLevel?.let { preferences[Keys.ACTIVITY_LEVEL] = it.name }
            profile.targetWeightKg?.let { preferences[Keys.TARGET_WEIGHT_KG] = it }
            profile.requestedLossRateKgPerWeek?.let {
                preferences[Keys.REQUESTED_LOSS_RATE_KG_PER_WEEK] = it
            }
        }
    }

    suspend fun setUnitSystem(unitSystem: UnitSystem) {
        dataStore.edit { it[Keys.UNIT_SYSTEM] = unitSystem.name }
    }

    suspend fun setAppLanguage(appLanguage: AppLanguage) {
        dataStore.edit { it[Keys.APP_LANGUAGE] = appLanguage.name }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    object Keys {
        val HEIGHT_CM = doublePreferencesKey("height_cm")
        val AGE_YEARS = intPreferencesKey("age_years")
        val FORMULA_VARIANT = stringPreferencesKey("formula_variant")
        val ACTIVITY_LEVEL = stringPreferencesKey("activity_level")
        val TARGET_WEIGHT_KG = doublePreferencesKey("target_weight_kg")
        val REQUESTED_LOSS_RATE_KG_PER_WEEK = doublePreferencesKey("requested_loss_rate_kg_per_week")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } }
