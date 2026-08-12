package com.usagemonitor.domain

import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Testes unitários para QuotaInfo.
 *
 * Em Kotlin, `@Test` funciona igual ao Jest/Vitest no Node.js.
 * Os testes rodam com `./gradlew test`.
 */
class QuotaInfoTest {

    // Instante fixo para os testes — evita dependência do clock do sistema
    private val fixedInstant = Instant.parse("2025-01-01T00:00:00Z")

    @Test
    fun `percentageUsed returns 0 when total is 0`() {
        // Testa divisão por zero: total 0 deve retornar 0%, não lançar exceção
        val quota = QuotaInfo(
            label = "Tokens",
            used = 100L,
            total = 0L,
            periodEndAt = fixedInstant,
            unit = UsageUnit.TOKENS
        )

        assertEquals(0f, quota.percentageUsed)
    }

    @Test
    fun `percentageUsed calculates correctly at 25 percent`() {
        val quota = QuotaInfo(
            label = "Tokens",
            used = 250L,
            total = 1000L,
            periodEndAt = fixedInstant,
            unit = UsageUnit.TOKENS
        )

        assertEquals(0.25f, quota.percentageUsed)
    }

    @Test
    fun `percentageUsed is capped at 1_0 even when used exceeds total`() {
        // Garante que nunca ultrapassa 100% (evita barra de progresso quebrada)
        val quota = QuotaInfo(
            label = "Tokens",
            used = 1500L,
            total = 1000L,
            periodEndAt = fixedInstant,
            unit = UsageUnit.TOKENS
        )

        assertEquals(1f, quota.percentageUsed)
    }

    @Test
    fun `remaining returns correct value`() {
        val quota = QuotaInfo(
            label = "MiniMax-M*",
            used = 2223L,
            total = 4500L,
            periodEndAt = fixedInstant,
            unit = UsageUnit.REQUESTS
        )

        assertEquals(2277L, quota.remaining)
    }

    @Test
    fun `remaining never goes negative when used exceeds total`() {
        val quota = QuotaInfo(
            label = "Tokens",
            used = 5000L,
            total = 1000L,
            periodEndAt = fixedInstant,
            unit = UsageUnit.TOKENS
        )

        assertTrue(quota.remaining >= 0L)
        assertEquals(0L, quota.remaining)
    }

    @Test
    fun `isExpiredAt turns true from the reset instant on`() {
        val quota = QuotaInfo(
            label = "Claude 5h",
            used = 100L,
            total = 100L,
            periodEndAt = fixedInstant,
            unit = UsageUnit.PERCENTAGE
        )

        assertFalse(quota.isExpiredAt(fixedInstant - 1.seconds))
        assertTrue(quota.isExpiredAt(fixedInstant))
        assertTrue(quota.isExpiredAt(fixedInstant + 1.seconds))
    }

    /**
     * Sem reset conhecido o `periodEndAt` é o sentinela distante do mapper —
     * tratá-lo como janela real faria o card mentir a partir de 2100.
     */
    @Test
    fun `isExpiredAt is always false without a known reset`() {
        val quota = QuotaInfo(
            label = "Claude 5h",
            used = 100L,
            total = 100L,
            periodEndAt = fixedInstant,
            hasKnownResetAt = false,
            unit = UsageUnit.PERCENTAGE
        )

        assertFalse(quota.isExpiredAt(fixedInstant + 365.days))
    }

    @Test
    fun `periodType defaults to INTERVAL when not provided`() {
        // Anthropic não usa dimensão semanal — deve defaultar para INTERVAL
        val quota = QuotaInfo(
            label = "Tokens",
            used = 100L,
            total = 1000L,
            periodEndAt = fixedInstant,
            unit = UsageUnit.TOKENS
        )

        assertEquals(com.usagemonitor.domain.entity.PeriodType.INTERVAL, quota.periodType)
    }
}
