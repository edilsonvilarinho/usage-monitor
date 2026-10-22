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

    private fun spark(values: List<Int?>, resets: Set<Int> = emptySet()) = TuiSpark(values, resets, now - 24.hours, 30.minutes.inWholeMilliseconds)

    private val history = TuiHistory(
        HistoryRange.LAST_24_HOURS,
        listOf(
            TuiHistoryRow(team, team.quotas.single(), spark(List(48) { index -> if (index < 20) 10 else 91 }, resets = setOf(20))),
            TuiHistoryRow(go, go.quotas.single(), null)
        )
    )

    private fun screen(state: TuiHistoryState = TuiHistoryState(), size: TuiSize = TuiSize(96, 20)) =
        TuiScreen.render(TuiState(tab = TuiTab.HISTORY, history = state), TuiData(now, CollectorKind.APP, now, history = history), size, pt, color).map { line -> line.plainText }

    @Test
    fun `uma trilha por cota com o reinicio marcado, o uso de agora e o estado`() {
        val lines = screen()
        assertTrue(lines[3].startsWith("│ janela  24 h    7 dias    30 dias ") && lines[3].endsWith(" uma célula = meia hora │"), lines[3])
        assertTrue(lines[5].startsWith("│ ▸ trabalho Claude 5h  " + "▁".repeat(20) + "┆" + "▇".repeat(27) + "   91% crítico"), lines[5])
        assertTrue(lines[6].startsWith("│   Go 5h               sem leitura nesta janela"), lines[6])
        assertTrue(lines[6].contains("24% em dia"))
        assertTrue(lines.any { line -> "┆ reinício da janela · altura = uso da janela · cor = fonte" in line })
    }

    @Test
    fun `o eixo e em BRT, a cada 3 horas cheias`() {
        // A primeira célula começa às 14:23 de ontem; a terceira, às 15:23, é a das 15h.
        val axis = screen()[7]
        assertTrue(axis.startsWith("│ " + " ".repeat(2 + 20) + "  15h   18h   21h   00h"), axis)
    }

    @Test
    fun `setas trocam a janela e pedem releitura, e a trilha escolhida anda`() {
        val context = TuiContext(false, false, historyRowCount = 2)
        val right = TuiReducer.reduce(TuiState(tab = TuiTab.HISTORY), TuiKey.Right, context)
        assertEquals(HistoryRange.LAST_7_DAYS, right.state.history.range)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), right.effects)
        assertEquals(1, TuiReducer.reduce(TuiState(tab = TuiTab.HISTORY), TuiKey.Down, context).state.history.selected)
        assertEquals(emptyList(), TuiReducer.reduce(TuiState(tab = TuiTab.HISTORY), TuiKey.Left, context).effects)
        assertEquals(56, TuiHistoryState.cellCount(HistoryRange.LAST_7_DAYS))
    }

    @Test
    fun `em tela estreita a trilha perde o comeco e nunca o agora`() {
        for (size in listOf(TuiSize(80, 20), TuiSize(96, 20), TuiSize(60, 14))) {
            val lines = screen(size = size)
            assertEquals(size.rows, lines.size)
            assertTrue(lines.all { line -> line.length == size.columns }, "$size")
        }
        assertTrue(screen(size = TuiSize(80, 20))[5].contains("┆" + "▇".repeat(27) + "   91%"))
    }
}
