package app.kcal.data.repository

import app.kcal.data.db.WeightEntryDao
import app.kcal.data.prefs.ProfilePreferencesDataSource
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val preferences: ProfilePreferencesDataSource,
    private val weightEntryDao: WeightEntryDao,
) : ProfileRepository {

    override val isProfileComplete: Flow<Boolean> =
        combine(
            preferences.preferences,
            weightEntryDao.observeLatest(),
        ) { stored, latestWeight ->
            stored.hasAllRequiredCalculatorInputs && latestWeight != null
        }

    override val themeMode: Flow<ThemeMode> = preferences.preferences.map { it.themeMode }
}
