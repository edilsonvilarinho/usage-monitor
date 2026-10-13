package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonRow
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
}
