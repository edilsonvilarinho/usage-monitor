package com.usagemonitor.domain.entity

/**
 * Zona de uma cota pelos limiares de alerta da conta: a palavra de estado do modo
 * terminal (`docs/design-system/cli/readme.md`, "Palavra de estado").
 */
enum class QuotaZone { ON_TRACK, ATTENTION, CRITICAL }

/**
 * Quantos limiares o percentual já alcançou: nenhum é [QuotaZone.ON_TRACK], um é
 * [QuotaZone.ATTENTION], dois ou mais é [QuotaZone.CRITICAL]. É a regra de zonas da
 * barra do Telegram (AE7), lida pelo número em vez de pela célula, para o bot e o
 * terminal nunca discordarem com os mesmos limiares.
 *
 * `null` quando não há o que dizer: cota sem percentual (saldo, contagem) ou conta
 * sem limiar nenhum. Sem limiar, escrever "em dia" afirmaria um estado que o
 * usuário não definiu.
 */
fun quotaZone(percent: Int?, thresholds: List<Int>): QuotaZone? {
    val value = percent ?: return null
    val normalized = normalizeQuotaPercents(thresholds)
    if (normalized.isEmpty()) {
        return null
    }
    return when (normalized.count { threshold -> value >= threshold }) {
        0 -> QuotaZone.ON_TRACK
        1 -> QuotaZone.ATTENTION
        else -> QuotaZone.CRITICAL
    }
}
