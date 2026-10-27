package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.quotaWindowsOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Uma cota da aba 3 com leituras sintéticas de 15 em 15 min nas últimas [since] horas: as janelas
 * fechadas sobem até [closedPeaks] em 70% da duração (100 esgota e fica parada até o reinício),
 * com uma hora ociosa entre elas, e a aberta sobe em linha reta até o percentual de agora. As
 * janelas saem do mesmo corte do domínio ([quotaWindowsOf]) que o app usa.
 */
internal object TuiHistoryFixtures {

    fun row(account: TerminalAccount, quota: UsageSnapshotQuota, closedPeaks: List<Int>, length: Duration, now: Instant, since: Duration = 24.hours): TuiHistoryRow {
        val from = now - since
        val openReset = requireNotNull(quota.resetsAt)
        val points = mutableListOf<UsageHistoryPoint>()
        closedPeaks.forEachIndexed { index, peak ->
            val reset = openReset - (length + 1.hours) * (closedPeaks.size - index)
            val start = reset - length
            var at = start + STEP
            while (at <= reset) {
                val value = minOf(peak.toDouble(), peak * ((at - start) / (length * RISE_SHARE))).toLong()
                if (at >= from) {
                    points += point(at, value, reset)
                }
                at += STEP
            }
        }
        val percent = requireNotNull(quota.percent)
        val start = openReset - length
        var at = maxOf(start, from) + STEP
        while (at < now) {
            points += point(at, (percent * ((at - start) / (now - start))).toLong(), openReset)
            at += STEP
        }
        points += point(now, percent.toLong(), openReset)
        return TuiHistoryRow(account, quota, quotaWindowsOf(points, UsageUnit.PERCENTAGE, quota.periodType), points)
    }

    private fun point(at: Instant, used: Long, reset: Instant) = UsageHistoryPoint(at, used, 100, 0, 0, reset)

    private val STEP = 15.minutes
    private const val RISE_SHARE = 0.7
}
