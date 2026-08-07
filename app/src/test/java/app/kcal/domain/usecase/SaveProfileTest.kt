package app.kcal.domain.usecase

import app.kcal.core.common.TimeProvider
import app.kcal.testing.FakeDailyTargetRepository
import app.kcal.testing.FakeProfileRepository
import app.kcal.testing.completeProfile
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
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
    private val saveProfile =
        SaveProfile(profileRepository, dailyTargetRepository, CalculateDailyTargets(), timeProvider)

    @Test
    fun `saving stores the profile and creates today's snapshot`() = runTest {
        val result = saveProfile(completeProfile())

        assertTrue(result is DailyTargetResult.Available)
        assertEquals(listOf(completeProfile()), profileRepository.savedProfiles)

        // 23:30 UTC is already the next day in Berlin, so the snapshot uses the local date.
        val snapshot = dailyTargetRepository.find(LocalDate.of(2026, 3, 16))
        assertEquals(result.targets, snapshot?.targets)
        assertEquals(result.effectiveLossRateKgPerWeek, snapshot?.effectiveLossRateKgPerWeek)
    }

    @Test
    fun `saving again on the same day replaces only today's snapshot`() = runTest {
        val yesterday = LocalDate.of(2026, 3, 15)
        saveProfile(completeProfile())
        val untouched = dailyTargetRepository.snapshots.value.toMutableMap()
        untouched[yesterday] =
            dailyTargetRepository.find(LocalDate.of(2026, 3, 16))!!.copy(localDate = yesterday)
        dailyTargetRepository.snapshots.value = untouched
        val pastSnapshot = dailyTargetRepository.find(yesterday)

        saveProfile(completeProfile(currentWeightKg = 80.0))

        assertEquals(pastSnapshot, dailyTargetRepository.find(yesterday))
        val today = dailyTargetRepository.find(LocalDate.of(2026, 3, 16))
        assertTrue(today!!.targets.kcal != pastSnapshot!!.targets.kcal)
    }

    @Test
    fun `an incomplete profile stores no snapshot`() = runTest {
        val result = saveProfile(completeProfile(activityLevel = null))

        assertEquals(
            DailyTargetResult.Unavailable(DailyTargetUnavailableReason.MISSING_PROFILE_INPUTS),
            result,
        )
        assertNull(dailyTargetRepository.find(LocalDate.of(2026, 3, 16)))
        assertEquals(0, dailyTargetRepository.upsertCount)
    }

    @Test
    fun `an out of scope age stores no snapshot`() = runTest {
        val result = saveProfile(completeProfile(ageYears = 15))

        assertEquals(
            DailyTargetResult.Unavailable(DailyTargetUnavailableReason.AGE_BELOW_MINIMUM),
            result,
        )
        assertEquals(0, dailyTargetRepository.upsertCount)
    }

    @Test
    fun `repeating the same save is idempotent`() = runTest {
        saveProfile(completeProfile())
        val first = dailyTargetRepository.snapshots.value

        saveProfile(completeProfile())

        assertEquals(first, dailyTargetRepository.snapshots.value)
        assertEquals(2, dailyTargetRepository.upsertCount)
    }
}
