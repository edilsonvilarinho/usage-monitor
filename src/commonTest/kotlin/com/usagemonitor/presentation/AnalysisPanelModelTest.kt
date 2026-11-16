package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnalysisPeriod
import com.usagemonitor.domain.entity.AnalysisPeriodRate
import com.usagemonitor.domain.entity.AnalysisPeriodSummary
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.AnalysisFact
import com.usagemonitor.presentation.ui.AnalysisHourBar
import com.usagemonitor.presentation.ui.AnalysisIntervalRowModel
import com.usagemonitor.presentation.ui.AnalysisPanelModel
import com.usagemonitor.presentation.ui.AnalysisPaceComparatorModel
import com.usagemonitor.presentation.ui.analysisBarAt
import com.usagemonitor.presentation.ui.analysisAxisMax
import com.usagemonitor.presentation.ui.analysisBarFraction
import com.usagemonitor.presentation.ui.PaceDiagonalModels
import com.usagemonitor.domain.entity.PacePathPoint
import com.usagemonitor.presentation.ui.PaceLegendEntry
import com.usagemonitor.presentation.ui.PaceLegendTone
import com.usagemonitor.presentation.ui.components.AppChartKeyStyle
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Painel único da janela "Análise" (rodada AD, AD1): textos, tons e geometria, sem tela. */
class AnalysisPanelModelTest {

    private val analysis = AnalysisFixture.analysis

    @Test
    fun `window card puts the window on the pace diagonal and says how it ends`() {
        // 09:00–14:00 BRT, lida às 12:30: 70% do tempo e 62% da cota, 8 pontos abaixo da linha.
        val fiveHour = AnalysisPanelModel.windowCard(analysis.fiveHour!!, analysis.generatedAt, AppLanguage.PT)
        assertEquals("cabe até o reinício", fiveHour.verdict)
        assertEquals("Cabe até o reinício: usa o ritmo da última hora (14 %/h). Projeção de 83 % no reinício, abaixo dos 100 %.", fiveHour.verdictSentence)
        assertEquals(AppTone.OK, fiveHour.verdictTone)
        assertEquals("reinicia 14:00", fiveHour.resetLabel)
        assertEquals("62% usada com 70% da janela já passada: 8 pontos abaixo da linha. Chega a ~83% no reinício (14:00).", fiveHour.sentence)
        assertTrue(fiveHour.facts.contains(AnalysisFact("Ritmo seguro", "25 %/h")))
        assertTrue(fiveHour.facts.contains(AnalysisFact("Última hora", "14 %/h")))
        val diagonal = fiveHour.diagonal
        assertEquals("62% · 70% do tempo", diagonal.pointLabel)
        assertEquals(listOf("09:00", "11:30", "14:00"), diagonal.axisLabels)
        assertEquals(listOf(PacePathPoint(0.7, 62.0), PacePathPoint(1.0, 83.0)), diagonal.projection)
        assertFalse(diagonal.projectionCritical)
        assertEquals(fiveHour.sentence, diagonal.description)

        // A leitura nova chega antes do caminho relido: a linha termina nela, e "1 ponto" no singular.
        val newer = AnalysisPanelModel.windowCard(analysis.fiveHour!!.copy(percent = 71, projectedAtReset = 92), analysis.generatedAt, AppLanguage.PT)
        assertEquals(PacePathPoint(0.7, 71.0), newer.diagonal.current.last())
        assertTrue(newer.sentence.startsWith("71% usada com 70% da janela já passada: 1 ponto acima da linha."))
    }

    @Test
    fun `a window that runs out says when and how long before the reset`() {
        // 38% a 30 %/h: 76 min até 100%, 14 min antes do reinício das 14:00.
        val full = AnalysisPanelModel.windowCard(analysis.fiveHour!!.copy(projectedAtReset = 100, paceNow = 30.0), analysis.generatedAt, AppLanguage.EN)
        assertEquals("runs out before the reset", full.verdict)
        assertEquals(
            "Runs out before the reset: uses the last hour's pace (30 %/h). At the window's average pace (18 %/h) it would fit.",
            full.verdictSentence
        )
        assertEquals(AppTone.CRITICAL, full.verdictTone)
        assertEquals(
            "62% used with 70% of the window gone: 8 points under the line. At the last hour's pace (30 %/h) it runs out around 13:46, 14 min before the reset.",
            full.sentence
        )
        assertEquals(AnalysisFact("Runs out", "Tue 13:46 · 14 min before the reset"), full.facts.last())
        val projection = full.diagonal.projection
        assertTrue(full.diagonal.projectionCritical)
        assertEquals(100.0, projection[1].percent)
        assertEquals((210.0 + 76.0) / 300.0, projection[1].elapsedFraction, 1e-6)
        assertEquals(PacePathPoint(1.0, 100.0), projection.last())
    }

    @Test
    fun `a 5 hour window with no open window keeps the previous paths behind the diagonal, without the live line`() {
        // Sem início nem reinício: não há janela aberta, mas as anteriores continuam atrás da diagonal.
        val closed = AnalysisPanelModel.windowCard(analysis.fiveHour!!.copy(startsAt = null, resetsAt = null), analysis.generatedAt, AppLanguage.EN)
        assertEquals("62% used; no open window, the chart shows only the previous ones.", closed.sentence)
        val diagonal = assertNotNull(closed.diagonal)
        assertEquals(emptyList(), diagonal.current)
        assertEquals(analysis.fiveHour!!.previousPaths.map { path -> path.points }, diagonal.previous)
        assertEquals(listOf("start", "2 h 30 min", "reset"), diagonal.axisLabels)
        assertNull(diagonal.nowLabel)
        assertNull(diagonal.pointLabel)
        assertNull(diagonal.endLabel)
        assertNull(diagonal.exhaustionLabel)
        assertEquals(emptyList(), diagonal.projection)
        assertFalse(diagonal.projectionCritical)
        assertEquals(100.0, diagonal.yMax)
        assertEquals(listOf(PaceLegendEntry("1 previous window", AppChartKeyStyle.FADED, PaceLegendTone.MUTED)), diagonal.legend)
        assertEquals(closed.sentence, diagonal.description)
    }

    @Test
    fun `a weekly window with no open window and no previous paths still draws the axis in days`() {
        val closed = AnalysisPanelModel.windowCard(analysis.weekly!!.copy(startsAt = null, resetsAt = null, projectedAtReset = null, paceNow = null, paceAverage = null), analysis.generatedAt, AppLanguage.EN)
        assertEquals("Weekly", closed.title)
        assertEquals("41% used; no open window, the chart shows only the previous ones.", closed.sentence)
        val diagonal = assertNotNull(closed.diagonal)
        assertEquals(emptyList(), diagonal.previous)
        assertEquals(emptyList(), diagonal.current)
        assertEquals(listOf("start", "3 d 12 h", "reset"), diagonal.axisLabels)
        assertEquals(emptyList(), diagonal.legend)
        assertNull(diagonal.nowLabel)
    }

    @Test
    fun `durations keep the two largest units`() {
        assertEquals("9 min", PaceDiagonalModels.durationLabel(9 * 60_000L))
        assertEquals("1 h 48 min", PaceDiagonalModels.durationLabel(108 * 60_000L))
        assertEquals("3 d 3 h", PaceDiagonalModels.durationLabel((3 * 24 + 3) * 3_600_000L))
        assertEquals("2 h", PaceDiagonalModels.durationLabel(2 * 3_600_000L))
    }

    @Test
    fun `intervals compare each pace against the 7 day one`() {
        val rows = AnalysisPanelModel.intervals(analysis, AppLanguage.PT)
        assertEquals(
            listOf("↑ 1,9× mais rápido", "↑ 2,2× mais rápido", "↑ 2,4× mais rápido", "↑ 1,1× mais rápido", "referência"),
            rows.map { row -> row.ratio }
        )
        assertEquals(AppTone.WARNING, rows.first().ratioTone)
        assertEquals("48", rows.first().tokensPerSecond)
    }

    @Test
    fun `a 30 day table adds the period row against the 7 day pace`() {
        val rates = analysis.copy(period = AnalysisPeriod.DAYS_30, periodRate = AnalysisPeriodRate(AnalysisPeriod.DAYS_30, 9.0, 40.0))
        val rows = AnalysisPanelModel.intervals(rates, AppLanguage.PT)
        assertEquals(6, rows.size)
        assertEquals(AnalysisIntervalRowModel("30 d", "9,0", "40", "↑ 1,2× mais rápido", AppTone.WARNING), rows.last())
        assertEquals("Ritmo por intervalo · 30 d", AnalysisPanelModel.intervalsTitle(AnalysisPeriod.DAYS_30, AppLanguage.PT))
        assertEquals("Pace by interval · now", AnalysisPanelModel.intervalsTitle(AnalysisPeriod.NOW, AppLanguage.EN))
    }

    @Test
    fun `period summary and bar titles follow the chosen period`() {
        val hours = analysis.copy(period = AnalysisPeriod.HOURS_24, fiveHourSummary = AnalysisPeriodSummary(3, 1, 72))
        assertEquals("3 janelas de 5 h nas últimas 24 h · 1 de 3 esgotaram · pico médio 72 %", AnalysisPanelModel.periodSummary(hours, AppLanguage.PT))
        val month = analysis.copy(period = AnalysisPeriod.DAYS_30, weeklySummary = AnalysisPeriodSummary(4, 0, 61))
        assertEquals("4 semanas anteriores · 0 de 4 esgotaram · pico médio 61 %", AnalysisPanelModel.periodSummary(month, AppLanguage.PT))
        assertNull(AnalysisPanelModel.periodSummary(analysis, AppLanguage.PT))
        assertEquals("Pico por dia · 7 d", AnalysisPanelModel.barsTitle(AnalysisPeriod.DAYS_7, AppLanguage.PT))
        assertEquals("Peak per week · all", AnalysisPanelModel.barsTitle(AnalysisPeriod.ALL, AppLanguage.EN))
        assertNull(AnalysisPanelModel.barsTitle(AnalysisPeriod.NOW, AppLanguage.PT))
    }

    @Test
    fun `hours keep not measured apart from zero and name the peak`() {
        val bars = AnalysisPanelModel.hours(analysis)
        assertEquals(24, bars.size)
        assertEquals("pico 15h · 17%", AnalysisPanelModel.peakLabel(bars, AppLanguage.PT))
        assertEquals("15h–16h · 17,0% · 48 tok/s", AnalysisPanelModel.hourTooltip(bars[2], AppLanguage.PT))
        assertEquals("09h–10h · no reading", AnalysisPanelModel.hourTooltip(AnalysisHourBar("09", null, null), AppLanguage.EN))
        assertEquals("no usage in the last 24 h", AnalysisPanelModel.peakLabel(listOf(AnalysisHourBar("09", 0.0, null)), AppLanguage.EN))

        assertNull(analysisBarFraction(null, 10.0))
        assertEquals(0f, analysisBarFraction(0.0, 10.0))
        assertEquals(0.5f, analysisBarFraction(5.0, 10.0))
        assertEquals(0f, analysisBarFraction(3.0, 0.0))
        assertEquals(2, analysisBarAt(25f, 240f, 24))
        // Topo do eixo par, para o meio ser inteiro: 17 → 18 (meio 9), 32 fica.
        assertEquals(18, analysisAxisMax(17.0))
        assertEquals(32, analysisAxisMax(32.0))
        assertEquals(2, analysisAxisMax(0.0))
        assertNull(analysisBarAt(-1f, 240f, 24))
    }

    @Test
    fun `live label and speed line say when the numbers are from`() {
        assertEquals("Ao vivo · 12:30:00", AnalysisPanelModel.liveLabel(analysis.generatedAt, null, AppLanguage.PT))
        assertEquals(
            "Falhou às 12:31:00 · leitura das 12:30:00",
            AnalysisPanelModel.liveLabel(analysis.generatedAt, analysis.generatedAt + 1.minutes, AppLanguage.PT)
        )
        assertEquals("tok/s in the last hour: claude-sonnet 71 · claude-opus 48", AnalysisPanelModel.speedLine(analysis, AppLanguage.EN))
        assertEquals(
            "tok/s na última hora: sem medição · índice do Codex às 12:20",
            AnalysisPanelModel.speedLine(analysis.copy(modelSpeeds = emptyList(), throughputIndexedAt = analysis.generatedAt - 10.minutes), AppLanguage.PT)
        )
    }

    private fun Float?.pct(): Int? = this?.let { (it * 100).roundToInt() }

    @Test
    fun `pace comparator scale steps 1-2-3-5 times a power of ten above the highest pace`() {
        assertEquals(30.0, AnalysisPaceComparatorModel.scaleCeiling(29.0))
        assertEquals(3.0, AnalysisPaceComparatorModel.scaleCeiling(2.7))
        assertEquals(5.0, AnalysisPaceComparatorModel.scaleCeiling(5.0))
        assertEquals(1.0, AnalysisPaceComparatorModel.scaleCeiling(null))
    }

    @Test
    fun `pace comparator puts both paces and the safe marker on one scale`() {
        // 5h: teto 30 (maior dos três é o seguro, 25,3); média 17,7 → 59%; última hora 14 → 47%; seguro → marca em 84%.
        val comparator = AnalysisPaceComparatorModel.of(analysis.fiveHour!!, AppLanguage.PT)
        assertEquals("Ritmo · mesma escala · 0 a 30 %/h", comparator.eyebrow)
        assertEquals("seguro 25 %/h", comparator.safeCaption)
        assertEquals(84, comparator.marker.pct())
        assertEquals(listOf("Média", "Última hora"), comparator.rows.map { it.label })
        assertEquals(listOf("18 %/h", "14 %/h"), comparator.rows.map { it.value })
        assertEquals(listOf(59, 47), comparator.rows.map { it.fraction.pct() })
        assertEquals(listOf("abaixo do seguro", "abaixo do seguro"), comparator.rows.map { it.verdict })
    }

    @Test
    fun `pace comparator says above the safe pace only when the pace is over it`() {
        val comparator = AnalysisPaceComparatorModel.of(analysis.fiveHour!!.copy(paceAverage = 27.0), AppLanguage.PT)
        assertEquals(listOf("acima do seguro", "abaixo do seguro"), comparator.rows.map { it.verdict })
        assertEquals(listOf(true, false), comparator.rows.map { it.aboveSafe })
        // Igualdade com o seguro cai em "abaixo", como no protótipo p05.
        val equal = AnalysisPaceComparatorModel.of(analysis.fiveHour!!.copy(paceAverage = 25.3), AppLanguage.PT)
        assertEquals("abaixo do seguro", equal.rows[0].verdict)
    }

    @Test
    fun `pace comparator shows a null pace as a dash without bar or word`() {
        val lastHour = AnalysisPaceComparatorModel.of(analysis.fiveHour!!.copy(paceNow = null), AppLanguage.PT).rows[1]
        assertEquals("—", lastHour.value)
        assertNull(lastHour.fraction)
        assertNull(lastHour.verdict)
    }

    @Test
    fun `pace comparator without a safe pace has no marker and no word`() {
        // Sem seguro, o teto sai do maior ritmo medido (17,0 → 20): média 85%, última hora 70%.
        val comparator = AnalysisPaceComparatorModel.of(analysis.fiveHour!!.copy(safePace = null, paceAverage = 17.0), AppLanguage.PT)
        assertEquals("Ritmo · mesma escala · 0 a 20 %/h", comparator.eyebrow)
        assertNull(comparator.marker)
        assertNull(comparator.safeCaption)
        assertEquals(listOf(85, 70), comparator.rows.map { it.fraction.pct() })
        assertEquals(listOf(null, null), comparator.rows.map { it.verdict })
    }

    @Test
    fun `pace comparator writes one decimal below ten in both languages`() {
        // Protótipo p05: 2,7 e 2,0 com teto 3; seguro 2,2 → marca em 73%.
        val window = analysis.weekly!!.copy(paceAverage = 2.7, paceNow = 2.0, safePace = 2.2)
        val pt = AnalysisPaceComparatorModel.of(window, AppLanguage.PT)
        assertEquals("Ritmo · mesma escala · 0 a 3 %/h", pt.eyebrow)
        assertEquals(listOf("2,7 %/h", "2,0 %/h"), pt.rows.map { it.value })
        assertEquals(listOf("acima do seguro", "abaixo do seguro"), pt.rows.map { it.verdict })
        assertEquals(73, pt.marker.pct())
        val en = AnalysisPaceComparatorModel.of(window, AppLanguage.EN)
        assertEquals("Pace · same scale · 0 to 3 %/h", en.eyebrow)
        assertEquals(listOf("2.7 %/h", "2.0 %/h"), en.rows.map { it.value })
        assertEquals(listOf("above the safe pace", "below the safe pace"), en.rows.map { it.verdict })
    }
}
