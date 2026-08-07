package app.kcal.data.repository

import app.kcal.core.common.TimeProvider
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
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val preferencesDataSource: ProfilePreferencesDataSource,
    private val weightEntryDao: WeightEntryDao,
    private val timeProvider: TimeProvider,
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

    override suspend fun saveProfile(profile: StoredProfile) {
        preferencesDataSource.saveProfile(profile)
        profile.currentWeightKg?.let { weightKg ->
            weightEntryDao.upsert(
                WeightEntryEntity(
                    localDateEpochDay = timeProvider.today().toEpochDay().toInt(),
                    kg = weightKg,
                ),
            )
        }
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
