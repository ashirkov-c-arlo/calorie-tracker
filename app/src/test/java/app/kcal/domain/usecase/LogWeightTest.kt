package app.kcal.domain.usecase

import app.kcal.domain.model.WeightEntry
import app.kcal.testing.FakeProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The persisted-weight invariant lives here, not in the screen that happens to call it. */
class LogWeightTest {

    private val date = LocalDate.of(2026, 3, 15)
    private val repository = FakeProfileRepository()
    private val logWeight = LogWeight(repository)

    @Test
    fun `a finite positive weight is stored`() = runTest {
        assertTrue(logWeight(WeightEntry(date, 81.0)))

        assertEquals(listOf(WeightEntry(date, 81.0)), repository.loggedWeights)
    }

    @Test
    fun `non-finite and non-positive weights are rejected without a write`() = runTest {
        val rejected = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0)

        rejected.forEach { kg ->
            assertFalse(logWeight(WeightEntry(date, kg)), "expected $kg to be rejected")
        }

        assertTrue(repository.loggedWeights.isEmpty())
        assertTrue(repository.weightsByDate.value.isEmpty())
    }
}
