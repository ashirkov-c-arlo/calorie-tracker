package app.kcal.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.data.db.KcalDatabase
import app.kcal.data.db.WeightEntryEntity
import app.kcal.data.prefs.ProfilePreferencesDataSource
import app.kcal.data.prefs.ProfilePreferencesDataSource.Keys
import app.kcal.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ProfileRepositoryImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
        repository = ProfileRepositoryImpl(ProfilePreferencesDataSource(dataStore), database.weightEntryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a fresh install has no complete profile and no fabricated defaults`() = runTest {
        assertFalse(repository.isProfileComplete.first())
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun `calculator inputs without a current weight stay incomplete`() = runTest {
        storeCalculatorInputs()

        assertFalse(repository.isProfileComplete.first())
    }

    @Test
    fun `a current weight without calculator inputs stays incomplete`() = runTest {
        database.weightEntryDao().upsert(WeightEntryEntity(localDateEpochDay = 20_000, kg = 82.4))

        assertFalse(repository.isProfileComplete.first())
    }

    @Test
    fun `calculator inputs plus a current weight complete the profile`() = runTest {
        storeCalculatorInputs()
        database.weightEntryDao().upsert(WeightEntryEntity(localDateEpochDay = 20_000, kg = 82.4))

        assertTrue(repository.isProfileComplete.first())
    }

    @Test
    fun `stored theme mode is exposed and unknown values fall back to system`() = runTest {
        dataStore.edit { it[Keys.THEME_MODE] = ThemeMode.BLACK.name }
        assertEquals(ThemeMode.BLACK, repository.themeMode.first())

        dataStore.edit { it[Keys.THEME_MODE] = "NEON" }
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    private suspend fun storeCalculatorInputs() {
        dataStore.edit { preferences ->
            preferences[Keys.HEIGHT_CM] = 176.0
            preferences[Keys.AGE_YEARS] = 34
            preferences[Keys.FORMULA_VARIANT] = "MALE"
            preferences[Keys.ACTIVITY_LEVEL] = "LIGHT"
            preferences[Keys.TARGET_WEIGHT_KG] = 78.0
            preferences[Keys.REQUESTED_LOSS_RATE_KG_PER_WEEK] = 0.5
        }
    }
}
