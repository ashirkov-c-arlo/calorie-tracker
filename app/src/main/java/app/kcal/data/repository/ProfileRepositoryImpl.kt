package app.kcal.data.repository

import app.kcal.data.db.WeightEntryDao
import app.kcal.data.db.WeightEntryEntity
import app.kcal.data.prefs.ProfilePreferencesDataSource
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val preferencesDataSource: ProfilePreferencesDataSource,
    private val weightEntryDao: WeightEntryDao,
) : ProfileRepository {

    override val preferences: Flow<UserPreferences> =
        combine(
            preferencesDataSource.preferences,
            weightEntryDao.observeLatest(),
        ) { stored, latestWeight ->
            stored.copy(profile = stored.profile.copy(currentWeightKg = latestWeight?.kg))
        }

    override val isProfileComplete: Flow<Boolean> = preferences.map { it.profile.isComplete }

    override val themeMode: Flow<ThemeMode> = preferencesDataSource.preferences.map { it.themeMode }

    /**
     * The weight entry is written before the calculator inputs, and the inputs go into one
     * atomic preferences edit. An interruption can therefore only lose the edit itself: what
     * remains is the previous, consistent set of settings together with a freshly logged
     * weight, never new settings paired with a stale weight. Current weight is never
     * duplicated into preferences.
     */
    override suspend fun saveProfile(profile: StoredProfile, localDate: LocalDate) {
        profile.currentWeightKg?.let { weightKg ->
            weightEntryDao.upsert(
                WeightEntryEntity(localDateEpochDay = localDate.toEpochDay().toInt(), kg = weightKg),
            )
        }
        preferencesDataSource.saveProfile(profile)
    }

    override suspend fun setUnitSystem(unitSystem: UnitSystem) {
        preferencesDataSource.setUnitSystem(unitSystem)
    }

    override suspend fun setAppLanguage(appLanguage: AppLanguage) {
        preferencesDataSource.setAppLanguage(appLanguage)
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        preferencesDataSource.setThemeMode(themeMode)
    }
}
