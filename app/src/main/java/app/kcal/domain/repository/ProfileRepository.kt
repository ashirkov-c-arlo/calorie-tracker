package app.kcal.domain.repository

import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Profile and interface preferences, plus the weight series they are derived from. Current
 * weight is the latest weight entry, never a duplicated preference, and there is no separate
 * onboarding flag.
 */
interface ProfileRepository {

    val preferences: Flow<UserPreferences>

    /** Every logged weight, oldest first. */
    val weights: Flow<List<WeightEntry>>

    val isProfileComplete: Flow<Boolean>

    val themeMode: Flow<ThemeMode>

    /**
     * Stores the calculator inputs and upserts the current weight for [localDate]. The date
     * is passed in so one logical save cannot straddle midnight.
     */
    suspend fun saveProfile(profile: StoredProfile, localDate: LocalDate)

    /** Upserts the weight of its own local date, so re-entry replaces that day's entry. */
    suspend fun logWeight(entry: WeightEntry)

    suspend fun setUnitSystem(unitSystem: UnitSystem)

    suspend fun setAppLanguage(appLanguage: AppLanguage)

    suspend fun setThemeMode(themeMode: ThemeMode)
}
