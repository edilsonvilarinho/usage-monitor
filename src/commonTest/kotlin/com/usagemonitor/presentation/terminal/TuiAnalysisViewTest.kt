package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AnalysisBucket
import com.usagemonitor.domain.entity.AnalysisModelSpeed
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TuiAnalysisViewTest {

    // Quarta, 21/10/2026, 14:23 BRT.
    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TuiLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)

    private fun quota(label: String, value: Int) = UsageSnapshotQuota(label, PeriodType.INTERVAL, UsageUnit.PERCENTAGE, value.toLong(), 100, value, now + 9.minutes, "USD")
    private val work = TerminalAccount(ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", "trabalho", now, listOf(70, 90), listOf(quota("Claude 5h", 64)))
    private val codex = TerminalAccount(ApiSource.CODEX, "CODEX", "Codex", null, now, listOf(70, 90), listOf(quota("Codex 5h", 24)))

    // 5h em 64% com 9 min para o reinício; semanal em 94% com 3 d 5 h para o reinício.
    private val fiveHour = AnalysisWindowView("Claude 5h", PeriodType.INTERVAL, 64, now + 9.minutes - 5.hours, now + 9.minutes, 20.0, 13.2, 240.0, 67)
    private val weekly = AnalysisWindowView("Claude 7d", PeriodType.WEEKLY, 94, now + 77.hours - (7 * 24).hours, now + 77.hours, 3.0, 1.0, 6.0 / 77, 100)

    private val hourStart = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() / 3_600_000 * 3_600_000) - 23.hours
    private val hours = (0 until 24).map { index ->
        AnalysisBucket(hourStart + index.hours, if (index < 4) null else (index % 6) * 6.0, if (index < 18) null else 80.0 + index * 4)
    }

    private val analysis = SessionAnalysis(
        "Anthropic · trabalho", ApiSource.ANTHROPIC, now, fiveHour, weekly, emptyList(), hours, emptyList(), emptyList(), emptyList(), emptyList(), null,
        listOf(AnalysisModelSpeed("claude-opus-5-5", 103.0))
    )

    private fun data(accounts: List<TerminalAccount> = listOf(work, codex), result: SessionAnalysis? = analysis) =
        TuiData(now, CollectorKind.APP, now, accounts = accounts.map(::TuiAccount), analysis = TuiAnalysis(accounts, 0, result))

    private fun screen(state: TuiAnalysisState = TuiAnalysisState(), size: TuiSize = TuiSize(110, 44), data: TuiData = data()) =
        TuiScreen.render(TuiState(tab = TuiTab.ANALYSIS, analysis = state), data, size, pt, color).map { line -> line.plainText }

    @Test
    fun `a aba 6 tem as duas janelas lado a lado, a corrida de cada uma e as 24 horas`() {
        val lines = screen()
        assertTrue(lines[1].contains("5 Configurações   6 Análise"), lines[1])
        assertTrue(lines[3].startsWith("│ ● Anthropic · trabalho") && lines[3].contains("conta 1 de 2"), lines[3])
        assertTrue(lines.any { line -> "JANELA 5H" in line && "JANELA SEMANAL" in line })
        assertTrue(lines.any { line -> "✓ cabe até o reinício" in line && "▲ esgota antes do reinício" in line })
        assertTrue(lines.any { line -> "para caber            o ritmo de agora basta" in line && "no máximo 0,1 %/h" in line })
        val race = lines.indexOfFirst { line -> "5h · 64% usada · ✓ cabe até o reinício" in line }
        assertTrue(lines[race].contains("régua: agora até +2 h 43 min"), lines[race])
        assertTrue(lines[race + 1].startsWith("│   reinicia ") && lines[race + 1].contains("9 min · 14:32"), lines[race + 1])
        assertTrue(lines[race + 2].startsWith("│   esgota ") && lines[race + 2].contains("1 h 48 min · ~16:11 no ritmo da última hora"), lines[race + 2])
        assertTrue(lines[race + 3].contains("2 h 43 min · ~17:06 no ritmo médio"), lines[race + 3])
        val weeklyRace = lines.indexOfFirst { line -> "semanal · 94% usada · ▲ esgota antes do reinício" in line }
        assertTrue(lines[weeklyRace].contains("régua: agora até +3 d 5 h"), lines[weeklyRace])
        assertTrue(lines[weeklyRace + 2].contains("2 h · ~16:23 no ritmo da última hora"), lines[weeklyRace + 2])
        assertTrue(lines.any { line -> line.startsWith("│ ÚLTIMAS 24 H · % da cota 5h gasta em cada hora · pico ") })
        assertTrue(lines.any { line -> line.startsWith("│ tok/s ") && "máx 172" in line })
        assertTrue(lines.any { line -> "tok/s na última hora: 103 (claude-opus-5-5)" in line })
    }

    @Test
    fun `setas trocam a conta e relem, e a rolagem para no fim do corpo`() {
        val size = TuiSize(96, 24)
        val context = TuiScreen.context(data(), size)
        assertEquals(2, context.analysisAccountCount)
        assertTrue(context.analysisScrollMax > 0)
        val right = TuiReducer.reduce(TuiState(tab = TuiTab.ANALYSIS), TuiKey.Right, context)
        assertEquals(TuiAnalysisState(account = 1), right.state.analysis)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), right.effects)
        assertEquals(emptyList(), TuiReducer.reduce(TuiState(tab = TuiTab.ANALYSIS), TuiKey.Left, context).effects)
        val bottom = TuiReducer.reduce(TuiState(tab = TuiTab.ANALYSIS, analysis = TuiAnalysisState(scroll = context.analysisScrollMax)), TuiKey.Down, context)
        assertEquals(context.analysisScrollMax, bottom.state.analysis.scroll)
        // A tecla 6 abre a aba e pede a leitura dela.
        val six = TuiReducer.reduce(TuiState(), TuiKey.Text('6'), context)
        assertEquals(TuiTab.ANALYSIS, six.state.tab)
        assertEquals(listOf<TuiEffect>(TuiEffect.Refresh), six.effects)
    }

    @Test
    fun `rolar desce o corpo e a ultima linha aparece no fim`() {
        val size = TuiSize(96, 24)
        val max = TuiScreen.context(data(), size).analysisScrollMax
        val lines = screen(TuiAnalysisState(scroll = max), size)
        assertTrue(lines[size.rows - 4].contains("tok/s na última hora: 103"), lines.joinToString("\n"))
    }

    @Test
    fun `sem conta com analise ou sem historico, a frase diz o que falta`() {
        assertTrue(screen(data = data(accounts = emptyList(), result = null))[3].startsWith("│ Nenhuma conta com análise: só o Claude Code e o Codex têm."))
        val lines = screen(data = data(accounts = listOf(codex), result = null))
        assertTrue(lines[3].startsWith("│ ● Codex") && "conta 1 de" !in lines[3], lines[3])
        assertTrue(lines[5].startsWith("│ Sem histórico lido para esta conta."), lines[5])
    }

    @Test
    fun `largura e altura exatas de 40 a 200 colunas, e em ASCII`() {
        for (size in listOf(TuiSize(200, 40), TuiSize(96, 24), TuiSize(80, 24), TuiSize(79, 24), TuiSize(60, 14), TuiSize(40, 12))) {
            for (profile in listOf(color, TerminalProfile(TerminalPalette.NONE, unicode = false))) {
                val lines = TuiScreen.render(TuiState(tab = TuiTab.ANALYSIS), data(), size, pt, profile)
                assertEquals(size.rows, lines.size)
                assertTrue(lines.all { line -> line.width == size.columns }, "$size $profile")
            }
        }
    }

    @Test
    fun `a corrida corta na duracao da janela e diz quando o ritmo esta parado`() {
        val slow = fiveHour.copy(paceNow = 0.0, paceAverage = 0.01)
        val race = TerminalRace.of(slow, now)
        assertEquals(0.0, race.lastHour?.pacePerHour)
        assertNull(race.lastHour?.exhaustIn)
        assertEquals(5.hours, race.ruler)
        val lines = TerminalRaceView.render(slow, TerminalColor.Muted, now, 100, TerminalAnalysisLabels(AppLanguage.PT), TerminalLabels(AppLanguage.PT), color).map { line -> line.plainText }
        assertTrue(lines[2].contains("parado: não esgota neste ritmo"), lines[2])
        assertTrue(lines[3].contains("mais de 5 h no ritmo médio"), lines[3])
        // Sem reinício futuro não há régua.
        val noReset = TerminalRaceView.render(fiveHour.copy(resetsAt = null, startsAt = null), TerminalColor.Muted, now, 100, TerminalAnalysisLabels(AppLanguage.PT), TerminalLabels(AppLanguage.PT), color)
        assertEquals("  sem reinício conhecido", noReset.last().plainText)
    }
}
