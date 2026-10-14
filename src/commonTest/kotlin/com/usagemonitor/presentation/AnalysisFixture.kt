package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnalysisBucket
import com.usagemonitor.domain.entity.AnalysisDay
import com.usagemonitor.domain.entity.AnalysisIntervalRow
import com.usagemonitor.domain.entity.AnalysisModelSpeed
import com.usagemonitor.domain.entity.AnalysisSpan
import com.usagemonitor.domain.entity.AnalysisWindowComparison
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SessionAnalysis
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Análise de exemplo para os testes das vistas (rodada AB): 12:30 BRT, 5h em 62% reiniciando
 * 14:00 BRT, semanal em 41%; os números da galeria AB.
 */
internal object AnalysisFixture {
    val now: Instant = Instant.parse("2026-10-06T15:30:00Z")

    val analysis = SessionAnalysis(
        accountLabel = "Anthropic · <Padrão>",
        source = ApiSource.ANTHROPIC,
        generatedAt = now,
        fiveHour = AnalysisWindowView("Claude 5h", PeriodType.INTERVAL, 62, now - 3.5.hours, now + 1.5.hours, 14.0, 17.7, 25.3, 83),
        weekly = AnalysisWindowView("Claude 7d", PeriodType.WEEKLY, 41, null, Instant.parse("2026-10-09T22:44:00Z"), 0.9, 0.8, 1.5, 55),
        intervals = listOf(
            AnalysisIntervalRow(AnalysisSpan.HOUR_1, 14.0, 48.0),
            AnalysisIntervalRow(AnalysisSpan.HOURS_3, 16.3, 51.0),
            AnalysisIntervalRow(AnalysisSpan.HOURS_5, 17.7, 50.0),
            AnalysisIntervalRow(AnalysisSpan.HOURS_24, 8.1, 49.0),
            AnalysisIntervalRow(AnalysisSpan.DAYS_7, 7.4, 47.0)
        ),
        hours = (0 until 24).map { index ->
            AnalysisBucket(now - (23 - index).hours, listOf(1.0, 0.0, 17.0, 6.0)[index % 4], if (index % 4 == 1) null else 48.0)
        },
        heat = (0 until 7).map { day -> (0 until 24).map { hour -> if (hour in 9..16 && day != 3) (hour % 5 + day).toDouble() else 0.0 } },
        heatDays = listOf(LocalDate(2026, 9, 30)) + (1..6).map { day -> LocalDate(2026, 10, day) },
        days = (0 until 7).map { day -> AnalysisDay(if (day == 0) LocalDate(2026, 9, 30) else LocalDate(2026, 10, day), listOf(8.2, 11.4, 6.1, 1.2, 0.4, 7.9, 5.8)[day]) },
        slices = (0 until 7).map { index -> AnalysisBucket(now - 3.5.hours + (30 * index).minutes, listOf(6.0, 9.0, 9.0, 9.0, 8.0, 11.0, 10.0)[index], 50.0) },
        previous = AnalysisWindowComparison(3.5.hours.inWholeMilliseconds, 62, 71, 17.7, 20.3, 17.0, 22.0, 50.0, 44.0),
        modelSpeeds = listOf(AnalysisModelSpeed("claude-sonnet", 71.0), AnalysisModelSpeed("claude-opus", 48.0))
    )
}
