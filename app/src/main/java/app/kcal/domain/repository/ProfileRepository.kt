package app.kcal.domain.repository

import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Profile and interface preferences. Current weight is the latest weight entry, never a
 * duplicated preference, and there is no separate onboarding flag.
 */
interface ProfileRepository {

    val preferences: Flow<UserPreferences>

    val isProfileComplete: Flow<Boolean>

    val themeMode: Flow<ThemeMode>

    /** Stores the calculator inputs and upserts the current weight for [today]. */
    suspend fun saveProfile(profile: StoredProfile)

    suspend fun setUnitSystem(unitSystem: UnitSystem)

    suspend fun setAppLanguage(appLanguage: AppLanguage)

    suspend fun setThemeMode(themeMode: ThemeMode)
}
