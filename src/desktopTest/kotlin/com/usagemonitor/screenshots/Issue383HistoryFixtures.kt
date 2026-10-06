package com.usagemonitor.screenshots

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.quotaHourlyDistributionOf
import com.usagemonitor.domain.entity.quotaWindowStatsOf
import com.usagemonitor.domain.entity.quotaWindowsOf
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.UsageHistorySeries
import kotlin.time.Duration.Companion.hours

/** Mesma pressão de texto das capturas do bug, com identificações inteiramente sintéticas. */
internal fun issue383LongHistoryAccounts(source: ApiSource): List<UsageAccountContext> = (1..3).map { index ->
    val email = "developer.account.with.long.name.$index@example.test"
    UsageAccountContext(UsageAccountKey(source, "synthetic-$index", "workspace-$index"), email,
        "$email's Organization — Equipe de desenvolvimento $index")
}

internal fun issue383ManyWindowsSeries(): UsageHistorySeries {
    val base = issue383HistoryReports().first().series.first()
    val windows = (0..11).map { index ->
        val start = ScreenshotFixtures.NOW - (60 - index * 5).hours
        QuotaWindowSummary(start, start + 4.hours, start + 5.hours, 40 + index * 5,
            null, 35.0, 8.75, index == 11)
    }
    return base.copy(windows = windows, windowStats = quotaWindowStatsOf(windows))
}

/** Dados sintéticos para conferir as quatro anatomias atuais do histórico. */
internal fun issue383HistoryReports(): List<ApiUsageHistoryReport> {
    val original = ScreenshotFixtures.historyReport
    val quota = original.copy(series = original.series.map { series ->
        val windows = quotaWindowsOf(series.points, series.unit, series.periodType)
        series.copy(
            windows = windows,
            windowStats = quotaWindowStatsOf(windows),
            hourlyDistribution = quotaHourlyDistributionOf(series.points, series.unit, series.periodType)
        )
    })
    val base = original.series.first()
    val codexAccount = original.accountContext?.copy(key = original.accountContext.key.copy(source = ApiSource.CODEX))
    val reported = original.copy(source = ApiSource.CODEX, accountContext = codexAccount, series = listOf(base.copy(
        quotaLabel = "Janela reportada",
        periodType = PeriodType.REPORTED,
        averageDisplayConsumptionPerHour = 0.0,
        forecast = UsageForecast.InsufficientData,
        riskSummary = null
    )))
    val balance = original.copy(source = ApiSource.DEEPSEEK, accountContext = null, series = listOf(base.copy(
        quotaLabel = DeepSeekQuotaLabels.BALANCE,
        unit = UsageUnit.CURRENCY_USD,
        currentDisplayUsed = 1234,
        currentDisplayTotal = 0,
        deltaDisplayUsed = 230,
        averageDisplayConsumptionPerHour = 10.0,
        points = base.points.mapIndexed { index, point -> point.copy(used = 1464L - index * 10, total = 0, periodEndAt = ScreenshotFixtures.NOW, hasKnownResetAt = false) },
        riskSummary = null
    )))
    val observed = original.copy(source = ApiSource.OPENCODE, accountContext = null, series =
        (1..8).flatMap { model -> listOf(PeriodType.INTERVAL, PeriodType.WEEKLY).map { period ->
            val weekly = period == PeriodType.WEEKLY
            val factor = if (weekly) 4 else 1
            base.copy(
                quotaLabel = "modelo-local-$model ${if (weekly) "7d" else "5h"}",
                periodType = period,
                unit = UsageUnit.REQUESTS,
                points = base.points.mapIndexed { index, point -> point.copy(used = (index + 1L) * model * factor, total = 0, periodEndAt = ScreenshotFixtures.NOW, hasKnownResetAt = false) },
                currentDisplayUsed = 24L * model * factor,
                currentDisplayTotal = 0,
                deltaDisplayUsed = 23L * model * factor,
                averageDisplayConsumptionPerHour = model.toDouble() * factor,
                forecast = UsageForecast.InsufficientData,
                riskSummary = null
            )
        } }
    )
    return listOf(quota, reported, balance, observed)
}
