package com.usagemonitor.domain

import com.usagemonitor.FakeCliSessionRepository
import com.usagemonitor.domain.entity.ACTIVE_SESSION_WINDOW_MILLIS
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.usecase.GetHudSessionSummaryUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O rodapé da barra HUD (issue #164): o que a máquina queimou na janela de 5h e
 * quantas sessões estão vivas agora.
 */
class GetHudSessionSummaryUseCaseTest {

    private val now = Instant.parse("2026-09-01T18:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private fun session(
        id: String,
        minutesAgo: Long,
        costMicros: Long = 0L,
        inputTokens: Long = 0L,
        outputTokens: Long = 0L,
        unpricedTurnCount: Int = 0
    ) = CliSessionSummary(
        sessionId = id,
        filePath = "/tmp/$id.jsonl",
        firstTs = now - kotlin.time.Duration.parse("${minutesAgo + 10}m"),
        lastTs = now - kotlin.time.Duration.parse("${minutesAgo}m"),
        costMicros = costMicros,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        unpricedTurnCount = unpricedTurnCount
    )

    @Test
    fun `soma custo e tokens de todas as sessoes da janela`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(
                session("a", minutesAgo = 1, costMicros = 1_500_000, inputTokens = 100, outputTokens = 20),
                session("b", minutesAgo = 90, costMicros = 2_500_000, inputTokens = 300, outputTokens = 80)
            )
        )

        val summary = GetHudSessionSummaryUseCase(repository, clock)().getOrThrow()

        assertEquals(4_000_000L, summary.costMicros)
        assertEquals(500L, summary.totalTokens)
        assertEquals(2, summary.windowSessionCount)
    }

    /**
     * "Ativa" é o corte de 5 min do semáforo, não um terceiro valor: a mesma
     * pergunta já tem resposta neste app.
     */
    @Test
    fun `conta como ativa so a sessao com turno nos ultimos cinco minutos`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(
                session("viva", minutesAgo = 2),
                session("parada", minutesAgo = 30),
                session("antiga", minutesAgo = 200)
            )
        )

        val summary = GetHudSessionSummaryUseCase(repository, clock)().getOrThrow()

        assertEquals(1, summary.activeSessionCount)
        assertEquals(3, summary.windowSessionCount)
        assertEquals(5L * 60 * 1_000, ACTIVE_SESSION_WINDOW_MILLIS)
    }

    /**
     * Zero ativas com trabalho na janela é o caso comum — ninguém digitando
     * agora, o gasto da tarde ainda contando para a quota.
     */
    @Test
    fun `sem sessao ativa o consumo da janela continua contando`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(session("parada", minutesAgo = 45, costMicros = 900_000))
        )

        val summary = GetHudSessionSummaryUseCase(repository, clock)().getOrThrow()

        assertEquals(0, summary.activeSessionCount)
        assertEquals(1, summary.windowSessionCount)
        assertEquals(900_000L, summary.costMicros)
    }

    @Test
    fun `turno sem tarifa e propagado para a apresentacao marcar o piso`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(
                session("a", minutesAgo = 1, unpricedTurnCount = 2),
                session("b", minutesAgo = 2, unpricedTurnCount = 1)
            )
        )

        val summary = GetHudSessionSummaryUseCase(repository, clock)().getOrThrow()

        assertEquals(3, summary.unpricedTurnCount)
    }

    /**
     * O laço de fundo convive com o do semáforo, que já sincroniza a cada 30s:
     * sincronizar de novo dobraria a varredura sem tornar nada mais fresco.
     */
    @Test
    fun `nao sincroniza o indice`() = runTest {
        val repository = FakeCliSessionRepository()

        GetHudSessionSummaryUseCase(repository, clock)().getOrThrow()

        assertEquals(0, repository.syncCalls)
    }

    /** A janela é a mesma `LAST_5H` da tela de Sessões CLI, não um corte próprio. */
    @Test
    fun `recorta a leitura na janela de cinco horas`() = runTest {
        val repository = FakeCliSessionRepository()

        GetHudSessionSummaryUseCase(repository, clock)(CliQuotaWindows()).getOrThrow()

        val expected = now.toEpochMilliseconds() - 5L * 60 * 60 * 1_000
        assertEquals(expected, repository.lastSinceEpochMillis)
        assertEquals(null, repository.lastProfileId)
    }

    @Test
    fun `falha de leitura propaga em vez de inventar zero`() = runTest {
        val repository = FakeCliSessionRepository()
        repository.sessionsFailure = IllegalStateException("índice ocupado")

        val result = GetHudSessionSummaryUseCase(repository, clock)()

        assertTrue(result.isFailure)
    }
}
