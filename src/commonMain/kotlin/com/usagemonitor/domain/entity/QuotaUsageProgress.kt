package com.usagemonitor.domain.entity

import kotlin.time.Instant

/**
 * Quanto tempo um alvo continua marcado como "uso detectado" depois da última
 * leitura em que o consumo avançou (#385).
 *
 * O dobro do polling ocioso (5 min): a leitura seguinte chega no máximo um
 * intervalo depois, e um prazo igual ao intervalo faria o sinal apagar e
 * reacender entre duas leituras do mesmo uso contínuo.
 */
const val USAGE_DETECTED_WINDOW_MILLIS = 10 * 60 * 1_000L

/**
 * O consumo avançou entre duas leituras do mesmo alvo.
 *
 * Fontes sem CLI local (MiniMax, DeepSeek, OpenCode Go, Cursor, Antigravity…)
 * não têm arquivo que diga "trabalhando agora"; o único sinal é a cota mudar.
 * Compara cota a cota pela identidade (rótulo + período) e só dentro da mesma
 * janela: depois de um reinício o `used` cai, e comparar janelas diferentes
 * leria o reinício como ausência — ou, ao contrário, a primeira leitura da
 * janela nova como consumo.
 *
 * Saldo pré-pago (sem reinício conhecido, valor em dinheiro) avança quando o
 * restante **cai**: a fonte informa o saldo, não o gasto. Recarga aumenta o
 * restante e não conta.
 */
fun usageProgressed(previous: ApiUsageStats, current: ApiUsageStats): Boolean {
    val previousByKey = previous.quotas.associateBy { quota -> quota.label to quota.periodType }
    return current.quotas.any { quota ->
        val before = previousByKey[quota.label to quota.periodType] ?: return@any false
        if (!sameWindow(before, quota)) {
            return@any false
        }
        if (isPrepaidBalance(quota)) {
            quota.remaining < before.remaining
        } else {
            quota.used > before.used
        }
    }
}

/** Alvos cujo último avanço de consumo ainda está dentro de [windowMillis]; ordem estável. */
fun <K> recentlyProgressed(
    lastProgressAt: Map<K, Instant>,
    now: Instant,
    windowMillis: Long = USAGE_DETECTED_WINDOW_MILLIS
): Set<K> {
    return lastProgressAt
        .filterValues { at -> now.toEpochMilliseconds() - at.toEpochMilliseconds() < windowMillis }
        .keys
}

private fun sameWindow(before: QuotaInfo, after: QuotaInfo): Boolean {
    if (!before.hasKnownResetAt || !after.hasKnownResetAt) {
        return before.hasKnownResetAt == after.hasKnownResetAt
    }
    return isSamePeriod(before.periodEndAt, after.periodEndAt)
}

private fun isPrepaidBalance(quota: QuotaInfo): Boolean {
    return !quota.hasKnownResetAt && quota.unit == UsageUnit.CURRENCY_USD
}
