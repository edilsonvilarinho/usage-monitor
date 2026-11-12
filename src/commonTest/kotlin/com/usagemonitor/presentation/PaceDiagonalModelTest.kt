package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.PaceDiagonalModels
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
        val fiveHour = PaceDiagonalModels.forWindow(analysis.fiveHour!!, analysis.generatedAt, AppLanguage.PT, "descrição")!!
        assertEquals("agora 12:30", fiveHour.nowLabel)
        assertEquals("83 % no reinício", fiveHour.endLabel)
        assertNull(fiveHour.exhaustionLabel)
        assertEquals(100.0, fiveHour.yMax)
        assertEquals(
            listOf("linha segura: 25 %/h (100 % no reinício)", "seu uso até agora", "projeção pelo ritmo da última hora", "agora (linha pontilhada)", "1 janela anterior"),
            fiveHour.legend
        )

        // Esgota às 13:46: sem ponto final, rótulo no cruzamento dos 100%.
        val full = PaceDiagonalModels.forWindow(analysis.fiveHour!!.copy(projectedAtReset = 100, paceNow = 30.0), analysis.generatedAt, AppLanguage.PT, "descrição")!!
        assertEquals("esgota ter 13:46", full.exhaustionLabel)
        assertNull(full.endLabel)

        val english = PaceDiagonalModels.forWindow(analysis.fiveHour!!.copy(projectedAtReset = 100, paceNow = 30.0), analysis.generatedAt, AppLanguage.EN, "description")!!
        assertEquals("runs out Tue 13:46", english.exhaustionLabel)
        assertEquals("now 12:30", english.nowLabel)

        // Projeção acima de 100%: o eixo Y vai até ela.
        val above = PaceDiagonalModels.forWindow(analysis.fiveHour!!.copy(projectedAtReset = 150), analysis.generatedAt, AppLanguage.PT, "descrição")!!
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
        val pt = PaceDiagonalModels.forWindow(twelve, analysis.generatedAt, AppLanguage.PT, "descrição")!!
        assertContains(pt.legend, "12 janelas anteriores")
        val en = PaceDiagonalModels.forWindow(twelve, analysis.generatedAt, AppLanguage.EN, "description")!!
        assertContains(en.legend, "12 previous windows")
        val one = PaceDiagonalModels.forWindow(analysis.fiveHour!!, analysis.generatedAt, AppLanguage.EN, "description")!!
        assertContains(one.legend, "1 previous window")
    }
}
