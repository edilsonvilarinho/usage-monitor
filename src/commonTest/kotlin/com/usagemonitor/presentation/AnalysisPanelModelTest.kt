package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.AnalysisHourBar
import com.usagemonitor.presentation.ui.AnalysisPanelModel
import com.usagemonitor.presentation.ui.analysisBarAt
import com.usagemonitor.presentation.ui.analysisAxisMax
import com.usagemonitor.presentation.ui.analysisBarFraction
import com.usagemonitor.presentation.ui.PaceDiagonalModels
import com.usagemonitor.domain.entity.PacePathPoint
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(AppTone.OK, fiveHour.verdictTone)
        assertEquals("reinicia 14:00", fiveHour.resetLabel)
        assertEquals("62% usada com 70% da janela já passada: 8 pontos abaixo da linha. Chega a ~83% no reinício (14:00).", fiveHour.sentence)
        val diagonal = fiveHour.diagonal!!
        assertEquals("62% · 70% do tempo", diagonal.pointLabel)
        assertEquals(listOf("09:00", "11:30", "14:00"), diagonal.axisLabels)
        assertEquals(listOf(PacePathPoint(0.7, 62.0), PacePathPoint(1.0, 83.0)), diagonal.projection)
        assertFalse(diagonal.projectionCritical)
        assertEquals(fiveHour.sentence, diagonal.description)

        // A leitura nova chega antes do caminho relido: a linha termina nela, e "1 ponto" no singular.
        val newer = AnalysisPanelModel.windowCard(analysis.fiveHour!!.copy(percent = 71, projectedAtReset = 92), analysis.generatedAt, AppLanguage.PT)
        assertEquals(PacePathPoint(0.7, 71.0), newer.diagonal!!.current.last())
        assertTrue(newer.sentence.startsWith("71% usada com 70% da janela já passada: 1 ponto acima da linha."))
    }

    @Test
    fun `a window that runs out says when and how long before the reset`() {
        // 38% a 30 %/h: 76 min até 100%, 14 min antes do reinício das 14:00.
        val full = AnalysisPanelModel.windowCard(analysis.fiveHour!!.copy(projectedAtReset = 100, paceNow = 30.0), analysis.generatedAt, AppLanguage.EN)
        assertEquals("runs out before the reset", full.verdict)
        assertEquals(AppTone.CRITICAL, full.verdictTone)
        assertEquals(
            "62% used with 70% of the window gone: 8 points under the line. At the last hour's pace (30 %/h) it runs out around 13:46, 14 min before the reset.",
            full.sentence
        )
        val projection = full.diagonal!!.projection
        assertTrue(full.diagonal!!.projectionCritical)
        assertEquals(100.0, projection[1].percent)
        assertEquals((210.0 + 76.0) / 300.0, projection[1].elapsedFraction, 1e-6)
        assertEquals(PacePathPoint(1.0, 100.0), projection.last())
    }

    @Test
    fun `a window without its start has no diagonal, only the sentence`() {
        val unknown = AnalysisPanelModel.windowCard(analysis.weekly!!.copy(startsAt = null, projectedAtReset = null, paceNow = null, paceAverage = null), analysis.generatedAt, AppLanguage.EN)
        assertEquals("no projection", unknown.verdict)
        assertEquals(AppTone.NEUTRAL, unknown.verdictTone)
        assertEquals("Weekly", unknown.title)
        assertEquals("41% used; with no known reset there is no pace line.", unknown.sentence)
        assertNull(unknown.diagonal)
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
}
