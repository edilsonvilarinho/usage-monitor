package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.OutputThroughput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TuiSessionsViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TuiLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val ascii = TerminalProfile(TerminalPalette.NONE, unicode = false)

    private fun session(id: String, project: String, model: String, cost: Long?, incomplete: Boolean = false, source: ApiSource = ApiSource.ANTHROPIC) = TuiSession(
        id = id, source = source, project = project, model = model, startedAt = now - 81.minutes, lastAt = now - 1.minutes,
        durationMillis = 72.minutes.inWholeMilliseconds, turnCount = 87, inputTokens = 300_000, outputTokens = 150_000,
        cacheReadTokens = 4_000_000, cacheWriteTokens = 350_000, costMicros = cost, costIncomplete = incomplete,
        throughput = OutputThroughput(38_000, 1_000_000), contextTokens = 136_000, contextWindow = 200_000
    )

    private val sessions = TuiSessions(
        TuiSessionPeriod.TODAY,
        listOf(
            session("a", "usage-monitor", "claude-opus-5-5", 9_400_000),
            session("b", "notas", "minimax-m3", 310_000, incomplete = true),
            session("c", "server", "gpt-5.3-codex", null, source = ApiSource.CODEX)
        )
    )

    private fun screen(state: TuiSessionsState = TuiSessionsState(), data: TuiSessions = sessions, size: TuiSize = TuiSize(96, 24), profile: TerminalProfile = color) =
        TuiScreen.render(TuiState(tab = TuiTab.SESSIONS, sessions = state), TuiData(now, CollectorKind.APP, now, sessions = data), size, pt, profile).map { line -> line.plainText }

    @Test
    fun `corte, total, tabela com custo e vazao e a nota do modelo sem tarifa`() {
        val lines = screen()
        assertTrue(lines[3].startsWith("│  hoje    7 dias    30 dias "), lines[3])
        assertTrue(lines[3].endsWith("3 sessões · \$9.71+ │"), lines[3])
        assertTrue(lines[5].startsWith("│   PROJETO "))
        assertTrue(lines[6].startsWith("│ ▸ usage-monitor "))
        assertTrue(lines[6].contains("claude-opus-5-5") && lines[6].contains("1h12") && lines[6].contains("4,8M") && lines[6].trimEnd('│', ' ').endsWith("\$9.40   38 tok/s"), lines[6])
        assertTrue(lines[7].contains("\$0.31+"))
        assertTrue(lines[8].contains("—"), lines[8])
        assertTrue(lines[9].startsWith("│   + minimax-m3 sem tarifa na tabela; o custo mostrado é parcial"))
    }

    @Test
    fun `detalhe da selecionada com titulo, composicao e contexto`() {
        val text = screen().joinToString("\n")
        assertTrue("── usage-monitor · claude-opus-5-5 · iniciada 13:02 BRT · 87 turnos ──" in text)
        assertTrue("■ entrada 6%  ■ saída 3%" in text)
        assertTrue("■ cache lido 83%  ■ cache gravado 7%" in text)
        assertTrue(text.lines().any { line -> line.startsWith("│ contexto    ███████████████████████████▏░") && line.contains("░  136K de 200K") })
    }

    @Test
    fun `sem cor e sem Unicode a composicao usa a inicial de cada serie`() {
        val text = screen(profile = ascii).joinToString("\n")
        assertTrue("composição  eesllll" in text, text)
        assertTrue("lggg  e entrada 6%  s saída 3%" in text)
        assertTrue("l cache lido 83%  g cache gravado 7%" in text)
    }

    @Test
    fun `setas trocam o corte e pedem releitura, enter abre os turnos`() {
        val step = TuiReducer.reduce(TuiState(tab = TuiTab.SESSIONS, sessions = TuiSessionsState(selected = 2)), TuiKey.Right, TuiContext(false, false, sessionCount = 3))
        assertEquals(TuiSessionPeriod.LAST_7_DAYS, step.state.sessions.period)
        assertEquals(0, step.state.sessions.selected)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), step.effects)
        val left = TuiReducer.reduce(TuiState(tab = TuiTab.SESSIONS), TuiKey.Left, TuiContext(false, false, sessionCount = 3))
        assertEquals(emptyList(), left.effects)
        val open = TuiReducer.reduce(TuiState(tab = TuiTab.SESSIONS), TuiKey.Enter, TuiContext(false, false, sessionCount = 3))
        assertTrue(open.state.sessions.turnsOpen)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), open.effects)
        assertEquals(listOf<TuiEffect>(TuiEffect.ExportSessions), TuiReducer.reduce(TuiState(tab = TuiTab.SESSIONS), TuiKey.Text('e'), TuiContext(false, false)).effects)
    }

    @Test
    fun `turnos da sessao aberta, do mais recente ao mais antigo`() {
        val turns = listOf(TuiTurn(now - 2.minutes, "claude-opus-5-5", 1_500, 541, 400_500, 220_000), TuiTurn(now - 3.minutes, "claude-opus-5-5", 6_500, 1_500, 394_000, null))
        val lines = screen(TuiSessionsState(turnsOpen = true), sessions.copy(turns = turns))
        assertTrue(lines[5].contains("── usage-monitor · claude-opus-5-5"))
        assertTrue(lines[7].startsWith("│ HORA   MODELO"))
        assertTrue(lines[8].startsWith("│ 14:21  claude-opus-5-5") && lines[8].contains("400K") && lines[8].contains("\$0.22"), lines[8])
        assertTrue(lines[9].contains("—"))
        assertTrue(lines[lines.size - 2].contains("esc volta às sessões"))
    }

    @Test
    fun `sem sessoes no corte a aba diz isso`() {
        assertTrue(screen(data = TuiSessions(TuiSessionPeriod.TODAY, emptyList())).any { line -> "Nenhuma sessão CLI neste período." in line })
    }

    @Test
    fun `largura e altura exatas em 80 e 96 colunas`() {
        for (size in listOf(TuiSize(96, 24), TuiSize(80, 24), TuiSize(70, 16))) {
            val lines = screen(size = size)
            assertEquals(size.rows, lines.size)
            assertTrue(lines.all { line -> line.length == size.columns }, "$size")
        }
    }

    @Test
    fun `tokens com uma casa no separador do idioma, nunca do locale da JVM`() {
        assertEquals("4,8M", TuiText.tokens(4_800_000, pt = true))
        assertEquals("4.8M", TuiText.tokens(4_800_000, pt = false))
        assertEquals("136K", TuiText.tokens(136_000, pt = true))
        assertEquals("1,5K", TuiText.tokens(1_500, pt = true))
        assertEquals("164M", TuiText.tokens(164_100_000, pt = true))
        assertEquals("541", TuiText.tokens(541, pt = true))
    }
}
