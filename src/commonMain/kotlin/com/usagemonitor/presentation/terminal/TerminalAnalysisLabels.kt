package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/**
 * Os textos da análise no terminal (#463): a aba 6 (AI2), a corrida esgotar × reiniciar (AI3) e o
 * comando `analysis`. O veredito e o pico das 24 h vêm do `AnalysisPanelModel` do app, para o
 * terminal dizer o mesmo que a janela "Análise".
 */
internal class TerminalAnalysisLabels(val language: AppLanguage) {

    fun windowName(weekly: Boolean): String = if (weekly) localized(language, "semanal", "weekly") else "5h"
    fun windowHeader(weekly: Boolean): String = if (weekly) localized(language, "JANELA SEMANAL", "WEEKLY WINDOW") else localized(language, "JANELA 5H", "5H WINDOW")
    fun used(percent: Int): String = localized(language, "$percent% usada", "$percent% used")

    fun ruler(span: String): String = localized(language, "régua: agora até +$span", "ruler: now to +$span")
    val resets: String get() = localized(language, "reinicia", "resets")
    val runsOut: String get() = localized(language, "esgota", "runs out")

    fun exhaustAt(duration: String, clock: String, lastHour: Boolean): String =
        "$duration · ~$clock " + pace(lastHour)

    fun beyond(length: String, lastHour: Boolean): String = localized(language, "mais de $length ", "more than $length ") + pace(lastHour)

    private fun pace(lastHour: Boolean): String =
        if (lastHour) localized(language, "no ritmo da última hora", "at the last hour's pace") else localized(language, "no ritmo médio", "at the average pace")

    val noPace: String get() = localized(language, "sem ritmo medido", "no pace measured")
    val stalled: String get() = localized(language, "parado: não esgota neste ritmo", "idle: does not run out at this pace")
    val alreadyOut: String get() = localized(language, "já esgotada", "already ran out")
    val noReset: String get() = localized(language, "sem reinício conhecido", "no known reset")

    val paceLastHour: String get() = localized(language, "ritmo na última hora", "last hour's pace")
    val paceAverage: String get() = localized(language, "ritmo médio", "average pace")
    val toFit: String get() = localized(language, "para caber", "to fit")
    val currentPaceFits: String get() = localized(language, "o ritmo de agora basta", "current pace fits")
    fun atMost(pace: String): String = localized(language, "no máximo $pace", "at most $pace")

    val last24h: String get() = localized(language, "ÚLTIMAS 24 H", "LAST 24 H")
    val last24hNote: String get() = localized(language, "% da cota 5h gasta em cada hora", "% of the 5h quota spent each hour")
    val tokensPerSecond: String get() = "tok/s"
    fun maxTokens(value: Long): String = localized(language, "máx $value", "max $value")
    val noThroughput: String get() = localized(language, "sem vazão medida", "no throughput measured")
    fun lastHourModel(speed: Long, model: String): String =
        localized(language, "tok/s na última hora: $speed ($model)", "tok/s in the last hour: $speed ($model)")

    fun accountOf(index: Int, count: Int): String = localized(language, "conta ${index + 1} de $count", "account ${index + 1} of $count")
    val keyAccount: String get() = localized(language, "conta", "account")
    val keyScroll: String get() = localized(language, "rolar", "scroll")

    val noAccounts: String
        get() = localized(language, "Nenhuma conta com análise: só o Claude Code e o Codex têm.", "No account with analysis: only Claude Code and Codex have one.")
    val noHistory: String get() = localized(language, "Sem histórico lido para esta conta.", "No history read for this account.")
    fun noMatch(filter: String): String =
        localized(language, "Nenhuma conta com análise corresponde a '$filter'.", "No account with analysis matches '$filter'.")
}
