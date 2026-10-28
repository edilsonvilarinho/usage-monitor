package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.buildSessionAnalysis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Título e legenda das imagens do `/analise` no Telegram. */
class TelegramAnalysisImageLabelsTest {

    private val analysis = buildSessionAnalysis(
        accountLabel = "Anthropic · <Trabalho>",
        source = ApiSource.ANTHROPIC,
        series = emptyList(),
        cumulative = emptyMap(),
        now = Instant.parse("2026-10-28T15:00:00Z")
    )

    @Test
    fun `the card title names the analysis and a text view has no subject`() {
        assertEquals("Anthropic · <Trabalho> — análise", TelegramAnalysisImageLabels.title(AnalysisView.CARD, analysis, AppLanguage.PT))
        assertEquals("Anthropic · <Trabalho> — analysis", TelegramAnalysisImageLabels.title(AnalysisView.CARD, analysis, AppLanguage.EN))
        assertEquals("Anthropic · <Trabalho> — ", TelegramAnalysisImageLabels.title(AnalysisView.PANEL, analysis, AppLanguage.EN))
    }

    @Test
    fun `the caption escapes the account and explains the gauges and the curve`() {
        assertEquals(
            "<b>📐 Anthropic · &lt;Trabalho&gt;</b>\n<i>light mark = the pace that reaches 100% exactly at the reset</i>",
            TelegramAnalysisImageLabels.caption(AnalysisView.GAUGES, analysis, AppLanguage.EN)
        )
        assertEquals(
            "<b>📐 Anthropic · &lt;Trabalho&gt;</b>\n<i>abaixo da reta tracejada, sobra cota no reinício</i>",
            TelegramAnalysisImageLabels.caption(AnalysisView.CURVE, analysis, AppLanguage.PT)
        )
        // Sem nota a legenda é só o nome, sem linha vazia.
        assertEquals("<b>📐 Anthropic · &lt;Trabalho&gt;</b>", TelegramAnalysisImageLabels.caption(AnalysisView.HOURS, analysis, AppLanguage.PT))
    }
}
