package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.TelegramAccountHistory
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsagePeriodComparison
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.time.Instant
import com.usagemonitor.presentation.ui.TelegramBotUsageMessages
import kotlin.test.Test
import kotlin.test.assertEquals

/** Leituras do índice e do histórico no bot (#408). */
class TelegramBotUsageMessagesTest {

    private fun row(source: ApiSource, label: String, turns: Int?, throughput: OutputThroughput?) = ComparisonRow(
        source = source, label = label, turnCount = turns, totalTokens = null, costMicros = null, cacheRate = null,
        throughput = throughput, quotaPercent = null
    )

    @Test
    fun `throughput lists measured models fastest first and skips the unmeasured`() {
        val rows = listOf(
            row(ApiSource.ANTHROPIC, "claude-opus-5-5", 212, OutputThroughput(outputTokens = 48_000, generationMillis = 1_000_000)),
            row(ApiSource.CODEX, "gpt-5-codex", 88, OutputThroughput(outputTokens = 57_000, generationMillis = 1_000_000)),
            row(ApiSource.MINIMAX, "MiniMax", null, null)
        )

        assertEquals(
            "<b>⚡ Vazão de saída — 24 h</b>\n" +
                "<b>Codex · gpt-5-codex</b> — <b>57 tok/s</b> · 88 turnos\n" +
                "<b>Anthropic · claude-opus-5-5</b> — <b>48 tok/s</b> · 212 turnos\n" +
                "<i>ponta a ponta: do pedido à última linha</i>",
            TelegramBotUsageMessages.throughput(rows, AppLanguage.PT)
        )
        assertEquals("⚡ Sem vazão medida nas últimas 24 h.", TelegramBotUsageMessages.throughput(listOf(rows[2]), AppLanguage.PT))
        assertEquals("Não deu para ler o índice das sessões CLI agora.", TelegramBotUsageMessages.throughput(null, AppLanguage.PT))
    }

    @Test
    fun `week compares the summed quota points with the previous week`() {
        val histories = listOf(
            history(
                "Anthropic · Padrão",
                series("Semanal", PeriodType.WEEKLY, UsagePeriodComparison(currentDelta = 45, previousDelta = 27)),
                series("5h", PeriodType.INTERVAL, UsagePeriodComparison(currentDelta = 120, previousDelta = 120)),
                series("Sem anterior", PeriodType.INTERVAL, null)
            ),
            history("Codex", series("7d", PeriodType.WEEKLY, UsagePeriodComparison(currentDelta = 13, previousDelta = 22)))
        )

        assertEquals(
            "<b>📅 Últimos 7 dias × 7 anteriores</b>\n" +
                "<b>Anthropic · Padrão · Semanal</b> +45 pp (antes +27 pp) ▲ +18 pp\n" +
                "<b>Anthropic · Padrão · 5h</b> +120 pp (antes +120 pp) =\n" +
                "<b>Codex · 7d</b> +13 pp (antes +22 pp) ▼ −9 pp\n" +
                "<i>pontos da cota somados; reinício conta zero</i>",
            TelegramBotUsageMessages.week(histories, AppLanguage.PT)
        )
        assertEquals("📅 Ainda não há duas semanas de histórico para comparar.", TelegramBotUsageMessages.week(emptyList(), AppLanguage.PT))
    }

    @Test
    fun `windows lists the latest short windows newest first with peak, exhaustion and active span`() {
        fun window(first: String, reset: String, peak: Int, exhausted: String? = null, from: String? = null, until: String? = null, open: Boolean = false) =
            QuotaWindowSummary(
                firstObservedAt = Instant.parse(first), lastObservedAt = Instant.parse(first), resetsAt = Instant.parse(reset),
                peakPercent = peak, exhaustedAt = exhausted?.let(Instant::parse), consumedPercent = peak.toDouble(),
                averagePercentPerHour = null, isOpen = open, activeFrom = from?.let(Instant::parse), activeUntil = until?.let(Instant::parse)
            )
        val short = series("5h", PeriodType.INTERVAL, null).copy(
            windows = listOf(
                window("2026-10-13T09:00:00Z", "2026-10-13T14:00:00Z", 50, from = "2026-10-13T09:05:00Z", until = "2026-10-13T10:10:00Z"),
                window("2026-10-13T14:00:00Z", "2026-10-13T19:00:00Z", 97, exhausted = "2026-10-13T18:30:00Z", open = true)
            )
        )

        assertEquals(
            "<b>🪟 Janelas de 5 h</b>\n\n" +
                "<b>Anthropic · Padrão · 5h</b>\n" +
                "<code>13/10 11:00–16:00</code> pico 97% · esgotou 15:30 · aberta\n" +
                "<code>13/10 06:00–11:00</code> pico 50% · ativa 1 h 5 min",
            TelegramBotUsageMessages.windows(listOf(history("Anthropic · Padrão", short)), weekly = false, language = AppLanguage.PT)
        )
        assertEquals("🪟 Sem janelas semanais no histórico ainda.", TelegramBotUsageMessages.windows(listOf(history("Anthropic · Padrão", short)), weekly = true, language = AppLanguage.PT))
    }

    private val now = Instant.parse("2026-10-13T17:00:00Z")

    private fun series(label: String, periodType: PeriodType, comparison: UsagePeriodComparison?) = UsageHistorySeries(
        quotaLabel = label, periodType = periodType, unit = UsageUnit.PERCENTAGE, points = emptyList(),
        currentDisplayUsed = 0, currentDisplayTotal = 100, deltaDisplayUsed = 0, averageDisplayConsumptionPerHour = 0.0,
        currentPeriodEndAt = now, forecast = UsageForecast.InsufficientData, riskSummary = null, comparison = comparison
    )

    private fun history(label: String, vararg series: UsageHistorySeries) =
        TelegramAccountHistory(label, ApiUsageHistoryReport(ApiSource.ANTHROPIC, HistoryRange.LAST_7_DAYS, now, series.toList()))
}
