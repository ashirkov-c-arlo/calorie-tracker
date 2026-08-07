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
import kotlinx.coroutines.flow.first
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
     * The calculator inputs go into one atomic preferences edit that also records the weight
     * Room still has to receive. The weight entry is written next and the marker is cleared
     * last, so an interruption leaves a pending write that [completePendingSave] finishes
     * instead of a silent mix of new settings and an old weight.
     */
    override suspend fun saveProfile(profile: StoredProfile, localDate: LocalDate) {
        preferencesDataSource.saveProfile(profile, localDate.toEpochDay().toInt())
        completePendingSave()
    }

    override suspend fun completePendingSave() {
        val pending = preferencesDataSource.pendingWeightWrite.first() ?: return
        weightEntryDao.upsert(
            WeightEntryEntity(localDateEpochDay = pending.localDateEpochDay, kg = pending.kg),
        )
        preferencesDataSource.clearPendingWeightWrite()
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
