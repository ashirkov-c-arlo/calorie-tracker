package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.testing.FakeDailyTargetRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveProfileTest {

    private val profileRepository = FakeProfileRepository()
    private val dailyTargetRepository = FakeDailyTargetRepository()
    private val timeProvider =
        TimeProvider(
            clock = Clock.fixed(Instant.parse("2026-03-15T23:30:00Z"), ZoneId.of("UTC")),
            zoneId = ZoneId.of("Europe/Berlin"),
        )
    private val calculate = CalculateDailyTargets()
    private val applyTodayTarget = ApplyTodayTarget(dailyTargetRepository, calculate)
    private val saveProfile = SaveProfile(profileRepository, calculate, timeProvider)

    private val today = LocalDate.of(2026, 3, 16)
    private val yesterday = LocalDate.of(2026, 3, 15)

    /** The app shell owns the snapshot write, so the tests trigger it explicitly. */
    private suspend fun syncTarget(localDate: LocalDate = today) =
        applyTodayTarget(profileRepository.state.value.profile, localDate)

    @Test
    fun `saving stores the profile for the local date and reports the estimate`() = runTest {
        val result = saveProfile(completeProfile())

        assertTrue(result is DailyTargetResult.Available)
        assertEquals(listOf(completeProfile()), profileRepository.savedProfiles)
        // 23:30 UTC is already the next day in Berlin, so the save uses the local date.
        assertEquals(listOf(today), profileRepository.savedDates)
    }

    @Test
    fun `saving does not write the snapshot itself`() = runTest {
        saveProfile(completeProfile())

        assertEquals(0, dailyTargetRepository.upsertCount)
        assertNull(dailyTargetRepository.find(today))

        syncTarget()

        assertNotNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `values that break the persisted invariants are never written`() = runTest {
        listOf(
            completeProfile(currentWeightKg = Double.NaN),
            completeProfile(heightCm = Double.POSITIVE_INFINITY),
            completeProfile(targetWeightKg = -1.0),
            completeProfile(requestedLossRateKgPerWeek = -0.5),
            completeProfile(ageYears = 0),
        ).forEach { invalid ->
            val result = saveProfile(invalid)

            assertEquals(
                DailyTargetResult.Unavailable(DailyTargetUnavailableReason.INVALID_MEASUREMENTS),
                result,
                "expected a rejected save for $invalid",
            )
        }

        assertTrue(profileRepository.savedProfiles.isEmpty())
    }

    @Test
    fun `the requested rate is stored exactly as chosen even when a guardrail lowers it`() = runTest {
        val result = saveProfile(completeProfile(requestedLossRateKgPerWeek = 2.0))

        assertEquals(2.0, profileRepository.savedProfiles.single().requestedLossRateKgPerWeek)
        assertTrue(result is DailyTargetResult.Available)
        assertEquals(2.0, result.requestedLossRateKgPerWeek)
        assertTrue(result.effectiveLossRateKgPerWeek < result.requestedLossRateKgPerWeek)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `the shell writes today's snapshot and replaces only today's`() = runTest {
        saveProfile(completeProfile())
        syncTarget()
        val pastSnapshot = assertNotNull(dailyTargetRepository.find(today)).copy(localDate = yesterday)
        dailyTargetRepository.snapshots.value = dailyTargetRepository.snapshots.value + (yesterday to pastSnapshot)

        saveProfile(completeProfile(currentWeightKg = 70.0, targetWeightKg = 65.0))
        syncTarget()

        assertEquals(pastSnapshot, dailyTargetRepository.find(yesterday))
        assertTrue(assertNotNull(dailyTargetRepository.find(today)).targets.kcal != pastSnapshot.targets.kcal)
    }

    @Test
    fun `a snapshot left over from an older profile is replaced on the next sync`() = runTest {
        saveProfile(completeProfile())
        syncTarget()
        val staleTarget = assertNotNull(dailyTargetRepository.find(today))

        // The profile is updated, but the snapshot write fails.
        saveProfile(completeProfile(currentWeightKg = 70.0, targetWeightKg = 65.0))
        dailyTargetRepository.failNextWrites(true)
        assertFailsWith<IOException> { syncTarget() }
        assertEquals(staleTarget, dailyTargetRepository.find(today))

        dailyTargetRepository.failNextWrites(false)
        syncTarget()

        assertTrue(assertNotNull(dailyTargetRepository.find(today)).targets.kcal != staleTarget.targets.kcal)
    }

    @Test
    fun `a snapshot the stored profile no longer justifies is removed`() = runTest {
        saveProfile(completeProfile())
        syncTarget()
        assertNotNull(dailyTargetRepository.find(today))

        saveProfile(completeProfile(ageYears = 15))
        syncTarget()

        assertNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `syncing is stable and stores nothing while the profile is incomplete`() = runTest {
        saveProfile(completeProfile())
        syncTarget()
        val stored = dailyTargetRepository.find(today)

        syncTarget()
        assertEquals(stored, dailyTargetRepository.find(today))

        profileRepository.saveProfile(completeProfile(activityLevel = null), today)
        syncTarget()
        assertNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `a snapshot from a previous day is never rewritten after midnight`() = runTest {
        // The user saved yesterday and the app is opened today: past snapshots are immutable,
        // so only today's is written. A day without a snapshot is a normal state, exactly like
        // any day the app was not opened at all.
        saveProfile(completeProfile())
        syncTarget(yesterday)
        val yesterdaySnapshot = assertNotNull(dailyTargetRepository.find(yesterday))

        profileRepository.saveProfile(completeProfile(currentWeightKg = 70.0), today)
        syncTarget(today)

        assertEquals(yesterdaySnapshot, dailyTargetRepository.find(yesterday))
        assertTrue(assertNotNull(dailyTargetRepository.find(today)).targets.kcal != yesterdaySnapshot.targets.kcal)
    }
}
