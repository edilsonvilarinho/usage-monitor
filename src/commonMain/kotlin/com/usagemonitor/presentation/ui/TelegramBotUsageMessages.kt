package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.TelegramAccountHistory
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.displayName
import kotlin.math.roundToInt

/**
 * Leituras do índice CLI e do histórico no bot (#408): vazão, semana, janelas e
 * a comparação. Mesmo HTML e mesmo escape de [TelegramBotMessages].
 */
internal object TelegramBotUsageMessages {

    /**
     * `/vazao` (AA5): tokens de saída por segundo por modelo, **ponta a ponta** — a
     * vazão do conjunto é total sobre total (`OutputThroughput`), nunca média de
     * razões. [rows] `null` é índice indisponível; linha sem vazão medida fica fora.
     */
    fun throughput(rows: List<ComparisonRow>?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (rows == null) {
            return if (pt) "Não deu para ler o índice das sessões CLI agora." else "Could not read the CLI session index right now."
        }
        val measured = rows
            .mapNotNull { row -> row.throughput?.tokensPerSecond?.let { speed -> row to speed } }
            .sortedWith(compareByDescending<Pair<ComparisonRow, Double>> { (_, speed) -> speed }.thenBy { (row, _) -> row.source.ordinal }.thenBy { (row, _) -> row.label })
        if (measured.isEmpty()) {
            return if (pt) "⚡ Sem vazão medida nas últimas 24 h." else "⚡ No throughput measured in the last 24 h."
        }
        val lines = measured.map { (row, speed) ->
            val turns = row.turnCount?.let { count -> " · $count " + if (pt) "turnos" else "turns" }.orEmpty()
            "<b>${TelegramBotMessages.escape(row.source.displayName(language))} · ${TelegramBotMessages.escape(row.label)}</b> — " +
                "<b>${speed.roundToInt()} tok/s</b>$turns"
        }
        val header = if (pt) "<b>⚡ Vazão de saída — 24 h</b>" else "<b>⚡ Output throughput — 24 h</b>"
        val footer = if (pt) "<i>ponta a ponta: do pedido à última linha</i>" else "<i>end to end: from the request to the last line</i>"
        return (listOf(header) + lines + footer).joinToString("\n")
    }

    /**
     * `/semana` (AA7): o consumo dos últimos 7 dias contra os 7 anteriores, por cota,
     * em pontos somados (o `UsagePeriodComparison` do histórico: reinício conta zero,
     * nunca delta negativo). Seta **e** número; saldo em dinheiro fica fora.
     */
    fun week(histories: List<TelegramAccountHistory>, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val lines = histories.flatMap { history ->
            history.report.series.mapNotNull { series ->
                val comparison = series.comparison ?: return@mapNotNull null
                val total = series.currentDisplayTotal
                if (total <= 0L || series.unit == UsageUnit.CURRENCY_USD) return@mapNotNull null
                val current = comparison.currentDelta * 100 / total
                val previous = comparison.previousDelta * 100 / total
                val diff = current - previous
                val trend = when {
                    diff > 0 -> "▲ +$diff pp"
                    diff < 0 -> "▼ −${-diff} pp"
                    else -> "="
                }
                val name = "<b>${TelegramBotMessages.escape(history.label)} · ${TelegramBotMessages.escape(series.quotaLabel)}</b>"
                if (pt) "$name +$current pp (antes +$previous pp) $trend" else "$name +$current pp (before +$previous pp) $trend"
            }
        }
        if (lines.isEmpty()) {
            return if (pt) "📅 Ainda não há duas semanas de histórico para comparar." else "📅 There are not two weeks of history to compare yet."
        }
        val header = if (pt) "<b>📅 Últimos 7 dias × 7 anteriores</b>" else "<b>📅 Last 7 days × the 7 before</b>"
        val footer = if (pt) "<i>pontos da cota somados; reinício conta zero</i>" else "<i>quota points added up; a reset counts zero</i>"
        return (listOf(header) + lines + footer).joinToString("\n")
    }
}
