package app.kcal.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kcal.core.common.TimeProvider
import app.kcal.data.db.KcalDatabase
import app.kcal.data.prefs.ProfilePreferencesDataSource
import app.kcal.domain.usecase.ApplyTodayTarget
import app.kcal.domain.usecase.CalculateDailyTargets
import app.kcal.domain.usecase.DailyTargetResult
import app.kcal.domain.usecase.SaveProfile
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole save path over real DataStore and Room: preferences, the weight entry and
 * today's target snapshot.
 */
@RunWith(AndroidJUnit4::class)
class SaveProfileIntegrationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val today = LocalDate.of(2026, 3, 15)
    private lateinit var database: KcalDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var profileRepository: ProfileRepositoryImpl
    private lateinit var dailyTargetRepository: DailyTargetRepositoryImpl
    private lateinit var saveProfile: SaveProfile
    private lateinit var applyTodayTarget: ApplyTodayTarget
    private lateinit var preferencesDataSource: ProfilePreferencesDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KcalDatabase::class.java).build()
        dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { temporaryFolder.newFile("integration.preferences_pb") },
            )
        val timeProvider =
            TimeProvider(
                clock = Clock.fixed(Instant.parse("2026-03-15T09:00:00Z"), ZoneId.of("UTC")),
                zoneId = ZoneId.of("UTC"),
            )
        preferencesDataSource = ProfilePreferencesDataSource(dataStore)
        profileRepository =
            ProfileRepositoryImpl(
                preferencesDataSource = preferencesDataSource,
                weightEntryDao = database.weightEntryDao(),
            )
        dailyTargetRepository = DailyTargetRepositoryImpl(database.dailyTargetSnapshotDao())
        applyTodayTarget = ApplyTodayTarget(dailyTargetRepository, CalculateDailyTargets())
        saveProfile = SaveProfile(profileRepository, applyTodayTarget, timeProvider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saving writes preferences, the weight entry and today's snapshot`() = runTest {
        val result = saveProfile(completeProfile())

        assertTrue(result is DailyTargetResult.Available)
        val preferences = profileRepository.preferences.first()
        assertTrue(preferences.profile.isComplete)
        assertEquals(82.4, preferences.profile.currentWeightKg)
        assertEquals(82.4, database.weightEntryDao().findByDate(today.toEpochDay().toInt())?.kg)

        val snapshot = assertNotNull(dailyTargetRepository.find(today))
        assertEquals(result.targets, snapshot.targets)
        assertEquals(result.effectiveLossRateKgPerWeek, snapshot.effectiveLossRateKgPerWeek, 1e-9)
    }

    @Test
    fun `the gate opens only after the profile is complete`() = runTest {
        assertFalse(profileRepository.isProfileComplete.first())

        saveProfile(completeProfile(activityLevel = null))
        assertFalse(profileRepository.isProfileComplete.first())
        assertNull(dailyTargetRepository.find(today))

        saveProfile(completeProfile())
        assertTrue(profileRepository.isProfileComplete.first())
        assertNotNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `saving an out of scope age removes the stored target but keeps the profile`() = runTest {
        saveProfile(completeProfile())
        assertNotNull(dailyTargetRepository.find(today))

        saveProfile(completeProfile(ageYears = 15))

        assertNull(dailyTargetRepository.find(today))
        assertEquals(15, profileRepository.preferences.first().profile.ageYears)
    }

    @Test
    fun `startup recreates a snapshot that a partial save left missing`() = runTest {
        saveProfile(completeProfile())
        val expected = assertNotNull(dailyTargetRepository.find(today))
        // Simulates process death between the weight write and the snapshot write.
        dailyTargetRepository.delete(today)

        applyTodayTarget(profileRepository.preferences.first().profile, today)

        assertEquals(expected, dailyTargetRepository.find(today))
    }

    @Test
    fun `an interrupted save between preferences and the weight entry is completed later`() = runTest {
        saveProfile(completeProfile())
        val previousWeight = assertNotNull(database.weightEntryDao().findByDate(today.toEpochDay().toInt())).kg

        // Simulates process death right after the atomic preferences write: the new settings
        // and the pending weight are stored, but Room has not received the weight yet.
        preferencesDataSource.saveProfile(
            completeProfile(currentWeightKg = 79.0, heightCm = 180.0),
            today.toEpochDay().toInt(),
        )
        assertEquals(previousWeight, profileRepository.preferences.first().profile.currentWeightKg)

        profileRepository.completePendingSave()

        val recovered = profileRepository.preferences.first().profile
        assertEquals(79.0, recovered.currentWeightKg)
        assertEquals(180.0, recovered.heightCm)
        assertEquals(79.0, database.weightEntryDao().findByDate(today.toEpochDay().toInt())?.kg)
        assertNull(preferencesDataSource.pendingWeightWrite.first())
    }

    @Test
    fun `a completed save leaves no pending weight write behind`() = runTest {
        saveProfile(completeProfile())

        assertNull(preferencesDataSource.pendingWeightWrite.first())
    }

    @Test
    fun `changing the profile later never rewrites a past snapshot`() = runTest {
        saveProfile(completeProfile())
        val past = assertNotNull(dailyTargetRepository.find(today)).copy(localDate = today.minusDays(3))
        dailyTargetRepository.upsert(past)

        saveProfile(completeProfile(currentWeightKg = 75.0, targetWeightKg = 70.0))

        assertEquals(past, dailyTargetRepository.find(today.minusDays(3)))
        assertTrue(assertNotNull(dailyTargetRepository.find(today)).targets.kcal != past.targets.kcal)
    }
}
