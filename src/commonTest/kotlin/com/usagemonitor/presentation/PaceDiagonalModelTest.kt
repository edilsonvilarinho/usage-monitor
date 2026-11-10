package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.PaceDiagonalModels
import kotlin.test.Test
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
            listOf("linha segura: 25 %/h (100 % no reinício)", "seu uso até agora", "projeção pelo ritmo da última hora", "agora (linha pontilhada)", "janelas anteriores"),
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
}
