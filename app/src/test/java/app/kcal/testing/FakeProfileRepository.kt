package app.kcal.testing

import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.StoredProfile
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.domain.model.UserPreferences
import app.kcal.domain.model.WeightEntry
import app.kcal.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

/** Hand-written fake so tests never touch DataStore or Room. */
class FakeProfileRepository(
    initial: UserPreferences = UserPreferences(),
    /** Simulates storage that cannot be read; flipping it back lets a retry succeed. */
    var readFails: Boolean = false,
    /** Simulates a failing profile write. */
    var writeFails: Boolean = false,
) : ProfileRepository {

    val state = MutableStateFlow(initial)
    val savedProfiles = mutableListOf<StoredProfile>()
    val savedDates = mutableListOf<LocalDate>()

    /** One entry per local date, like the Room primary key. */
    val weightsByDate = MutableStateFlow(sortedMapOf<LocalDate, Double>())

    /** Every accepted write, in completion order, so serialization can be asserted. */
    val loggedWeights = mutableListOf<WeightEntry>()

    override val weights: Flow<List<WeightEntry>> =
        flow {
            if (readFails) throw IOException("weights unavailable")
            emitAll(weightsByDate.map { entries -> entries.map { (date, kg) -> WeightEntry(date, kg) } })
        }

    override val preferences: Flow<UserPreferences> =
        flow {
            if (readFails) throw IOException("preferences unavailable")
            emitAll(state)
        }

    override val isProfileComplete: Flow<Boolean> = state.map { it.profile.isComplete }

    override val themeMode: Flow<ThemeMode> = state.map { it.themeMode }

    override suspend fun saveProfile(profile: StoredProfile, localDate: LocalDate) {
        if (writeFails) throw IOException("profile storage unavailable")
        savedProfiles += profile
        savedDates += localDate
        state.value = state.value.copy(profile = profile)
        profile.currentWeightKg?.let { logWeight(WeightEntry(localDate, it)) }
    }

    /** Mirrors the implementation: current weight is the latest entry, never a preference. */
    override suspend fun logWeight(entry: WeightEntry) {
        if (writeFails) throw IOException("weight storage unavailable")
        loggedWeights += entry
        weightsByDate.value = sortedMapOf<LocalDate, Double>().apply {
            putAll(weightsByDate.value)
            put(entry.localDate, entry.kg)
        }
        val latest = weightsByDate.value.entries.last()
        state.value = state.value.copy(profile = state.value.profile.copy(currentWeightKg = latest.value))
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
