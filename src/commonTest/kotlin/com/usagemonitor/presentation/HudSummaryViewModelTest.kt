package com.usagemonitor.presentation

import com.usagemonitor.FakeCliSessionRepository
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.usecase.GetHudSessionSummaryUseCase
import com.usagemonitor.presentation.viewmodel.HudSummaryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * O laço em si não é exercitado aqui, e é decisão, não esquecimento: ele é
 * `while (true) { ler; delay }` dentro de um escopo próprio, e sob `runTest` o
 * `delay` avança tempo **virtual** — a espera volta na hora e o teste gira para
 * sempre sem nunca ficar ocioso. O que dá para afirmar sem inventar relógio é o
 * que uma passada faz; a cadência de 30s e o portão do `isEnabled` estão no
 * `HudSummaryViewModel` com o motivo escrito ao lado.
 *
 * O caso de uso é o **real**, sobre um repositório de mentira: herdar da classe
 * real e sobrescrever o método deixaria zero linha de produção executando.
 */
class HudSummaryViewModelTest {

    private val now = Instant.parse("2026-09-01T18:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private fun session(id: String, minutesAgo: Long, costMicros: Long) = CliSessionSummary(
        sessionId = id,
        filePath = "/tmp/$id.jsonl",
        firstTs = now - kotlin.time.Duration.parse("${minutesAgo + 5}m"),
        lastTs = now - kotlin.time.Duration.parse("${minutesAgo}m"),
        costMicros = costMicros
    )

    private fun viewModel(repository: FakeCliSessionRepository, enabled: Boolean = true) =
        HudSummaryViewModel(
            getSummary = GetHudSessionSummaryUseCase(repository, clock),
            isEnabled = MutableStateFlow(enabled),
            autoStart = false
        )

    @Test
    fun `antes da primeira leitura nao ha resumo`() = runTest {
        val model = viewModel(FakeCliSessionRepository())

        assertNull(model.summary.value)
    }

    @Test
    fun `uma passada publica o resumo da janela`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(
                session("viva", minutesAgo = 1, costMicros = 1_000_000),
                session("parada", minutesAgo = 120, costMicros = 3_210_000)
            )
        )
        val model = viewModel(repository)

        model.refreshOnce()

        val summary = requireNotNull(model.summary.value)
        assertEquals(1, summary.activeSessionCount)
        assertEquals(2, summary.windowSessionCount)
        assertEquals(4_210_000L, summary.costMicros)
    }

    /**
     * Leitura que falha mantém os números anteriores, mesma regra do resumo por
     * eixo: o índice local falha por arquivo em escrita, e apagar a linha a cada
     * tropeço seria pior que mostrar o valor de trinta segundos atrás.
     */
    @Test
    fun `leitura que falha mantem o resumo anterior`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(session("viva", minutesAgo = 1, costMicros = 1_000_000))
        )
        val model = viewModel(repository)

        model.refreshOnce()
        val first = model.summary.value
        repository.sessionsFailure = IllegalStateException("índice ocupado")
        model.refreshOnce()

        assertEquals(first, model.summary.value)
    }

    /** O laço de fundo não sincroniza: o semáforo de sessão já faz isso a cada 30s. */
    @Test
    fun `uma passada nao sincroniza o indice`() = runTest {
        val repository = FakeCliSessionRepository()
        val model = viewModel(repository)

        model.refreshOnce()

        assertEquals(0, repository.syncCalls)
        assertEquals(1, repository.sessionCalls)
    }
}
