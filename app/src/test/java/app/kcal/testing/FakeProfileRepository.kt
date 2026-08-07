package app.kcal.testing

import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Hand-written fake so tests never touch DataStore or Room. */
class FakeProfileRepository(initial: UserPreferences = UserPreferences()) : ProfileRepository {

    val state = MutableStateFlow(initial)
    var savedProfiles = mutableListOf<StoredProfile>()

    override val preferences: Flow<UserPreferences> = state

    override val isProfileComplete: Flow<Boolean> = state.map { it.profile.isComplete }

    override val themeMode: Flow<ThemeMode> = state.map { it.themeMode }

    override suspend fun saveProfile(profile: StoredProfile) {
        savedProfiles += profile
        state.value = state.value.copy(profile = profile)
    }

    override suspend fun setUnitSystem(unitSystem: UnitSystem) {
        state.value = state.value.copy(unitSystem = unitSystem)
    }

    override suspend fun setAppLanguage(appLanguage: AppLanguage) {
        state.value = state.value.copy(appLanguage = appLanguage)
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        state.value = state.value.copy(themeMode = themeMode)
    }
}
