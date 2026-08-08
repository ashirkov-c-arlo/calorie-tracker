package app.kcal.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.core.common.TimeProvider
import app.kcal.data.db.KcalDatabase
import app.kcal.data.db.WeightEntryEntity
import app.kcal.data.prefs.ProfilePreferencesDataSource
import app.kcal.data.prefs.ProfilePreferencesDataSource.Keys
import app.kcal.domain.model.ActivityLevel
import app.kcal.domain.model.AppLanguage
import app.kcal.domain.model.EnergyEquationSex
import app.kcal.domain.model.ThemeMode
import app.kcal.domain.model.UnitSystem
import app.kcal.testing.completeProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ProfileRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val today = LocalDate.of(2026, 3, 15)
    private lateinit var database: KcalDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: ProfileRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KcalDatabase::class.java).build()
        dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { temporaryFolder.newFile("test.preferences_pb") },
            )
        repository =
            ProfileRepositoryImpl(
                preferencesDataSource = ProfilePreferencesDataSource(dataStore),
                weightEntryDao = database.weightEntryDao(),
                timeProvider =
                TimeProvider(
                    clock = Clock.fixed(Instant.parse("2026-03-15T09:00:00Z"), ZoneId.of("UTC")),
                    zoneId = ZoneId.of("UTC"),
                ),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a fresh install has no complete profile and no fabricated defaults`() = runTest {
        val preferences = repository.preferences.first()

        assertFalse(preferences.profile.isComplete)
        assertNull(preferences.profile.energyEquationSex)
        assertNull(preferences.profile.activityLevel)
        assertNull(preferences.profile.currentWeightKg)
        assertEquals(UnitSystem.METRIC, preferences.unitSystem)
        assertEquals(AppLanguage.SYSTEM, preferences.appLanguage)
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
    }

    @Test
    fun `saving the profile stores canonical values and upserts today's weight`() = runTest {
        repository.saveProfile(completeProfile())

        val preferences = repository.preferences.first()
        assertTrue(preferences.profile.isComplete)
        assertEquals(176.0, preferences.profile.heightCm)
        assertEquals(EnergyEquationSex.MALE, preferences.profile.energyEquationSex)
        assertEquals(ActivityLevel.LIGHT, preferences.profile.activityLevel)
        assertEquals(82.4, preferences.profile.currentWeightKg)
        assertEquals(82.4, database.weightEntryDao().findByDate(today.toEpochDay().toInt())?.kg)
    }

    @Test
    fun `current weight comes from the latest weight entry and is not duplicated`() = runTest {
        repository.saveProfile(completeProfile())
        database.weightEntryDao().upsert(
            WeightEntryEntity(localDateEpochDay = today.plusDays(1).toEpochDay().toInt(), kg = 81.2),
        )

        assertEquals(81.2, repository.preferences.first().profile.currentWeightKg)

        repository.saveProfile(completeProfile(currentWeightKg = 80.0))

        // The upsert replaces today's entry only, so the newer entry still wins.
        assertEquals(81.2, repository.preferences.first().profile.currentWeightKg)
        assertEquals(80.0, database.weightEntryDao().findByDate(today.toEpochDay().toInt())?.kg)
    }

    @Test
    fun `completeness requires both stored inputs and a current weight`() = runTest {
        dataStore.edit { preferences ->
            preferences[Keys.HEIGHT_CM] = 176.0
            preferences[Keys.AGE_YEARS] = 34
            preferences[Keys.FORMULA_VARIANT] = EnergyEquationSex.MALE.name
            preferences[Keys.ACTIVITY_LEVEL] = ActivityLevel.LIGHT.name
            preferences[Keys.TARGET_WEIGHT_KG] = 78.0
            preferences[Keys.REQUESTED_LOSS_RATE_KG_PER_WEEK] = 0.5
        }
        assertFalse(repository.isProfileComplete.first())

        database.weightEntryDao().upsert(WeightEntryEntity(localDateEpochDay = 20_000, kg = 82.4))

        assertTrue(repository.isProfileComplete.first())
    }

    @Test
    fun `interface preferences round trip and unknown values fall back`() = runTest {
        repository.setUnitSystem(UnitSystem.IMPERIAL)
        repository.setAppLanguage(AppLanguage.RUSSIAN)
        repository.setThemeMode(ThemeMode.BLACK)

        val preferences = repository.preferences.first()
        assertEquals(UnitSystem.IMPERIAL, preferences.unitSystem)
        assertEquals(AppLanguage.RUSSIAN, preferences.appLanguage)
        assertEquals(ThemeMode.BLACK, preferences.themeMode)
        assertEquals(ThemeMode.BLACK, repository.themeMode.first())

        dataStore.edit { it[Keys.THEME_MODE] = "NEON" }
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }
}
