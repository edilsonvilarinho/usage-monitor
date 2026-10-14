package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.TelegramAnalysisTexts
import kotlin.test.Test
import kotlin.test.assertEquals

/** Caracterização da leitura em frases (AB4): a saída é contrato com a conversa do bot. */
class TelegramAnalysisTextsTest {

    private val full = AnalysisFixture.analysis

    /** Sem reinício conhecido, sem projeção e sem folga semanal: cada trecho opcional some. */
    private val partial = full.copy(
        fiveHour = full.fiveHour?.copy(resetsAt = null),
        weekly = full.weekly?.copy(safePace = null)
    )

    private val noProjection = full.copy(fiveHour = full.fiveHour?.copy(projectedAtReset = null))

    @Test
    fun `frases completas em PT`() {
        assertEquals("<b>📐 Anthropic · &lt;Padrão&gt; · leitura</b>\n• A 5h está em <b>62%</b> e chega a <b>~83%</b> no reinício das 14:00 — <b>não estoura</b>.\n• Ritmo médio da janela <b>17,7 pp/h</b>, <b>13% abaixo</b> da janela anterior no mesmo ponto.\n• Hora mais pesada nas últimas 24 h: <b>15h</b> (17 pp).\n• Semanal em <b>41%</b>; até o reinício cabem <b>1,5 pp/h</b>.\n• Na última hora: <b>claude-sonnet 71 tok/s</b>, <b>claude-opus 48 tok/s</b>.", TelegramAnalysisTexts.sentences(full, AppLanguage.PT))
    }

    @Test
    fun `frases completas em EN`() {
        assertEquals("<b>📐 Anthropic · &lt;Padrão&gt; · reading</b>\n• The 5h window is at <b>62%</b> and reaches <b>~83%</b> at the 14:00 reset — <b>does not run out</b>.\n• Window average pace <b>17.7 pp/h</b>, <b>13% below</b> the previous window at the same point.\n• Heaviest hour in the last 24 h: <b>15h</b> (17 pp).\n• Weekly at <b>41%</b>; until the reset there is room for <b>1.5 pp/h</b>.\n• In the last hour: <b>claude-sonnet 71 tok/s</b>, <b>claude-opus 48 tok/s</b>.", TelegramAnalysisTexts.sentences(full, AppLanguage.EN))
    }

    @Test
    fun `trechos opcionais ausentes em PT`() {
        assertEquals("<b>📐 Anthropic · &lt;Padrão&gt; · leitura</b>\n• A 5h está em <b>62%</b> e chega a <b>~83%</b> — <b>não estoura</b>.\n• Ritmo médio da janela <b>17,7 pp/h</b>, <b>13% abaixo</b> da janela anterior no mesmo ponto.\n• Hora mais pesada nas últimas 24 h: <b>15h</b> (17 pp).\n• Semanal em <b>41%</b>.\n• Na última hora: <b>claude-sonnet 71 tok/s</b>, <b>claude-opus 48 tok/s</b>.", TelegramAnalysisTexts.sentences(partial, AppLanguage.PT))
    }

    @Test
    fun `trechos opcionais ausentes em EN`() {
        assertEquals("<b>📐 Anthropic · &lt;Padrão&gt; · reading</b>\n• The 5h window is at <b>62%</b> and reaches <b>~83%</b> — <b>does not run out</b>.\n• Window average pace <b>17.7 pp/h</b>, <b>13% below</b> the previous window at the same point.\n• Heaviest hour in the last 24 h: <b>15h</b> (17 pp).\n• Weekly at <b>41%</b>.\n• In the last hour: <b>claude-sonnet 71 tok/s</b>, <b>claude-opus 48 tok/s</b>.", TelegramAnalysisTexts.sentences(partial, AppLanguage.EN))
    }

    @Test
    fun `sem projecao a frase da 5h termina no percentual`() {
        assertEquals("<b>📐 Anthropic · &lt;Padrão&gt; · leitura</b>\n• A 5h está em <b>62%</b>.\n• Ritmo médio da janela <b>17,7 pp/h</b>, <b>13% abaixo</b> da janela anterior no mesmo ponto.\n• Hora mais pesada nas últimas 24 h: <b>15h</b> (17 pp).\n• Semanal em <b>41%</b>; até o reinício cabem <b>1,5 pp/h</b>.\n• Na última hora: <b>claude-sonnet 71 tok/s</b>, <b>claude-opus 48 tok/s</b>.", TelegramAnalysisTexts.sentences(noProjection, AppLanguage.PT))
    }
}
