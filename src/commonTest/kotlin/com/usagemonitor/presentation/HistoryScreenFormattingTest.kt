package com.usagemonitor.presentation

import androidx.compose.ui.graphics.Color
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsagePeriodComparison
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.MutableOpenCodeHistoryGroup
import com.usagemonitor.presentation.ui.buildGenericHistoryGroups
import com.usagemonitor.presentation.ui.buildOpenCodeHistoryGroups
import com.usagemonitor.presentation.ui.currentUsagePercent
import com.usagemonitor.presentation.ui.deepSeekForecastText
import com.usagemonitor.presentation.ui.deepSeekSeriesSubtitle
import com.usagemonitor.presentation.ui.deepSeekSeriesTitle
import com.usagemonitor.presentation.ui.forecastLabel
import com.usagemonitor.presentation.ui.formatCents
import com.usagemonitor.presentation.ui.formatInstant
import com.usagemonitor.presentation.ui.formatPercentageOfTotal
import com.usagemonitor.presentation.ui.formatQuantity
import com.usagemonitor.presentation.ui.genericHistorySubtitle
import com.usagemonitor.presentation.ui.historyChartOverlays
import com.usagemonitor.presentation.ui.historySeriesDisplaySubtitle
import com.usagemonitor.presentation.ui.historySubtitle
import com.usagemonitor.presentation.ui.historyTitle
import com.usagemonitor.presentation.ui.intervalSummaryLabel
import com.usagemonitor.presentation.ui.lastUpdatedLabel
import com.usagemonitor.presentation.ui.localizedRequests
import com.usagemonitor.presentation.ui.monthlySummaryLabel
import com.usagemonitor.presentation.ui.openCodeHistorySubtitle
import com.usagemonitor.presentation.ui.periodComparisonLabel
import com.usagemonitor.presentation.ui.quotaWindowLabel
import com.usagemonitor.presentation.ui.selectOpenCodeChartSeries
import com.usagemonitor.presentation.ui.sourceLabel
import com.usagemonitor.presentation.ui.unitSuffix
import com.usagemonitor.presentation.ui.weeklySummaryLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val PT = AppLanguage.PT
private val EN = AppLanguage.EN
private val AT = Instant.parse("2026-10-27T15:04:00Z")

private fun series(
    label: String,
    period: PeriodType = PeriodType.INTERVAL,
    unit: UsageUnit = UsageUnit.PERCENTAGE,
    used: Long = 0L
) = UsageHistorySeries(
    quotaLabel = label,
    periodType = period,
    unit = unit,
    points = emptyList(),
    currentDisplayUsed = used,
    currentDisplayTotal = 100L,
    deltaDisplayUsed = 0L,
    averageDisplayConsumptionPerHour = 0.0,
    currentPeriodEndAt = AT,
    forecast = UsageForecast.NoGrowth,
    riskSummary = null
)

class HistoryScreenFormattingTest {

    @Test
    fun `title names the source only when there is no selector`() {
        val name = sourceLabel(ApiSource.CODEX)
        assertEquals("Histórico do $name", historyTitle(ApiSource.CODEX, showSourceSelector = false, language = PT))
        assertEquals("$name history", historyTitle(ApiSource.CODEX, showSourceSelector = false, language = EN))
        assertEquals("Histórico de uso", historyTitle(ApiSource.CODEX, showSourceSelector = true, language = PT))
        assertEquals("Usage history", historyTitle(null, showSourceSelector = false, language = EN))
    }

    @Test
    fun `subtitle follows the kind of source`() {
        assertEquals("Remaining balance, spend in range, and recent trend.", historySubtitle(ApiSource.DEEPSEEK, true, EN))
        assertEquals("Saldo restante, gasto no intervalo e tendência recente.", historySubtitle(ApiSource.DEEPSEEK, true, PT))
        assertEquals("Observed activity per free model across 5h and 7d windows.", historySubtitle(ApiSource.OPENCODE, true, EN))
        assertEquals("Atividade observada por modelo free nas janelas de 5h e 7d.", historySubtitle(ApiSource.KILO, true, PT))
        assertEquals("Trend, average consumption, and forecast by quota.", historySubtitle(ApiSource.ANTHROPIC, true, EN))
        assertEquals("Tendência, consumo médio e previsão por quota.", historySubtitle(null, true, PT))
    }

    @Test
    fun `last snapshot and forecasts in both languages`() {
        assertEquals("Última coleta: —", lastUpdatedLabel(null, PT))
        assertEquals("Last snapshot: —", lastUpdatedLabel(null, EN))
        assertEquals("Last snapshot: ${formatInstant(AT)}", lastUpdatedLabel(AT, EN))
        assertEquals("27/10 12:04 BRT", formatInstant(AT))

        assertEquals("Insufficient data", forecastLabel(UsageForecast.InsufficientData, EN))
        assertEquals("Sem crescimento detectado", forecastLabel(UsageForecast.NoGrowth, PT))
        assertEquals("Window should reset before the limit", forecastLabel(UsageForecast.ResetsBeforeExhaustion, EN))
        assertEquals(
            "Expected to exhaust around 27/10 12:04 BRT",
            forecastLabel(UsageForecast.EstimatedExhaustionAt(AT), EN)
        )
        assertEquals(
            "Esgota por volta de 27/10 12:04 BRT",
            forecastLabel(UsageForecast.EstimatedExhaustionAt(AT), PT)
        )
    }

    @Test
    fun `deepseek series have their own titles and forecast sentence`() {
        val balance = series(DeepSeekQuotaLabels.BALANCE, unit = UsageUnit.CURRENCY_USD)
        val granted = series(DeepSeekQuotaLabels.GRANTED.uppercase(), unit = UsageUnit.CURRENCY_USD)
        val other = series("Outro", unit = UsageUnit.CURRENCY_USD)

        assertEquals("Remaining balance", deepSeekSeriesTitle(balance, EN))
        assertEquals("Crédito gratuito", deepSeekSeriesTitle(granted, PT))
        assertEquals("Free credit", deepSeekSeriesTitle(granted, EN))
        assertEquals("Outro", deepSeekSeriesTitle(other, EN))

        assertEquals("Remaining balance observed over the selected range.", deepSeekSeriesSubtitle(balance, EN))
        assertEquals("Crédito promocional ainda disponível no período.", deepSeekSeriesSubtitle(granted, PT))
        assertEquals("Promotional credit still available in the selected range.", deepSeekSeriesSubtitle(granted, EN))
        assertEquals("Saldo observado neste intervalo.", deepSeekSeriesSubtitle(other, PT))
        assertEquals("Balance observed in this range.", deepSeekSeriesSubtitle(other, EN))

        assertEquals(
            "At the current pace, the balance may run out around 27/10 12:04 BRT.",
            deepSeekForecastText(UsageForecast.EstimatedExhaustionAt(AT), EN)
        )
        // Os outros estados delegam ao rótulo do card genérico (#140).
        assertEquals(forecastLabel(UsageForecast.NoGrowth, EN), deepSeekForecastText(UsageForecast.NoGrowth, EN))
    }

    @Test
    fun `numbers keep their unit and their missing total`() {
        assertEquals("—", currentUsagePercent(5L, 0L))
        assertEquals("33", currentUsagePercent(1L, 3L))
        assertEquals("— %", formatPercentageOfTotal(1.0, 0L))
        assertEquals("50 %", formatPercentageOfTotal(1.0, 2L))
        assertEquals("1.5K requests", localizedRequests(1_500L, EN).replace(',', '.'))
        assertEquals("${formatQuantity(1_500L)} requisições", localizedRequests(1_500L, PT))
        assertEquals("2B", formatQuantity(2_000_000_000L))
        assertEquals("-\$1.05", formatCents(-105L))
        assertEquals("\$0.07", formatCents(7L))
        assertEquals(listOf("tok", "req", "%", "USD"), listOf(UsageUnit.TOKENS, UsageUnit.REQUESTS, UsageUnit.PERCENTAGE, UsageUnit.CURRENCY_USD).map(::unitSuffix))
    }

    @Test
    fun `opencode groups pick the 5h series for 24h and the 7d series otherwise`() {
        val short = series("glm 5h", used = 4L)
        val long = series("glm 7d", PeriodType.WEEKLY, used = 9L)
        val lone = series("kimi", used = 2L)

        val groups = buildOpenCodeHistoryGroups(listOf(short, long, lone), HistoryRange.LAST_24_HOURS)

        assertEquals(listOf("glm"), groups.map { it.modelName })
        assertEquals(short, groups[0].chartSeries)
        assertEquals(4L, groups[0].requests5h)
        assertEquals(9L, groups[0].requests7d)
        assertEquals(
            long,
            selectOpenCodeChartSeries(MutableOpenCodeHistoryGroup("glm", short, long), HistoryRange.LAST_30_DAYS)
        )
        assertEquals(
            short,
            selectOpenCodeChartSeries(MutableOpenCodeHistoryGroup("glm", short, null), HistoryRange.TOTAL)
        )
        // Rótulo sem janela não tem série de gráfico: o grupo some em vez de vir vazio.
        assertEquals(emptyList(), buildOpenCodeHistoryGroups(listOf(lone), HistoryRange.LAST_7_DAYS))
    }

    @Test
    fun `window subtitles cover every period type`() {
        assertEquals("Observed free-model activity in the 5h short window.", openCodeHistorySubtitle(PeriodType.INTERVAL, EN))
        assertEquals("Observed free-model activity in the 7-day weekly window.", openCodeHistorySubtitle(PeriodType.WEEKLY, EN))
        assertEquals("Atividade observada do modelo free na janela mensal.", openCodeHistorySubtitle(PeriodType.MONTHLY, PT))
        assertEquals("Observed free-model activity in the monthly window.", openCodeHistorySubtitle(PeriodType.MONTHLY, EN))
        assertEquals("Janela reportada pela fonte.", openCodeHistorySubtitle(PeriodType.REPORTED, PT))
        assertEquals("Source-reported window.", openCodeHistorySubtitle(PeriodType.REPORTED, EN))

        val labels = PeriodType.entries.map { historySeriesDisplaySubtitle(ApiSource.CODEX, series("x", it), EN) }
        assertEquals(PeriodType.entries.size, labels.toSet().size)
        assertEquals("Quota mensal", historySeriesDisplaySubtitle(ApiSource.CODEX, series("x", PeriodType.MONTHLY), PT))
    }

    @Test
    fun `generic groups merge 5h, weekly and monthly of the same family`() {
        val interval = series("Go 5h")
        val weekly = series("Go semanal", PeriodType.WEEKLY)
        val monthly = series("Go mensal", PeriodType.MONTHLY)
        val onlyWeekly = series("Opus 7d", PeriodType.WEEKLY)
        val onlyMonthly = series("Credits 30d", PeriodType.MONTHLY)
        val reported = series("Janela", PeriodType.REPORTED)

        val groups = buildGenericHistoryGroups(listOf(interval, weekly, monthly, onlyWeekly, onlyMonthly, reported))

        assertEquals(listOf("Go", "Opus", "Credits", "Janela"), groups.map { it.baseLabel })
        assertEquals(interval, groups[0].chartSeries)
        assertEquals(weekly, groups[0].weeklySummary)
        assertEquals(monthly, groups[0].monthlySummary)
        assertEquals(onlyWeekly, groups[1].chartSeries)
        assertNull(groups[1].weeklySummary)
        assertEquals(onlyMonthly, groups[2].chartSeries)
        assertEquals(reported, groups[3].chartSeries)
    }

    @Test
    fun `summary and window labels`() {
        assertEquals("Consumption across the selected range", genericHistorySubtitle(EN))
        assertEquals("Current weekly quota", weeklySummaryLabel(EN))
        assertEquals("Cota intervalar atual", intervalSummaryLabel(PT))
        assertEquals("Current interval quota", intervalSummaryLabel(EN))
        assertEquals("Current monthly quota", monthlySummaryLabel(EN))
        assertEquals("5h", quotaWindowLabel(series("Claude 5h"), EN))
        assertEquals("7d", quotaWindowLabel(series("Claude 7d", PeriodType.WEEKLY), EN))
        assertEquals("Interval", quotaWindowLabel(series("Sessão"), EN))
        assertEquals("Semanal", quotaWindowLabel(series("Semana", PeriodType.WEEKLY), PT))
        assertEquals("Monthly", quotaWindowLabel(series("Mês", PeriodType.MONTHLY), EN))
        assertEquals("Janela", quotaWindowLabel(series("Janela", PeriodType.REPORTED), PT))
    }

    @Test
    fun `overlays only join a percentage chart`() {
        val primary = series("Claude 5h")
        val weekly = series("Claude 7d", PeriodType.WEEKLY)
        val monthlyTokens = series("Claude mensal", PeriodType.MONTHLY, unit = UsageUnit.TOKENS)

        val overlays = historyChartOverlays(weekly, primary, Color.Red, EN, monthlySummary = monthlyTokens, monthlyColor = Color.Blue)

        assertEquals(listOf("7d"), overlays.map { it.label })
        assertEquals(Color.Red, overlays.single().color)
        assertEquals(emptyList(), historyChartOverlays(weekly, primary.copy(unit = UsageUnit.TOKENS), Color.Red, EN))
    }

    @Test
    fun `period comparison is signed and undefined without previous use`() {
        assertEquals("Sem consumo anterior", periodComparisonLabel(UsagePeriodComparison(10L, 0L), PT))
        assertEquals("No previous consumption", periodComparisonLabel(UsagePeriodComparison(10L, 0L), EN))
        assertEquals("+50%", periodComparisonLabel(UsagePeriodComparison(15L, 10L), EN))
        assertEquals("-20%", periodComparisonLabel(UsagePeriodComparison(8L, 10L), EN))
        assertEquals("0%", periodComparisonLabel(UsagePeriodComparison(10L, 10L), EN))
    }
}
