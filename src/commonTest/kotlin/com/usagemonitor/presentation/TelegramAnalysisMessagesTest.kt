package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotTap
import com.usagemonitor.domain.entity.parseBotTap
import com.usagemonitor.presentation.ui.TelegramAnalysisMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** As vistas de texto da análise (rodada AB), no idioma do app. */
class TelegramAnalysisMessagesTest {

    @Test
    fun `panel shows both windows with pace, safe pace and the throughput per model`() {
        val pt = TelegramAnalysisMessages.panel(AnalysisFixture.analysis, AppLanguage.PT)
        assertTrue(pt.startsWith("<b>📐 Anthropic · &lt;Padrão&gt;</b> · <i>análise 12:30 BRT</i>"), pt)
        assertTrue("JANELA 5h    62%   reinicia 14:00" in pt, pt)
        assertTrue(" 14,0 pp/h" in pt && " 25,3 pp/h  → folga" in pt && "no reset ≈ 83%" in pt, pt)
        assertTrue("TOKENS/s (1 h)  claude-sonnet 71 · claude-opus 48" in pt, pt)

        val en = TelegramAnalysisMessages.panel(AnalysisFixture.analysis, AppLanguage.EN)
        assertTrue("5h WINDOW" in en && " 14.0 pp/h" in en && "room left" in en, en)
    }

    @Test
    fun `tabs mark the current view and travel as analysis taps`() {
        val rows = TelegramAnalysisMessages.tabs("ANTHROPIC:1a2b3c", AnalysisView.PANEL, AppLanguage.PT)
        val first = rows.first().first()
        assertEquals("• Painel •", first.first)
        assertEquals(BotTap.AnalysisShow("ANTHROPIC:1a2b3c", AnalysisView.PANEL), parseBotTap(first.second))
        assertTrue(rows.flatten().all { (_, data) -> data.length <= 64 })
    }
}
