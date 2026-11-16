package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.PaceDiagonalLabels
import com.usagemonitor.presentation.ui.PaceDiagonalModels
import com.usagemonitor.presentation.ui.PaceLegendEntry
import com.usagemonitor.presentation.ui.PaceLegendTone
import com.usagemonitor.presentation.ui.components.AppChartKeyStyle
import com.usagemonitor.presentation.ui.previousPathAlpha
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Rótulos do gráfico da diagonal (#538, P1): marcador, ponto final, esgotamento, eixo e legenda. */
class PaceDiagonalModelTest {

    private val analysis = AnalysisFixture.analysis

    @Test
    fun `chart labels say where now is and how the window ends`() {
        // Lida às 12:30 BRT; reinício 14:00; projeção 83% no reinício, sem esgotar.
        val fiveHour = PaceDiagonalModels.forWindow(analysis.fiveHour!!, analysis.generatedAt, AppLanguage.PT, "descrição")
        assertEquals("agora 12:30", fiveHour.nowLabel)
        assertEquals("83 % no reinício", fiveHour.endLabel)
        assertNull(fiveHour.exhaustionLabel)
        assertEquals(100.0, fiveHour.yMax)
        assertEquals(
            listOf(
                PaceLegendEntry("linha segura: 25 %/h (100 % no reinício)", AppChartKeyStyle.DASHED, PaceLegendTone.GOLD),
                PaceLegendEntry("seu uso até agora", AppChartKeyStyle.SOLID, PaceLegendTone.LINE),
                PaceLegendEntry("projeção pelo ritmo da última hora", AppChartKeyStyle.DASHED, PaceLegendTone.LINE),
                PaceLegendEntry("agora (linha pontilhada)", AppChartKeyStyle.DOTTED, PaceLegendTone.MUTED),
                PaceLegendEntry(PaceDiagonalLabels.above(AppLanguage.PT), AppChartKeyStyle.ZONE, PaceLegendTone.CRITICAL),
                PaceLegendEntry(PaceDiagonalLabels.below(AppLanguage.PT), AppChartKeyStyle.ZONE, PaceLegendTone.OK),
                PaceLegendEntry("1 janela anterior", AppChartKeyStyle.FADED, PaceLegendTone.MUTED),
            ),
            fiveHour.legend
        )

        // Esgota às 13:46: sem ponto final, rótulo no cruzamento dos 100%; a projeção vira crítica.
        val full = PaceDiagonalModels.forWindow(analysis.fiveHour!!.copy(projectedAtReset = 100, paceNow = 30.0), analysis.generatedAt, AppLanguage.PT, "descrição")
        assertEquals("esgota ter 13:46", full.exhaustionLabel)
        assertNull(full.endLabel)
        assertContains(full.legend, PaceLegendEntry("projeção pelo ritmo da última hora", AppChartKeyStyle.DASHED, PaceLegendTone.CRITICAL))

        val english = PaceDiagonalModels.forWindow(analysis.fiveHour!!.copy(projectedAtReset = 100, paceNow = 30.0), analysis.generatedAt, AppLanguage.EN, "description")
        assertEquals("runs out Tue 13:46", english.exhaustionLabel)
        assertEquals("now 12:30", english.nowLabel)

        // Projeção acima de 100%: o eixo Y vai até ela.
        val above = PaceDiagonalModels.forWindow(analysis.fiveHour!!.copy(projectedAtReset = 150), analysis.generatedAt, AppLanguage.PT, "descrição")
        assertEquals(150.0, above.yMax)
    }

    @Test
    fun `previous windows fade as they pile up and never below the floor`() {
        // Até três linhas, opacidade cheia (0,45); acima disso cai com a raiz de 3/n; piso 0,12.
        assertEquals(0.45f, previousPathAlpha(1), 1e-4f)
        assertEquals(0.45f, previousPathAlpha(3), 1e-4f)
        assertEquals(0.225f, previousPathAlpha(12), 1e-4f)
        assertEquals(0.12f, previousPathAlpha(200), 1e-4f)
    }

    @Test
    fun `legend counts the previous windows drawn in the chosen language`() {
        val previous = analysis.fiveHour!!.previousPaths.single()
        val twelve = analysis.fiveHour!!.copy(previousPaths = List(12) { previous })
        val pt = PaceDiagonalModels.forWindow(twelve, analysis.generatedAt, AppLanguage.PT, "descrição")
        assertContains(pt.legend, PaceLegendEntry("12 janelas anteriores", AppChartKeyStyle.FADED, PaceLegendTone.MUTED))
        val en = PaceDiagonalModels.forWindow(twelve, analysis.generatedAt, AppLanguage.EN, "description")
        assertContains(en.legend, PaceLegendEntry("12 previous windows", AppChartKeyStyle.FADED, PaceLegendTone.MUTED))
        val one = PaceDiagonalModels.forWindow(analysis.fiveHour!!, analysis.generatedAt, AppLanguage.EN, "description")
        assertContains(one.legend, PaceLegendEntry("1 previous window", AppChartKeyStyle.FADED, PaceLegendTone.MUTED))
    }
}
