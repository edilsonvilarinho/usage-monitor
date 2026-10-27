package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TuiHistoryViewTest {

    // Quarta, 21/10/2026, 14:23 BRT.
    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TuiLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)

    private fun percent(label: String, value: Int) = UsageSnapshotQuota(label, PeriodType.INTERVAL, UsageUnit.PERCENTAGE, value.toLong(), 100, value, now + 1.hours, "USD")

    private val team = TerminalAccount(ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", "trabalho", now, listOf(70, 90), listOf(percent("Claude 5h", 91)))
    private val go = TerminalAccount(ApiSource.OPENCODE_GO, "OPENCODE_GO", "OpenCode Go", null, now, listOf(70, 90), listOf(percent("Go 5h", 24)))

    // Janela aberta das 10:23 às 15:23; antes, uma que sobrou 4% (04:23–09:23) e uma que esgotou (22:23–03:23).
    private val teamRow = TuiHistoryFixtures.row(team, team.quotas.single(), closedPeaks = listOf(100, 96), length = 5.hours, now = now)
    private val history = TuiHistory(HistoryRange.LAST_24_HOURS, listOf(teamRow, TuiHistoryRow(go, go.quotas.single())))

    private fun data() = TuiData(now, CollectorKind.APP, now, history = history)

    private fun screen(state: TuiHistoryState = TuiHistoryState(), size: TuiSize = TuiSize(96, 20)) =
        TuiScreen.render(TuiState(tab = TuiTab.HISTORY, history = state), data(), size, pt, color).map { line -> line.plainText }

    private fun press(state: TuiHistoryState, key: TuiKey): TuiStep =
        TuiReducer.reduce(TuiState(tab = TuiTab.HISTORY, history = state), key, TuiScreen.context(data(), TuiSize(96, 20)))

    @Test
    fun `uma linha por janela, a mais nova primeiro, com inicio, faixa ativa, pico e como terminou`() {
        val lines = screen()
        assertTrue(lines[3].startsWith("│ período  24 h    7 dias    30 dias ") && lines[3].endsWith(" uma linha = uma janela │"), lines[3])
        assertTrue(lines[5].startsWith("│   INÍCIO       ATIVA         PICO              COMO TERMINOU"), lines[5])
        assertTrue(lines[6].startsWith("│ ● trabalho Claude 5h  91% crítico · 3 janelas no período · 1 esgotou"), lines[6])
        assertTrue(lines[7].startsWith("│ ▸ 21/10 10:38  10:38–14:23   █████████░  91%   aberta · reinicia "), lines[7])
        assertTrue(lines[8].startsWith("│   21/10 04:38  04:38–07:53   █████████▌  96%   sobrou 4%"), lines[8])
        assertTrue(lines[9].startsWith("│   20/10 22:38  20/10–21/10   ██████████ 100%   ▲ esgotou após 3 h 15 min · parado 1 h 30 min"), lines[9])
        assertTrue(lines[11].startsWith("│ ● Go 5h  24% em dia"), lines[11])
        assertTrue(lines[12].startsWith("│   sem janela no período"), lines[12])
    }

    @Test
    fun `setas andam pelas janelas de todas as cotas e trocam o periodo, enter abre o grafico`() {
        assertEquals(TuiHistoryState(row = 0, window = 1), press(TuiHistoryState(), TuiKey.Down).state.history)
        // A Go não tem janela: a última janela da lista é o fim.
        assertEquals(TuiHistoryState(row = 0, window = 2), press(TuiHistoryState(window = 2), TuiKey.Down).state.history)
        val right = press(TuiHistoryState(window = 2), TuiKey.Right)
        assertEquals(TuiHistoryState(range = HistoryRange.LAST_7_DAYS), right.state.history)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), right.effects)
        val chart = press(TuiHistoryState(window = 1), TuiKey.Enter).state.history
        assertEquals(TuiHistoryState(window = 1, chartOpen = true), chart)
        // Com o gráfico aberto, o período não muda; `esc` volta à tabela.
        assertEquals(emptyList(), press(chart, TuiKey.Right).effects)
        assertEquals(chart, press(chart, TuiKey.Right).state.history)
        assertEquals(TuiHistoryState(window = 1), press(chart, TuiKey.Escape).state.history)
    }

    @Test
    fun `escolha numa cota sem janela cai na janela mais proxima`() {
        val counts = listOf(3, 0, 2)
        assertEquals(2 to 0, TuiHistoryState(row = 1).resolve(counts))
        assertEquals(0 to 2, TuiHistoryState(row = 1).resolve(listOf(3, 0)))
        assertNull(TuiHistoryState().resolve(listOf(0, 0)))
    }

    @Test
    fun `o grafico alto tem eixo escrito, o reinicio marcado e a frase da janela aberta`() {
        val lines = screen(TuiHistoryState(chartOpen = true), TuiSize(96, 24))
        assertTrue(lines[3].startsWith("│ trabalho Claude 5h · janela de 21/10 10:23 a 15:23 · uma coluna = 5 min"), lines[3])
        assertTrue(lines[5].startsWith("│ 100% ┤"), lines[5])
        assertTrue(lines[9].startsWith("│  50% ┤"), lines[9])
        assertTrue(lines[12].startsWith("│   0% ┤"), lines[12])
        // 60 colunas de 5 min desde 10:23: as três antes da 1ª leitura (10:38) e as onze depois de agora ficam vazias.
        assertTrue(lines[12].startsWith("│   0% ┤   ▃▃▃▇▇▇" + "█".repeat(40) + " ".repeat(11)), lines[12])
        assertTrue(lines[13].startsWith("│      ╰" + "─".repeat(60) + "┆"), lines[13])
        assertTrue(lines[14].startsWith("│       10:23") && lines[14].contains("12:53") && lines[14].trimEnd('│', ' ').endsWith("15:23"), lines[14])
        assertTrue(lines[16].startsWith("│ 91% usada com 80% da janela já passada · ▲ 11 pp à frente do tempo"), lines[16])
        assertTrue(lines[17].startsWith("│ ativa 10:38–14:23 · ritmo médio "), lines[17])
        assertTrue(lines[22].startsWith("│ esc lista  ↑↓ janela"), lines[22])
    }

    @Test
    fun `o grafico de uma janela que esgotou diz quando e quanto ficou parada`() {
        val lines = screen(TuiHistoryState(window = 2, chartOpen = true), TuiSize(96, 24))
        assertTrue(lines[3].contains("janela de 20/10 22:23 a 21/10 03:23"), lines[3])
        assertTrue(lines[16].startsWith("│ pico 100% · ▲ esgotou após 3 h 15 min · parado 1 h 30 min"), lines[16])
    }

    @Test
    fun `coluna sem leitura fica vazia, a fechada carrega a ultima leitura ate o reinicio`() {
        val closed = teamRow.listedWindows[1]
        val start = closed.resetsAt - 5.hours
        val values = TuiHistoryChartView.columnValues(teamRow.points, closed, start, 30.minutes.inWholeMilliseconds, 10, now)
        // A primeira leitura é às 04:38: a coluna das 04:23 já a tem; a última leitura, 96%, vai até o fim.
        assertEquals(96, values.last())
        assertTrue(values.all { value -> value != null })
        val open = TuiHistoryChartView.columnValues(teamRow.points, teamRow.listedWindows[0], now - 4.hours, 30.minutes.inWholeMilliseconds, 10, now)
        // A coluna que começa agora ainda tem a leitura de agora; a seguinte, não.
        assertEquals(listOf(91, null), open.takeLast(2))
    }

    @Test
    fun `largura e altura exatas, na tabela e no grafico`() {
        for (size in listOf(TuiSize(200, 40), TuiSize(80, 20), TuiSize(96, 20), TuiSize(60, 14), TuiSize(40, 12))) {
            for (state in listOf(TuiHistoryState(), TuiHistoryState(window = 2, chartOpen = true))) {
                val lines = screen(state, size)
                assertEquals(size.rows, lines.size)
                assertTrue(lines.all { line -> line.length == size.columns }, "$size $state")
            }
        }
    }

    @Test
    fun `em 80 colunas o tempo parado sai inteiro, sem cortar a frase`() {
        val line = screen(size = TuiSize(80, 20))[9]
        assertTrue(line.contains("▲ esgotou após 3 h 15 min ") && "parado" !in line, line)
    }

    @Test
    fun `em tela baixa a tabela rola ate a janela escolhida`() {
        val lines = screen(TuiHistoryState(window = 2), TuiSize(96, 13))
        assertTrue(lines.any { line -> line.startsWith("│ ▸ 20/10 22:38") }, lines.joinToString("\n"))
    }
}
