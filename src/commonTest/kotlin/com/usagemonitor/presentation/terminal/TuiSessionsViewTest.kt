package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.SessionModelMix
import com.usagemonitor.domain.entity.sessionModelMixOf
import com.usagemonitor.domain.entity.toUsageBreakdown
import kotlin.math.roundToInt
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

    private fun session(
        id: String,
        project: String,
        model: String,
        cost: Long?,
        incomplete: Boolean = false,
        source: ApiSource = ApiSource.ANTHROPIC,
        modelMix: SessionModelMix = SessionModelMix()
    ) = TuiSession(
        id = id, source = source, project = project, model = model, startedAt = now - 81.minutes, lastAt = now - 1.minutes,
        durationMillis = 72.minutes.inWholeMilliseconds, turnCount = 87, inputTokens = 300_000, outputTokens = 150_000,
        cacheReadTokens = 4_000_000, cacheWriteTokens = 350_000, costMicros = cost, costIncomplete = incomplete,
        throughput = OutputThroughput(38_000, 1_000_000), contextTokens = 136_000, contextWindow = 200_000,
        modelMix = modelMix
    )

    /** Opus na thread principal e Haiku nos subagentes, como na sessão da #445. */
    private val switchedMix = mix(
        CliUsageGroupRow(model = "claude-opus-5", turnCount = 80, outputTokens = 140_000, cacheReadTokens = 3_600_000, cacheWrite5mTokens = 300_000),
        CliUsageGroupRow(model = "claude-haiku-4-5", turnCount = 7, outputTokens = 10_000, cacheReadTokens = 400_000, cacheWrite5mTokens = 50_000)
    )

    private fun mix(vararg rows: CliUsageGroupRow) = sessionModelMixOf(rows.map { row -> row.copy(sessionId = "a") }.toUsageBreakdown().byModel)

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
    fun `sessao que trocou de modelo mostra o principal com +N e um modelo por linha no detalhe`() {
        val data = sessions.copy(sessions = listOf(session("a", "usage-monitor", "claude-opus-5", 9_400_000, modelMix = switchedMix)))
        val lines = screen(data = data, size = TuiSize(110, 30))
        val opus = (switchedMix.shares[0].share!! * 100).roundToInt()
        val haiku = (switchedMix.shares[1].share!! * 100).roundToInt()
        assertTrue(lines[6].contains("claude-opus-5 +1"), lines[6])
        val text = lines.joinToString("\n")
        assertTrue(lines.any { line -> line.startsWith("│ modelos     claude-opus-5     ") && line.contains("$opus%") && line.contains("lido 3,6M · gravado 300K") }, text)
        assertTrue(lines.any { line -> line.startsWith("│ % do custo  claude-haiku-4-5  ") && line.contains("$haiku%") && line.contains("lido 400K · gravado 50K") }, text)
    }

    @Test
    fun `modelo sem tarifa diz sem tarifa e mais de tres modelos viram +N modelos`() {
        val many = mix(
            CliUsageGroupRow(model = "claude-opus-5", turnCount = 4, outputTokens = 1_000),
            CliUsageGroupRow(model = "claude-haiku-4-5", turnCount = 3, outputTokens = 1_000),
            CliUsageGroupRow(model = "claude-sonnet-5", turnCount = 2, outputTokens = 1_000),
            CliUsageGroupRow(model = "minimax-m3", turnCount = 1, outputTokens = 1_000)
        )
        val data = sessions.copy(sessions = listOf(session("a", "usage-monitor", "claude-opus-5", 9_400_000, modelMix = many)))
        val text = screen(data = data, size = TuiSize(110, 30)).joinToString("\n")
        assertTrue("+2 modelos" in text, text)
        val unpriced = mix(CliUsageGroupRow(model = "claude-opus-5", turnCount = 1, outputTokens = 1_000), CliUsageGroupRow(model = "minimax-m3", turnCount = 1, outputTokens = 1_000))
        val other = screen(data = sessions.copy(sessions = listOf(session("a", "x", "claude-opus-5", 1, modelMix = unpriced))), size = TuiSize(110, 30)).joinToString("\n")
        assertTrue(other.lines().any { line -> line.contains("minimax-m3") && line.contains("sem tarifa") && line.contains("lido") }, other)
        // A nota de rodapé nomeia o modelo sem tarifa, não o que mais pesou na sessão.
        val incomplete = sessions.copy(sessions = listOf(session("a", "x", "claude-opus-5", 1, incomplete = true, modelMix = unpriced)))
        val footnote = screen(data = incomplete, size = TuiSize(110, 30)).single { line -> "sem tarifa na tabela" in line }
        assertTrue(footnote.contains("+ minimax-m3 sem tarifa") && !footnote.contains("claude-opus-5"), footnote)
    }

    @Test
    fun `sem cor e sem Unicode a linha de modelos usa o separador da tabela de glifos`() {
        val data = sessions.copy(sessions = listOf(session("a", "usage-monitor", "claude-opus-5", 9_400_000, modelMix = switchedMix)))
        val text = screen(data = data, size = TuiSize(110, 30), profile = ascii).joinToString("\n")
        assertTrue("lido 3,6M - gravado 300K" in text, text)
        // As linhas de modelo não trazem glifo fora da tabela ASCII: barra entre colchetes, separador `-`.
        val modelLines = text.lines().filter { line -> " - gravado " in line }
        assertEquals(2, modelLines.size, text)
        assertTrue(modelLines.all { line -> line.all { char -> char.code < 128 } }, text)
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
    fun `largura e altura exatas de 80 a 200 colunas`() {
        val switched = sessions.copy(sessions = sessions.sessions.map { session -> session.copy(modelMix = switchedMix) })
        for (size in listOf(TuiSize(200, 40), TuiSize(96, 24), TuiSize(80, 24), TuiSize(70, 16))) {
            val lines = screen(size = size)
            assertEquals(size.rows, lines.size)
            assertTrue(lines.all { line -> line.length == size.columns }, "$size")
        }
        // Com as linhas de modelo (#445) o quadro continua exato.
        for (size in listOf(TuiSize(200, 40), TuiSize(96, 24), TuiSize(80, 24), TuiSize(70, 16))) {
            val lines = screen(data = switched, size = size)
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
