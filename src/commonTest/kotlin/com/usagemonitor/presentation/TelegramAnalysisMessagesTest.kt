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

    /** AB2: uma linha por métrica com a tendência, o valor de agora e a seta contra o trecho anterior. */
    @Test
    fun `trend draws one sparkline per metric`() {
        val text = TelegramAnalysisMessages.text(AnalysisView.TREND, AnalysisFixture.analysis, AppLanguage.PT)!!
        assertTrue("5h %" in text && "62%" in text && "+10,0 pp" in text, text)
        assertTrue("semanal" in text && "41%" in text, text)
        assertEquals("▁▅█ ", com.usagemonitor.presentation.ui.TelegramAnalysisTexts.spark(listOf(0.0, 5.0, 10.0, null)))
        assertTrue("weekly" in TelegramAnalysisMessages.text(AnalysisView.TREND, AnalysisFixture.analysis, AppLanguage.EN)!!)
    }

    /** AB3: cada intervalo com ritmo, vazão e a razão contra 7 dias; 7 dias não se compara consigo. */
    @Test
    fun `intervals compare each span with the week`() {
        val text = TelegramAnalysisMessages.text(AnalysisView.INTERVALS, AnalysisFixture.analysis, AppLanguage.PT)!!
        assertTrue("1 h         14,0      48   ↑ 1,9×" in text, text)
        assertTrue("7 d          7,4      47        —" in text, text)
        assertTrue("pace by interval" in TelegramAnalysisMessages.text(AnalysisView.INTERVALS, AnalysisFixture.analysis, AppLanguage.EN)!!)
    }

    /** AB4: frases só com dado medido — reinício, comparação no mesmo ponto, hora pesada, semanal e vazão. */
    @Test
    fun `sentences read the numbers in plain words`() {
        val text = TelegramAnalysisMessages.text(AnalysisView.SENTENCES, AnalysisFixture.analysis, AppLanguage.PT)!!
        assertTrue("• A 5h está em <b>62%</b> e chega a <b>~83%</b> no reinício das 14:00 — <b>não estoura</b>." in text, text)
        assertTrue("<b>13% abaixo</b> da janela anterior no mesmo ponto" in text, text)
        assertTrue("Hora mais pesada nas últimas 24 h: <b>15h</b> (17 pp)." in text, text)
        assertTrue("cabem <b>1,5 pp/h</b>" in text && "claude-sonnet 71 tok/s" in text, text)
        val bare = AnalysisFixture.analysis.copy(previous = null, modelSpeeds = emptyList())
        val en = TelegramAnalysisMessages.text(AnalysisView.SENTENCES, bare, AppLanguage.EN)!!
        assertTrue("previous window" !in en && "In the last hour" !in en && "does not run out" in en, en)
    }

    /** AB5: uma fatia de 30 min por linha, barra sobre a maior fatia, ritmo da fatia e vazão. */
    @Test
    fun `timeline lists the open window in half hours`() {
        val text = TelegramAnalysisMessages.text(AnalysisView.TIMELINE, AnalysisFixture.analysis, AppLanguage.PT)!!
        assertTrue("09:00 ▰▰▰▱▱   6 pp  12/h  50 t/s" in text, text)
        assertTrue("11:30 ▰▰▰▰▰  11 pp  22/h  50 t/s" in text, text)
        assertTrue(text.endsWith("<i>total 62% · reinicia 14:00</i>"), text)
        val closed = AnalysisFixture.analysis.copy(slices = emptyList())
        assertTrue("No 5h window open" in TelegramAnalysisMessages.text(AnalysisView.TIMELINE, closed, AppLanguage.EN)!!)
    }
}
