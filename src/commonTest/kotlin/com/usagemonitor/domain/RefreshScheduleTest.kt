package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.RATE_LIMIT_BACKOFF_CAP
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.decodeRateLimitBackoffs
import com.usagemonitor.domain.entity.encodeRateLimitBackoffs
import com.usagemonitor.domain.entity.isReadingFreshEnough
import com.usagemonitor.domain.entity.rateLimitBackoff
import kotlinx.datetime.Instant
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
}
