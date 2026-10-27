package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnalysisBucket
import com.usagemonitor.domain.entity.AnalysisDay
import com.usagemonitor.domain.entity.AnalysisIntervalRow
import com.usagemonitor.domain.entity.AnalysisModelSpeed
import com.usagemonitor.domain.entity.AnalysisSpan
import com.usagemonitor.domain.entity.AnalysisWindowComparison
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PacePathPoint
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaWindowPacePath
import com.usagemonitor.domain.entity.SessionAnalysis
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Análise de exemplo para os testes das vistas (rodada AB): 12:30 BRT, 5h em 62% reiniciando
 * 14:00 BRT, semanal em 41%; os números da galeria AB. Com o caminho de cada janela na diagonal
 * do ritmo (#463) e a 5h anterior atrás.
 */
internal object AnalysisFixture {
    val now: Instant = Instant.parse("2026-10-06T15:30:00Z")

    /** A janela na diagonal do ritmo (#463): (fração do tempo, % usado). */
    private fun path(startsAt: Instant, resetsAt: Instant, vararg points: Pair<Double, Double>) =
        QuotaWindowPacePath(startsAt, resetsAt, points.map { (fraction, percent) -> PacePathPoint(fraction, percent) })

    val analysis = SessionAnalysis(
        accountLabel = "Anthropic · <Padrão>",
        source = ApiSource.ANTHROPIC,
        generatedAt = now,
        fiveHour = AnalysisWindowView(
            "Claude 5h", PeriodType.INTERVAL, 62, now - 3.5.hours, now + 1.5.hours, 14.0, 17.7, 25.3, 83,
            path = path(now - 3.5.hours, now + 1.5.hours, 0.05 to 0.0, 0.2 to 9.0, 0.35 to 21.0, 0.5 to 36.0, 0.6 to 49.0, 0.7 to 62.0),
            previousPaths = listOf(path(now - 8.5.hours, now - 3.5.hours, 0.1 to 0.0, 0.3 to 20.0, 0.5 to 44.0, 0.7 to 71.0, 0.9 to 88.0, 1.0 to 90.0))
        ),
        weekly = AnalysisWindowView(
            "Claude 7d", PeriodType.WEEKLY, 41, Instant.parse("2026-10-02T22:44:00Z"), Instant.parse("2026-10-09T22:44:00Z"), 0.9, 0.8, 1.5, 55,
            path = path(Instant.parse("2026-10-02T22:44:00Z"), Instant.parse("2026-10-09T22:44:00Z"), 0.0 to 0.0, 0.14 to 8.0, 0.28 to 19.0, 0.42 to 30.0, 0.528 to 41.0)
        ),
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
