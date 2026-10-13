package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AnalysisViewKind
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotTap
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.domain.entity.data
import kotlin.math.roundToInt

/**
 * Textos da análise da sessão no bot (rodada AB). Toda vista sai no idioma do app; número
 * com uma casa usa vírgula em português e ponto em inglês. `null` (não medido) vira "—".
 */
internal object TelegramAnalysisMessages {

    /** As vistas que já têm texto, imagem ou arquivo; só elas viram aba. */
    val availableViews: List<AnalysisView> = listOf(
        AnalysisView.PANEL, AnalysisView.TREND, AnalysisView.INTERVALS, AnalysisView.SENTENCES, AnalysisView.TIMELINE, AnalysisView.VERSUS,
        AnalysisView.GAUGES
    )

    fun pickAccount(language: AppLanguage): String =
        if (language == AppLanguage.PT) "<b>📐 Análise de qual conta?</b>" else "<b>📐 Analysis of which account?</b>"

    /** Botão "Todas" da escolha de conta: abre o placar (AB13). */
    fun scoreboardButton(language: AppLanguage): String = if (language == AppLanguage.PT) "📊 Todas (placar)" else "📊 All (scoreboard)"

    fun noAccount(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "📐 A análise cobre contas Anthropic e Codex, e nenhuma está com leitura agora."
    } else {
        "📐 The analysis covers Anthropic and Codex accounts, and none has a reading right now."
    }

    fun notReady(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "📐 Sem histórico desta conta ainda. A análise aparece depois de algumas coletas."
    } else {
        "📐 No history for this account yet. The analysis shows up after a few collections."
    }

    /** As abas (AB12): uma por vista disponível, a corrente marcada; três por linha. */
    fun tabs(accountKey: String, current: AnalysisView, language: AppLanguage): List<List<Pair<String, String>>> =
        availableViews.map { view ->
            val name = viewName(view, language)
            (if (view == current) "• $name •" else name) to BotTap.AnalysisShow(accountKey, view).data()
        }.chunked(TABS_PER_ROW)

    fun viewName(view: AnalysisView, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        return when (view) {
            AnalysisView.PANEL -> if (pt) "Painel" else "Panel"
            AnalysisView.TREND -> if (pt) "Tendência" else "Trend"
            AnalysisView.INTERVALS -> if (pt) "Intervalos" else "Intervals"
            AnalysisView.SENTENCES -> if (pt) "Frases" else "Reading"
            AnalysisView.TIMELINE -> if (pt) "Janela 5h" else "5h window"
            AnalysisView.VERSUS -> if (pt) "Antes × agora" else "Before × now"
            AnalysisView.GAUGES -> if (pt) "🖼 Velocímetro" else "🖼 Gauges"
            AnalysisView.HOURS -> if (pt) "🖼 Horas" else "🖼 Hours"
            AnalysisView.HEATMAP -> if (pt) "🖼 Dia × hora" else "🖼 Day × hour"
            AnalysisView.CURVE -> if (pt) "🖼 Curva" else "🖼 Curve"
            AnalysisView.DAYS -> if (pt) "🖼 Dias" else "🖼 Days"
            AnalysisView.CARD -> if (pt) "🖼 Cartão" else "🖼 Card"
            AnalysisView.REPORT -> "📄 PDF"
        }
    }

    /** O texto de uma vista de texto; `null` para vista de imagem ou arquivo, ou ainda sem texto. */
    fun text(view: AnalysisView, analysis: SessionAnalysis, language: AppLanguage): String? {
        if (view.kind != AnalysisViewKind.TEXT || view !in availableViews) return null
        return when (view) {
            AnalysisView.PANEL -> panel(analysis, language)
            AnalysisView.TREND -> TelegramAnalysisTexts.trend(analysis, language)
            AnalysisView.INTERVALS -> TelegramAnalysisTexts.intervals(analysis, language)
            AnalysisView.SENTENCES -> TelegramAnalysisTexts.sentences(analysis, language)
            AnalysisView.TIMELINE -> TelegramAnalysisTexts.timeline(analysis, language)
            AnalysisView.VERSUS -> TelegramAnalysisTexts.versus(analysis, language)
            else -> null
        }
    }

    /**
     * AB1 · painel de bordo: um bloco por janela com consumo, ritmo agora, médio e seguro, e
     * onde ela chega no reinício; a vazão da última hora por modelo no fim.
     */
    fun panel(analysis: SessionAnalysis, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val blocks = listOfNotNull(
            analysis.fiveHour?.let { window -> windowBlock(if (pt) "JANELA 5h" else "5h WINDOW", window, analysis, pt) },
            analysis.weekly?.let { window -> windowBlock(if (pt) "SEMANAL" else "WEEKLY", window, analysis, pt) }
        )
        val speeds = analysis.modelSpeeds.joinToString(" · ") { speed -> "${speed.model} ${speed.tokensPerSecond.roundToInt()}" }
            .ifEmpty { if (pt) "sem medição" else "not measured" }
        val body = (blocks + ((if (pt) "TOKENS/s (1 h)  " else "TOKENS/s (1 h)  ") + speeds)).joinToString("\n\n")
        return header(analysis, pt) + "\n<pre>" + TelegramBotMessages.escape(body) + "</pre>"
    }

    private fun windowBlock(title: String, window: AnalysisWindowView, analysis: SessionAnalysis, pt: Boolean): String {
        val reset = window.resetsAt?.let { at -> (if (pt) "reinicia " else "resets ") + TelegramBotMessages.resetLabel(at, analysis.generatedAt, pt) }.orEmpty()
        val since = window.startsAt?.let { at -> (if (pt) "desde " else "since ") + TelegramBotMessages.clock(at) }.orEmpty()
        val current = window.paceNow ?: window.paceAverage
        val safe = window.safePace
        val verdict = when {
            current == null || safe == null -> ""
            current <= safe -> if (pt) "  → folga" else "  → room left"
            else -> if (pt) "  → acima" else "  → too fast"
        }
        return listOf(
            "${title.padEnd(11)} ${"${window.percent}%".padStart(4)}   $reset".trimEnd(),
            "  " + (if (pt) "agora " else "now   ") + "  ${pace(window.paceNow, pt)} pp/h  " + (if (pt) "(última 1 h)" else "(last 1 h)"),
            "  " + (if (pt) "média " else "avg   ") + "  ${pace(window.paceAverage, pt)} pp/h  ${if (since.isEmpty()) "" else "($since)"}".trimEnd(),
            "  " + (if (pt) "seguro" else "safe  ") + "  ${pace(window.safePace, pt)} pp/h$verdict",
            "  " + (if (pt) "no reset ≈ " else "at reset ≈ ") + (window.projectedAtReset?.let { value -> "$value%" } ?: "—")
        ).joinToString("\n")
    }

    private fun header(analysis: SessionAnalysis, pt: Boolean): String =
        "<b>📐 ${TelegramBotMessages.escape(analysis.accountLabel)}</b> · <i>" + (if (pt) "análise " else "analysis ") +
            TelegramBotMessages.clock(analysis.generatedAt) + " BRT</i>"

    /** Uma casa, vírgula em português; `null` vira "—", nunca zero. */
    fun pace(value: Double?, pt: Boolean): String {
        if (value == null) return "—".padStart(5)
        val tenths = (value * 10).roundToInt()
        val text = "${tenths / 10}${if (pt) "," else "."}${kotlin.math.abs(tenths % 10)}"
        return text.padStart(5)
    }

    private const val TABS_PER_ROW = 3
}
