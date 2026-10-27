package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.quotaWindowLengthMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * O tempo da janela de uma cota (#463, AI5): o início nominal (reinício − 5 h ou 7 d, a mesma
 * regra da diagonal do app) e quanto dele já passou. Uma fonte só para o `status`, a linha de
 * status, o `--json` e o gráfico da aba 3: o mesmo número nos quatro lugares.
 */
internal data class TerminalQuotaTime(
    val startsAt: Instant,
    val resetsAt: Instant,
    val length: Duration,
    val elapsed: Duration
) {
    /** Do tempo da janela, quanto já passou, de 0 a 100, pelo piso, como a barra de cota. */
    val elapsedPercent: Int
        get() = (elapsed.inWholeMilliseconds * 100 / length.inWholeMilliseconds).toInt()

    /** A cota usada menos o tempo que passou, em pontos: positivo esgota antes do reinício. */
    fun gap(percent: Int): Int = percent - elapsedPercent

    companion object {
        /**
         * `null` sem duração nominal (a intervalar do MiniMax, a mensal), sem reinício ou com a
         * janela já vencida ("reiniciando"): sem início e fim não há tempo para comparar.
         */
        fun of(quota: UsageSnapshotQuota, now: Instant): TerminalQuotaTime? {
            val lengthMillis = quotaWindowLengthMillis(quota.label, quota.periodType) ?: return null
            val resetsAt = quota.resetsAt?.takeIf { at -> at > now } ?: return null
            val length = lengthMillis.milliseconds
            val startsAt = resetsAt - length
            val elapsed = (now - startsAt).coerceIn(Duration.ZERO, length)
            return TerminalQuotaTime(startsAt, resetsAt, length, elapsed)
        }
    }
}
