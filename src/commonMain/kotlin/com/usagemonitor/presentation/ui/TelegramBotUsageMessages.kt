package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.TelegramAccountHistory
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.displayName
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
     * `/janelas` (AA12): as últimas janelas de cada conta — pico, se esgotou e quanto
     * tempo o uso ficou subindo (faixa ativa, #382). [weekly] escolhe as janelas
     * semanais; sem ele, as curtas (5 h). Mesmas `QuotaWindowSummary` do Histórico.
     */
    fun windows(histories: List<TelegramAccountHistory>, weekly: Boolean, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val period = if (weekly) PeriodType.WEEKLY else PeriodType.INTERVAL
        val blocks = histories.flatMap { history ->
            history.report.series.filter { series -> series.periodType == period && series.windows.isNotEmpty() }.map { series ->
                val recent = series.windows.sortedByDescending { window -> window.resetsAt }.take(MAX_WINDOWS)
                val title = "<b>${TelegramBotMessages.escape(history.label)} · ${TelegramBotMessages.escape(series.quotaLabel)}</b>"
                title + "\n" + recent.joinToString("\n") { window -> windowLine(window, pt) }
            }
        }
        if (blocks.isEmpty()) {
            val which = if (weekly) (if (pt) "semanais" else "weekly") else (if (pt) "de 5 h" else "5 h")
            return if (pt) "🪟 Sem janelas $which no histórico ainda." else "🪟 No $which windows in the history yet."
        }
        val header = if (weekly) {
            if (pt) "<b>🪟 Janelas semanais</b>" else "<b>🪟 Weekly windows</b>"
        } else {
            if (pt) "<b>🪟 Janelas de 5 h</b>" else "<b>🪟 5 h windows</b>"
        }
        return header + "\n\n" + blocks.joinToString("\n\n")
    }

    private fun windowLine(window: QuotaWindowSummary, pt: Boolean): String {
        val range = "<code>${dayClock(window.firstObservedAt, pt)}–${TelegramBotMessages.clock(window.resetsAt)}</code>"
        val peak = (if (pt) " pico " else " peak ") + "${window.peakPercent}%"
        val exhausted = window.exhaustedAt?.let { at -> (if (pt) " · esgotou " else " · ran out ") + TelegramBotMessages.clock(at) }.orEmpty()
        val from = window.activeFrom
        val until = window.activeUntil
        val active = if (from != null && until != null && until > from) {
            (if (pt) " · ativa " else " · active ") + TelegramBotInsightMessages.span(until - from)
        } else {
            ""
        }
        val open = if (window.isOpen) (if (pt) " · aberta" else " · open") else ""
        return "$range$peak$exhausted$active$open"
    }

    /** "13/10 11:00": o dia ajuda a separar janelas de dias diferentes com a mesma hora. */
    private fun dayClock(at: Instant, pt: Boolean): String {
        val local = at.toLocalDateTime(SAO_PAULO)
        val day = local.date.dayOfMonth.toString().padStart(2, '0')
        val month = local.date.monthNumber.toString().padStart(2, '0')
        val date = if (pt) "$day/$month" else "$month/$day"
        return "$date ${TelegramBotMessages.clock(at)}"
    }

    /**
     * Sparkline do `/status` (AA13) de cada conta e cota com histórico nas últimas 6 h:
     * rótulo da conta → rótulo da cota → linha. Saldo em dinheiro fica fora.
     */
    fun sparklines(histories: List<TelegramAccountHistory>, now: Instant): Map<String, Map<String, String>> =
        histories.associate { history ->
            history.label to history.report.series
                .filter { series -> series.unit != UsageUnit.CURRENCY_USD }
                .mapNotNull { series -> sparkline(series.points, now)?.let { line -> series.quotaLabel to line } }
                .toMap()
        }.filterValues { lines -> lines.isNotEmpty() }

    /**
     * Doze blocos de 30 min nas últimas 6 h, cada um com o uso da última leitura até o
     * fim dele (a linha não inventa ponto entre leituras). Antes da primeira leitura, um
     * espaço. `null` com menos de duas leituras na janela: não há tendência a mostrar.
     * O número continua escrito ao lado; a linha só dá a forma.
     */
    fun sparkline(points: List<UsageHistoryPoint>, now: Instant): String? {
        val start = now - SPARK_SPAN
        val ordered = points.sortedBy { point -> point.capturedAt }
        if (ordered.count { point -> point.capturedAt > start && point.capturedAt <= now } < 2) return null
        return (1..SPARK_BUCKETS).joinToString("") { index ->
            val end = start + SPARK_BUCKET * index
            val last = ordered.lastOrNull { point -> point.capturedAt <= end }
            if (last == null) {
                " "
            } else {
                val percent = (last.normalizedUsage * 100f).toInt()
                SPARK_BLOCKS[(percent * (SPARK_BLOCKS.length - 1) / 100).coerceIn(0, SPARK_BLOCKS.length - 1)].toString()
            }
        }
    }

    private const val SPARK_BLOCKS = "▁▂▃▄▅▆▇█"
    private const val SPARK_BUCKETS = 12
    private val SPARK_BUCKET = 30.minutes
    private val SPARK_SPAN = 6.hours

    /** Legenda da imagem do `/comparar` (AA6): o recorte e a regra da cor. */
    fun compareCaption(days: Int, language: AppLanguage): String =
        if (language == AppLanguage.PT) {
            "<b>Comparação · últimos $days ${if (days == 1) "dia" else "dias"}</b>\n<i>cor: valor sobre o maior da coluna; o número está sempre escrito</i>"
        } else {
            "<b>Comparison · last $days ${if (days == 1) "day" else "days"}</b>\n<i>color: value over the column maximum; the number is always written</i>"
        }

    /** Legenda do `/status img` (AA14): a hora da coleta, como o `/status`. */
    fun ringsCaption(collectedAt: Instant?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val collected = collectedAt?.let { at -> (if (pt) "coleta " else "collected ") + TelegramBotMessages.clock(at) + " BRT" }
            ?: if (pt) "sem coleta ainda" else "not collected yet"
        return "<b>📊 Usage Monitor</b>\n<i>$collected</i>"
    }

    fun ringsEmpty(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Nenhuma conta com cota em percentual para desenhar. Use /status." else "No account with a percentage quota to draw. Use /status."

    fun compareEmpty(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Nenhum uso no recorte para comparar." else "No usage in range to compare."

    private const val MAX_WINDOWS = 4
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

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
