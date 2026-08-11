package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.computeContextStatus
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val OPUS_5 = "claude-opus-5"

class CliSessionContextStatusTest {

    @Test
    fun `a small context on a large window is healthy`() {
        val status = computeContextStatus(
            liveContextTokens = 20_000L,
            windowModel = OPUS_5,
            lastTurnModel = OPUS_5
        )

        assertEquals(0.02, status.contextSaturation)
        assertEquals(CliSessionHealth.HEALTHY, status.health)
    }

    @Test
    fun `crossing 40 percent of the window raises attention`() {
        val status = computeContextStatus(
            liveContextTokens = 400_000L,
            windowModel = OPUS_5,
            lastTurnModel = OPUS_5
        )

        assertEquals(CliSessionHealth.ATTENTION, status.health)
    }

    @Test
    fun `crossing 60 percent of the window saturates`() {
        val status = computeContextStatus(
            liveContextTokens = 600_000L,
            windowModel = OPUS_5,
            lastTurnModel = OPUS_5
        )

        assertEquals(CliSessionHealth.SATURATED, status.health)
    }

    /**
     * Numa janela de 200K a fração pode ser modesta e a mensagem ainda sair cara:
     * as duas dimensões são avaliadas em paralelo.
     */
    @Test
    fun `an expensive next message saturates even on a modest fraction`() {
        val status = computeContextStatus(
            liveContextTokens = 60_000L,
            windowModel = "claude-haiku-4-5",
            lastTurnModel = OPUS_5
        )

        assertEquals(0.3, status.contextSaturation)
        // 60K a $0,50/M de cache read do Opus = $0,030.
        assertEquals(30_000L, status.nextInteractionCostMicros)
        assertEquals(CliSessionHealth.HEALTHY, status.health)
    }

    @Test
    fun `an unknown window leaves the saturation unavailable instead of guessing`() {
        val status = computeContextStatus(
            liveContextTokens = 900_000L,
            windowModel = "claude-3-5-sonnet",
            lastTurnModel = "claude-3-5-sonnet"
        )

        assertNull(status.contextSaturation)
        // Modelo sem preço: o custo não é inventado, fica em zero.
        assertEquals(0L, status.nextInteractionCostMicros)
        assertEquals(CliSessionHealth.HEALTHY, status.health)
    }

    /** A tarifa da próxima mensagem é a do último turno, não a do modelo predominante. */
    @Test
    fun `the next message is priced by the model of the last turn`() {
        val status = computeContextStatus(
            liveContextTokens = 100_000L,
            windowModel = OPUS_5,
            lastTurnModel = "claude-haiku-4-5"
        )

        // 100K a $0,10/M de cache read do Haiku.
        assertEquals(10_000L, status.nextInteractionCostMicros)
    }

    /** Um veredito que discorda de si mesmo entre lista e detalhe é pior que nenhum. */
    @Test
    fun `the list and the detail reach the same verdict from the same inputs`() {
        val summary = CliSessionSummary(
            sessionId = "a",
            filePath = "/tmp/a.jsonl",
            firstTs = Instant.fromEpochMilliseconds(0L),
            lastTs = Instant.fromEpochMilliseconds(2_000L),
            primaryModel = OPUS_5,
            liveContextTokens = 650_000L,
            liveContextModel = OPUS_5
        )
        val detail = CliSessionDetail(
            summary = summary,
            turns = listOf(
                turn(seq = 1, cacheReadTokens = 120_000L),
                // Subagente depois do último turno principal: não muda o contexto vivo.
                turn(seq = 2, cacheReadTokens = 650_000L),
                turn(seq = 3, cacheReadTokens = 5_000L, isSidechain = true)
            )
        )

        val analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)

        assertEquals(analytics.contextStatus, summary.contextStatus)
        assertEquals(CliSessionHealth.SATURATED, summary.contextStatus.health)
    }

    private fun turn(
        seq: Int,
        cacheReadTokens: Long,
        isSidechain: Boolean = false
    ): CliSessionTurn {
        return CliSessionTurn(
            sessionId = "a",
            seq = seq,
            messageId = "msg-$seq",
            ts = Instant.fromEpochMilliseconds(seq.toLong() * 1_000L),
            model = OPUS_5,
            isSidechain = isSidechain,
            cacheReadTokens = cacheReadTokens
        )
    }
}
