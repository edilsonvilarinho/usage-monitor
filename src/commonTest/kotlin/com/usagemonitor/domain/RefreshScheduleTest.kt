package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.nextDueAt
import com.usagemonitor.domain.entity.looksLikeWakeFromSleep
import com.usagemonitor.domain.entity.isTargetDue
import com.usagemonitor.domain.entity.hasQuotaResetSince
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.RATE_LIMIT_BACKOFF_CAP
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.decodeRateLimitBackoffs
import com.usagemonitor.domain.entity.encodeRateLimitBackoffs
import com.usagemonitor.domain.entity.isReadingFreshEnough
import com.usagemonitor.domain.entity.rateLimitBackoff
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RefreshScheduleTest {

    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @Test
    fun `backoff doubles from 60s and stops at 15 min`() {
        val sequence = (0..6).map { attempt -> rateLimitBackoff(attempt, retryAfter = null) }

        assertEquals(listOf(60.seconds, 120.seconds, 240.seconds, 480.seconds, 900.seconds, 900.seconds, 900.seconds), sequence)
    }

    @Test
    fun `Retry-After only lengthens the wait`() {
        assertEquals(60.seconds, rateLimitBackoff(0, retryAfter = 10.seconds))
        assertEquals(300.seconds, rateLimitBackoff(0, retryAfter = 300.seconds))
        assertEquals(240.seconds, rateLimitBackoff(2, retryAfter = 100.seconds))
    }

    @Test
    fun `Retry-After zero is the 60s floor`() {
        assertEquals(60.seconds, rateLimitBackoff(0, retryAfter = 0.seconds))
    }

    @Test
    fun `the cap holds even against a longer Retry-After`() {
        assertEquals(RATE_LIMIT_BACKOFF_CAP, rateLimitBackoff(0, retryAfter = 1.hours))
    }

    @Test
    fun `a reading older than seven days is not shown`() {
        assertTrue(isReadingFreshEnough(null, now))
        assertTrue(isReadingFreshEnough(now - 7.days, now))
        assertFalse(isReadingFreshEnough(now - 7.days - 1.minutes, now))
    }

    @Test
    fun `backoffs round trip and expired or unreadable lines are dropped`() {
        val anthropic = UsageTargetKey(ApiSource.ANTHROPIC, "perfil=com=igual")
        val codex = UsageTargetKey.forSource(ApiSource.CODEX)
        val encoded = encodeRateLimitBackoffs(mapOf(anthropic to now + 5.minutes, codex to now - 1.minutes))

        val decoded = decodeRateLimitBackoffs("$encoded\nlixo\nMINIMAX=abc", now)

        assertEquals(mapOf(anthropic to now + 5.minutes), decoded)
        assertEquals(emptyMap(), decodeRateLimitBackoffs(null, now))
    }

    private val active = 1.minutes
    private val idle = 5.minutes

    @Test
    fun `isTargetDue follows the four branches`() {
        // Backoff vence tudo, até reset e alvo nunca tentado.
        assertFalse(isTargetDue(now, null, now + 1.minutes, busy = true, resetRolledOver = true, active, idle))
        // Nunca tentado.
        assertTrue(isTargetDue(now, null, null, busy = false, resetRolledOver = false, active, idle))
        // Reset vencido desde a última tentativa.
        assertTrue(isTargetDue(now, now - 10.seconds, null, busy = false, resetRolledOver = true, active, idle))
        // Cadência: 90 s basta com sessão, não sem.
        assertTrue(isTargetDue(now, now - 90.seconds, null, busy = true, resetRolledOver = false, active, idle))
        assertFalse(isTargetDue(now, now - 90.seconds, null, busy = false, resetRolledOver = false, active, idle))
        assertTrue(isTargetDue(now, now - 5.minutes, null, busy = false, resetRolledOver = false, active, idle))
        // Backoff vencido não segura mais.
        assertTrue(isTargetDue(now, now - 5.minutes, now - 1.seconds, busy = false, resetRolledOver = false, active, idle))
    }

    @Test
    fun `nextDueAt is the cadence pushed by the backoff`() {
        assertEquals(now + 4.minutes, nextDueAt(now, now - 1.minutes, null, busy = false, active, idle))
        assertEquals(now, nextDueAt(now, now - 1.minutes, null, busy = true, active, idle))
        assertEquals(now + 10.minutes, nextDueAt(now, now - 1.minutes, now + 10.minutes, busy = false, active, idle))
        assertEquals(now, nextDueAt(now, null, null, busy = false, active, idle))
    }

    @Test
    fun `a reset counts once, after the grace, and never when unknown`() {
        fun statsResetting(at: kotlin.time.Instant, known: Boolean = true) = ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            apiName = "Anthropic",
            quotas = listOf(
                QuotaInfo(
                    label = "5h",
                    used = 1L,
                    total = 100L,
                    periodEndAt = at,
                    hasKnownResetAt = known,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                )
            )
        )
        val grace = 20.seconds
        val lastAttempt = now - 5.minutes

        assertTrue(hasQuotaResetSince(statsResetting(now - 1.minutes), lastAttempt, now, grace))
        // Ainda dentro da folga: o reset da Anthropic não é instantâneo.
        assertFalse(hasQuotaResetSince(statsResetting(now - 10.seconds), lastAttempt, now, grace))
        // Já coletado depois do reset.
        assertFalse(hasQuotaResetSince(statsResetting(now - 1.minutes), now - 30.seconds, now, grace))
        assertFalse(hasQuotaResetSince(statsResetting(now - 1.minutes, known = false), lastAttempt, now, grace))
    }

    @Test
    fun `a wait that ends far past its deadline is a wake from sleep`() {
        assertTrue(looksLikeWakeFromSleep(now - 3.minutes, now, 2.minutes))
        assertFalse(looksLikeWakeFromSleep(now - 1.minutes, now, 2.minutes))
    }
}
