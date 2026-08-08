package app.kcal.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The only source of the current time. Nothing calls `Instant.now()` or `LocalDate.now()`
 * directly, so day boundaries stay deterministic in tests.
 */
class TimeProvider @Inject constructor(private val clock: Clock, private val zoneId: ZoneId) {
    fun now(): Instant = clock.instant()

    fun localDateAt(instant: Instant): LocalDate = instant.atZone(zoneId).toLocalDate()

    fun today(): LocalDate = localDateAt(now())
}
