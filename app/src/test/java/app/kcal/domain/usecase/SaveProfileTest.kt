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
    private val applyTodayTarget = ApplyTodayTarget(dailyTargetRepository, CalculateDailyTargets())
    private val saveProfile = SaveProfile(profileRepository, applyTodayTarget, timeProvider)
    private val reconcileTodayTarget =
        ReconcileTodayTarget(profileRepository, applyTodayTarget, timeProvider)

    private val today = LocalDate.of(2026, 3, 16)
    private val yesterday = LocalDate.of(2026, 3, 15)

    @Test
    fun `saving stores the profile and creates today's snapshot`() = runTest {
        val result = saveProfile(completeProfile())

        assertTrue(result is DailyTargetResult.Available)
        assertEquals(listOf(completeProfile()), profileRepository.savedProfiles)

        // 23:30 UTC is already the next day in Berlin, so the snapshot uses the local date.
        val snapshot = dailyTargetRepository.find(today)
        assertEquals(result.targets, snapshot?.targets)
        assertEquals(result.effectiveLossRateKgPerWeek, snapshot?.effectiveLossRateKgPerWeek)
    }

    @Test
    fun `saving again on the same day replaces only today's snapshot`() = runTest {
        saveProfile(completeProfile())
        val pastSnapshot = dailyTargetRepository.find(today)!!.copy(localDate = yesterday)
        dailyTargetRepository.snapshots.value = dailyTargetRepository.snapshots.value + (yesterday to pastSnapshot)

        saveProfile(completeProfile(currentWeightKg = 80.0))

        assertEquals(pastSnapshot, dailyTargetRepository.find(yesterday))
        assertTrue(dailyTargetRepository.find(today)!!.targets.kcal != pastSnapshot.targets.kcal)
    }

    @Test
    fun `an unavailable target removes the stale snapshot instead of keeping it`() = runTest {
        saveProfile(completeProfile())
        assertNotNull(dailyTargetRepository.find(today))
        val pastSnapshot = dailyTargetRepository.find(today)!!.copy(localDate = yesterday)
        dailyTargetRepository.snapshots.value = dailyTargetRepository.snapshots.value + (yesterday to pastSnapshot)

        val result = saveProfile(completeProfile(ageYears = 15))

        assertEquals(DailyTargetResult.Unavailable(DailyTargetUnavailableReason.AGE_BELOW_MINIMUM), result)
        assertNull(dailyTargetRepository.find(today))
        assertEquals(pastSnapshot, dailyTargetRepository.find(yesterday))
    }

    @Test
    fun `an incomplete profile stores no snapshot`() = runTest {
        val result = saveProfile(completeProfile(activityLevel = null))

        assertEquals(
            DailyTargetResult.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
            result,
        )
        assertNull(dailyTargetRepository.find(today))
        assertEquals(0, dailyTargetRepository.upsertCount)
    }

    @Test
    fun `repeating the same save is idempotent`() = runTest {
        saveProfile(completeProfile())
        val first = dailyTargetRepository.snapshots.value

        saveProfile(completeProfile())

        assertEquals(first, dailyTargetRepository.snapshots.value)
    }

    @Test
    fun `a failed target write keeps the stored profile and is repaired on the next start`() = runTest {
        dailyTargetRepository.failNextWrites(true)

        assertFailsWith<IOException> { saveProfile(completeProfile()) }

        // The profile is stored and now reports complete, but the target is missing.
        assertEquals(listOf(completeProfile()), profileRepository.savedProfiles)
        assertNull(dailyTargetRepository.find(today))

        dailyTargetRepository.failNextWrites(false)
        reconcileTodayTarget()

        assertNotNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `reconciliation rewrites a snapshot left over from an older profile`() = runTest {
        saveProfile(completeProfile())
        val staleTarget = dailyTargetRepository.find(today)

        // The profile is updated, but the target write fails afterwards.
        dailyTargetRepository.failNextWrites(true)
        assertFailsWith<IOException> { saveProfile(completeProfile(currentWeightKg = 70.0, targetWeightKg = 65.0)) }
        assertEquals(staleTarget, dailyTargetRepository.find(today))

        dailyTargetRepository.failNextWrites(false)
        reconcileTodayTarget()

        val repaired = assertNotNull(dailyTargetRepository.find(today))
        assertTrue(repaired.targets.kcal != staleTarget!!.targets.kcal)
    }

    @Test
    fun `reconciliation is stable for an unchanged profile`() = runTest {
        saveProfile(completeProfile())
        val stored = dailyTargetRepository.find(today)

        reconcileTodayTarget()

        assertEquals(stored, dailyTargetRepository.find(today))
    }

    @Test
    fun `reconciliation removes a snapshot that the stored profile no longer justifies`() = runTest {
        saveProfile(completeProfile())
        assertNotNull(dailyTargetRepository.find(today))

        dailyTargetRepository.failNextWrites(true)
        assertFailsWith<IOException> { saveProfile(completeProfile(ageYears = 15)) }
        assertNotNull(dailyTargetRepository.find(today))

        dailyTargetRepository.failNextWrites(false)
        reconcileTodayTarget()

        assertNull(dailyTargetRepository.find(today))
    }

    @Test
    fun `reconciliation stores nothing while the profile is incomplete`() = runTest {
        profileRepository.saveProfile(completeProfile(activityLevel = null), today)

        reconcileTodayTarget()

        assertNull(dailyTargetRepository.find(today))
        assertEquals(0, dailyTargetRepository.upsertCount)
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
        assertEquals(0, dailyTargetRepository.upsertCount)
    }

    @Test
    fun `the weight entry and the snapshot share one local date`() = runTest {
        saveProfile(completeProfile())

        assertEquals(listOf(today), profileRepository.savedDates)
        assertEquals(today, dailyTargetRepository.snapshots.value.keys.single())
    }
}
