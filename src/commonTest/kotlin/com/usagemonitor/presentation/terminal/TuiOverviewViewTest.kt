package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A aba 1 com os números da tela 3.1 do protótipo: quarta, 21/10, 14:23 BRT, limiares 70 e 90. */
class TuiOverviewViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TuiLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)

    private fun percent(label: String, value: Int, resetsAt: Instant?, type: PeriodType = PeriodType.INTERVAL) =
        UsageSnapshotQuota(label, type, UsageUnit.PERCENTAGE, value.toLong(), 100, value, resetsAt, "USD")

    private val max = TerminalAccount(
        ApiSource.ANTHROPIC, "ANTHROPIC:default", "Anthropic", "pessoal", now - 1.minutes, listOf(70, 90),
        listOf(percent("Claude 5h", 62, now + 2.hours + 17.minutes))
    )
    private val team = TerminalAccount(
        ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", "trabalho", now - 11.minutes, listOf(70, 90),
        listOf(percent("Claude 5h", 91, now + 47.minutes), percent("Claude 7d", 74, Instant.parse("2026-10-23T00:00:00Z"), PeriodType.WEEKLY)),
        plan = "Team"
    )
    private val deepseek = TerminalAccount(
        ApiSource.DEEPSEEK, "DEEPSEEK", "DeepSeek", null, now - 1.minutes, listOf(70, 90),
        listOf(UsageSnapshotQuota("Saldo", PeriodType.REPORTED, UsageUnit.CURRENCY_USD, 0, 782, null, null, "USD"))
    )
    private val session = TuiActiveSession("usage-monitor", "opus-5-5", 72.minutes.inWholeMilliseconds, 9_400_000, false, OutputThroughput(38_000, 1_000_000))

    private fun data(failure: TuiReadFailure? = null) = TuiData(
        now = now,
        collector = CollectorKind.SERVICE,
        lastCollectedAt = now - 1.minutes,
        accounts = listOf(TuiAccount(max), TuiAccount(team, failure, session), TuiAccount(deepseek))
    )

    private fun screen(state: TuiState, size: TuiSize = TuiSize(96, 24), data: TuiData = data()) =
        TuiScreen.render(state, data, size, pt, color).map { line -> line.plainText }

    @Test
    fun `lista de fontes com o maior uso, a selecao e o detalhe da conta escolhida`() {
        val lines = screen(TuiState(overview = TuiListState(selected = 1)))
        assertTrue(lines[3].startsWith("│ FONTES               │ Anthropic · trabalho   "), lines[3])
        assertTrue(lines[3].endsWith("   Team │"), lines[3])
        assertTrue(lines[5].startsWith("│   pessoal        62% │ Claude 5h  ████████████████████████████████████▍░░░   91%  crítico "), lines[5])
        assertTrue(lines[6].startsWith("│ ▸ trabalho       91% │            reinicia 15:10 BRT · em 47 min   "), lines[6])
        assertTrue(lines[7].startsWith("│   DeepSeek     \$7.82 │ "), lines[7])
        assertTrue(lines.any { line -> "Claude 7d  " in line && "74%  atenção" in line })
        assertTrue(lines.any { line -> "SESSÃO ATIVA  usage-monitor · opus-5-5 · 1h12 · \$9.40 · 38 tok/s" in line })
        assertTrue(lines.any { line -> "24 H          sem leitura nas últimas 24 h" in line })
        assertTrue(lines[lines.size - 2].startsWith("│ ↑↓ fonte  enter abre a conta  1–5 aba"))
    }

    @Test
    fun `a selecao pinta o fundo do item e as setas a movem dentro da lista`() {
        val view = TuiOverviewView.render(data(), TuiListState(selected = 0), TuiSize(96, 24), TuiOverviewLabels(AppLanguage.PT), TerminalLabels(AppLanguage.PT), color)
        assertTrue(view.body[2].spans.filter { span -> span.text.isNotBlank() && span.text != "│" }.take(2).all { span -> span.selected })
        val context = TuiContext(teamAvailable = false, stacked = false, accountCount = 3)
        val down = TuiReducer.reduce(TuiState(), TuiKey.Down, context).state
        assertEquals(1, down.overview.selected)
        assertEquals(2, TuiReducer.reduce(TuiState(overview = TuiListState(2)), TuiKey.Down, context).state.overview.selected)
        assertEquals(0, TuiReducer.reduce(TuiState(), TuiKey.Up, context).state.overview.selected)
    }

    @Test
    fun `leitura que falhou mostra a faixa de aviso e o status com a hora da leitura`() {
        val failure = TuiReadFailure("limite de requisições da API", attemptedAt = null, retryAt = now + 4.minutes)
        val lines = screen(TuiState(overview = TuiListState(selected = 1)), data = data(failure))
        assertTrue(lines[0].endsWith("─ leitura de 14:12 ─╮"), lines[0])
        assertTrue(lines.any { line -> "│ ▲ A última leitura falhou: limite de requisições da API." in line })
        assertTrue(lines.any { line -> "│   Mostrando os números de 14:12; tenta de novo às 14:27." in line })
        val status = TuiScreen.status(data(failure), pt)
        assertEquals(TerminalColor.Warn, status.color)
    }

    @Test
    fun `empilhado abaixo de 80 colunas, enter abre a conta e esc volta`() {
        val context = TuiContext(teamAvailable = false, stacked = true, accountCount = 3)
        val list = screen(TuiState(), TuiSize(70, 20))
        assertTrue(list[3].startsWith("│ FONTES "))
        assertTrue(list[5].startsWith("│ ▸ pessoal") && list[5].endsWith("62% │"), list[5])
        val opened = TuiReducer.reduce(TuiState(), TuiKey.Enter, context).state
        val detail = screen(opened, TuiSize(70, 20))
        assertTrue(detail[3].startsWith("│ Anthropic · pessoal"))
        assertTrue(detail[detail.size - 2].contains("esc volta à lista"))
        assertEquals(false, TuiReducer.reduce(opened, TuiKey.Escape, context).state.overview.detailOpen)
    }

    @Test
    fun `duas leituras iguais desenham a mesma tela`() {
        assertEquals(screen(TuiState()), screen(TuiState()))
    }

    @Test
    fun `largura e altura exatas de 80 a 200 colunas`() {
        for (size in listOf(TuiSize(200, 40), TuiSize(96, 24), TuiSize(80, 24))) {
            val lines = TuiScreen.render(TuiState(overview = TuiListState(1)), data(), size, pt, color)
            assertEquals(size.rows, lines.size)
            assertTrue(lines.all { line -> line.width == size.columns }, "$size")
        }
    }

    @Test
    fun `lado a lado, enter abre o historico na primeira cota da conta`() {
        val data = data()
        val context = TuiScreen.context(data, TuiSize(96, 24))
        assertEquals(listOf<Int?>(0, 1, null), context.historyRowOfAccount)
        val step = TuiReducer.reduce(TuiState(overview = TuiListState(selected = 1)), TuiKey.Enter, context)
        assertEquals(TuiTab.HISTORY, step.state.tab)
        assertEquals(1, step.state.history.row)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), step.effects)
        // Conta sem percentual (saldo) não tem trilha: o enter não faz nada.
        assertEquals(TuiTab.OVERVIEW, TuiReducer.reduce(TuiState(overview = TuiListState(selected = 2)), TuiKey.Enter, context).state.tab)
    }
}
