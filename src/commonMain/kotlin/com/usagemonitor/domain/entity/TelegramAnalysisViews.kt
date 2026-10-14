package com.usagemonitor.domain.entity

/**
 * As vistas da análise da sessão (rodada AB). [code] vai no `callback_data` do botão
 * (`anv:<conta>:<code>`), por isso curto e estável; [kind] diz se a vista edita a
 * mensagem no lugar (texto, AB12), manda imagem ou manda arquivo.
 */
enum class AnalysisView(val code: String, val kind: AnalysisViewKind) {
    PANEL("pn", AnalysisViewKind.TEXT),
    TREND("tr", AnalysisViewKind.TEXT),
    INTERVALS("iv", AnalysisViewKind.TEXT),
    SENTENCES("fr", AnalysisViewKind.TEXT),
    TIMELINE("tl", AnalysisViewKind.TEXT),
    VERSUS("vs", AnalysisViewKind.TEXT),
    GAUGES("gg", AnalysisViewKind.IMAGE),
    HOURS("hr", AnalysisViewKind.IMAGE),
    HEATMAP("hm", AnalysisViewKind.IMAGE),
    CURVE("cv", AnalysisViewKind.IMAGE),
    DAYS("dy", AnalysisViewKind.IMAGE),
    CARD("cd", AnalysisViewKind.IMAGE),
    REPORT("pd", AnalysisViewKind.FILE)
}

enum class AnalysisViewKind { TEXT, IMAGE, FILE }

/** Fontes com janela 5h e semanal em percentual: as únicas que a análise cobre. */
val ANALYSIS_SOURCES: Set<ApiSource> = setOf(ApiSource.ANTHROPIC, ApiSource.CODEX)

/**
 * Os argumentos do `/analise`, em qualquer ordem: a fonte (`claude`, `codex`), `todas` para o
 * placar, e a vista (`horas`, `img`, `pdf`…). Palavra desconhecida é uso inválido.
 */
internal fun parseAnalysis(args: List<String>): BotCommand {
    var view: AnalysisView? = null
    var source: ApiSource? = null
    var all = false
    val words = args.map { arg -> arg.lowercase() }
    for (word in words) {
        when (word) {
            "todas", "all", "placar", "scoreboard" -> all = true
            "anthropic", "claude" -> source = ApiSource.ANTHROPIC
            "codex" -> source = ApiSource.CODEX
            "5h" -> view = if ("img" in words || "imagem" in words) AnalysisView.CURVE else AnalysisView.TIMELINE
            "img", "imagem", "image" -> if (view == null) view = AnalysisView.GAUGES
            else -> view = ANALYSIS_WORDS[word] ?: return BotCommand.Invalid("/analise | /analise codex | /analise horas | /analise pdf")
        }
    }
    return BotCommand.Analysis(view, source, all)
}

private val ANALYSIS_WORDS: Map<String, AnalysisView> = mapOf(
    "painel" to AnalysisView.PANEL, "panel" to AnalysisView.PANEL,
    "tendencia" to AnalysisView.TREND, "tendência" to AnalysisView.TREND, "trend" to AnalysisView.TREND,
    "intervalos" to AnalysisView.INTERVALS, "intervals" to AnalysisView.INTERVALS,
    "frases" to AnalysisView.SENTENCES, "leitura" to AnalysisView.SENTENCES, "sentences" to AnalysisView.SENTENCES,
    "janela" to AnalysisView.TIMELINE, "timeline" to AnalysisView.TIMELINE,
    "comparar" to AnalysisView.VERSUS, "antes" to AnalysisView.VERSUS, "versus" to AnalysisView.VERSUS, "compare" to AnalysisView.VERSUS,
    "velocimetro" to AnalysisView.GAUGES, "velocímetro" to AnalysisView.GAUGES, "gauges" to AnalysisView.GAUGES,
    "horas" to AnalysisView.HOURS, "hours" to AnalysisView.HOURS,
    "semana" to AnalysisView.HEATMAP, "calor" to AnalysisView.HEATMAP, "heatmap" to AnalysisView.HEATMAP, "week" to AnalysisView.HEATMAP,
    "curva" to AnalysisView.CURVE, "curve" to AnalysisView.CURVE,
    "dias" to AnalysisView.DAYS, "7d" to AnalysisView.DAYS, "days" to AnalysisView.DAYS,
    "cartao" to AnalysisView.CARD, "cartão" to AnalysisView.CARD, "card" to AnalysisView.CARD,
    "pdf" to AnalysisView.REPORT, "relatorio" to AnalysisView.REPORT, "relatório" to AnalysisView.REPORT, "report" to AnalysisView.REPORT
)
