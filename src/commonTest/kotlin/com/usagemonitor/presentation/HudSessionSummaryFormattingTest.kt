package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HudSessionSummary
import com.usagemonitor.presentation.ui.hudSessionSummaryLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudSessionSummaryFormattingTest {

    private fun summary(
        active: Int = 2,
        window: Int = 5,
        costMicros: Long = 4_210_000,
        tokens: Long = 1_200_000,
        unpriced: Int = 0
    ) = HudSessionSummary(
        activeSessionCount = active,
        windowSessionCount = window,
        costMicros = costMicros,
        totalTokens = tokens,
        unpricedTurnCount = unpriced
    )

    @Test
    fun `resume sessoes ativas, custo e tokens da janela`() {
        val label = hudSessionSummaryLabel(summary(), AppLanguage.PT)

        assertEquals("2 sessões ativas · \$4.21 · 1,2M tok · 5h", label)
    }

    @Test
    fun `uma sessao ativa sai no singular`() {
        val label = hudSessionSummaryLabel(summary(active = 1), AppLanguage.PT)

        assertTrue(label.startsWith("1 sessão ativa ·"), label)
    }

    /**
     * Zero ativas com trabalho na janela é o caso comum — ninguém digitando
     * agora, o gasto da tarde ainda contando para a quota.
     */
    @Test
    fun `sem sessao ativa o consumo continua na linha`() {
        val label = hudSessionSummaryLabel(summary(active = 0), AppLanguage.PT)

        assertTrue(label.startsWith("0 sessões ativas"), label)
        assertTrue(label.contains("\$4.21"), label)
    }

    /**
     * A janela vai escrita na linha: sem o "5h", o número seria lido como
     * "hoje" ou "sempre", que são outras duas respostas.
     */
    @Test
    fun `a janela aparece no texto`() {
        assertTrue(hudSessionSummaryLabel(summary(), AppLanguage.PT).endsWith("· 5h"))
        assertTrue(hudSessionSummaryLabel(summary(), AppLanguage.EN).endsWith("· 5h"))
    }

    /** Turno sem tarifa faz do custo um piso, e a marca é a mesma do resumo por eixo. */
    @Test
    fun `turno sem tarifa marca o custo com mais`() {
        val label = hudSessionSummaryLabel(summary(unpriced = 3), AppLanguage.PT)

        assertTrue(label.contains("\$4.21+"), label)
    }

    /**
     * A linha existir dizendo "não houve" é informação; a linha sumir é ambíguo
     * com o rodapé ainda não ter carregado.
     */
    @Test
    fun `janela vazia diz que nao houve sessao`() {
        assertEquals(
            "Sem sessão CLI nas últimas 5h",
            hudSessionSummaryLabel(HudSessionSummary.Empty, AppLanguage.PT)
        )
        assertEquals(
            "No CLI session in the last 5h",
            hudSessionSummaryLabel(HudSessionSummary.Empty, AppLanguage.EN)
        )
    }

    @Test
    fun `o texto em ingles nao vaza portugues`() {
        val label = hudSessionSummaryLabel(summary(), AppLanguage.EN)

        assertEquals("2 active sessions · \$4.21 · 1,2M tok · 5h", label)
    }
}
