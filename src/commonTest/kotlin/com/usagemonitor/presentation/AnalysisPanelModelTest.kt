package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.AnalysisHourBar
import com.usagemonitor.presentation.ui.AnalysisPanelModel
import com.usagemonitor.presentation.ui.analysisBarAt
import com.usagemonitor.presentation.ui.analysisBarFraction
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/** Painel único da janela "Análise" (rodada AD, AD1): textos, tons e geometria, sem tela. */
class AnalysisPanelModelTest {

    private val analysis = AnalysisFixture.analysis

    @Test
    fun `window card says whether the quota fits until the reset`() {
        val fiveHour = AnalysisPanelModel.windowCard(analysis.fiveHour!!, analysis.generatedAt, AppLanguage.PT)
        assertEquals("cabe até o reinício", fiveHour.verdict)
        assertEquals(AppTone.OK, fiveHour.verdictTone)
        assertEquals("≈83% no reinício", fiveHour.projectionLabel)
        assertEquals("reinicia 14:00", fiveHour.resetLabel)
        assertEquals("14,0", fiveHour.paceNow)
        assertEquals("folga", fiveHour.safeVerdict)

        val full = AnalysisPanelModel.windowCard(analysis.fiveHour!!.copy(projectedAtReset = 100, paceNow = 30.0), analysis.generatedAt, AppLanguage.EN)
        assertEquals("runs out before the reset", full.verdict)
        assertEquals(AppTone.CRITICAL, full.verdictTone)
        assertEquals("above the safe pace", full.safeVerdict)

        val unknown = AnalysisPanelModel.windowCard(analysis.weekly!!.copy(projectedAtReset = null, paceNow = null, paceAverage = null), analysis.generatedAt, AppLanguage.EN)
        assertEquals("no projection", unknown.verdict)
        assertEquals(AppTone.NEUTRAL, unknown.verdictTone)
        assertNull(unknown.safeVerdict)
        assertEquals("—", unknown.paceNow)
        assertEquals("Weekly", unknown.title)
    }

    @Test
    fun `intervals compare each pace against the 7 day one`() {
        val rows = AnalysisPanelModel.intervals(analysis, AppLanguage.PT)
        assertEquals(listOf("↑ 1,9×", "↑ 2,2×", "↑ 2,4×", "↑ 1,1×", "referência"), rows.map { row -> row.ratio })
        assertEquals(AppTone.WARNING, rows.first().ratioTone)
        assertEquals("48", rows.first().tokensPerSecond)
    }

    @Test
    fun `versus marks less usage as room and leaves throughput neutral`() {
        val rows = AnalysisPanelModel.versus(analysis, AppLanguage.EN)!!
        assertEquals("5h at 3h30", rows[0].label)
        assertEquals("↓ 13%", rows[0].change)
        assertEquals(AppTone.OK, rows[0].changeTone)
        assertEquals("↑ 14%", rows[3].change)
        assertEquals(AppTone.NEUTRAL, rows[3].changeTone)
        assertNull(AnalysisPanelModel.versus(analysis.copy(previous = null), AppLanguage.EN))
    }

    @Test
    fun `hours keep not measured apart from zero and name the peak`() {
        val bars = AnalysisPanelModel.hours(analysis)
        assertEquals(24, bars.size)
        assertEquals("pico 15h · 17 pp", AnalysisPanelModel.peakLabel(bars, AppLanguage.PT))
        assertEquals("15h–16h · 17,0 pp · 48 tok/s", AnalysisPanelModel.hourTooltip(bars[2], AppLanguage.PT))
        assertEquals("09h–10h · no reading", AnalysisPanelModel.hourTooltip(AnalysisHourBar("09", null, null), AppLanguage.EN))
        assertEquals("no usage in the last 24 h", AnalysisPanelModel.peakLabel(listOf(AnalysisHourBar("09", 0.0, null)), AppLanguage.EN))

        assertNull(analysisBarFraction(null, 10.0))
        assertEquals(0f, analysisBarFraction(0.0, 10.0))
        assertEquals(0.5f, analysisBarFraction(5.0, 10.0))
        assertEquals(0f, analysisBarFraction(3.0, 0.0))
        assertEquals(2, analysisBarAt(25f, 240f, 24))
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
