package com.usagemonitor

import androidx.compose.ui.unit.Dp
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount

/**
 * As contas que a HUD vertical recolhe sozinha (issue #596): com a coluna completa
 * acima do teto da borda, a conta de **menor uso** vira minimizada, uma por vez,
 * até a coluna caber. Uso é o maior percentual das cotas; sem cota, zero. No
 * empate sai a primeira da lista.
 *
 * Vazio na borda horizontal, quando a coluna completa já cabe e quando nem com
 * todas minimizadas ela cabe — aí o compacto de sempre continua valendo. As já
 * minimizadas (pelo usuário ou pela regra) entram na altura, mas não no resultado;
 * a preferência do usuário não é tocada aqui.
 */
internal fun hudAutoMinimizedTargets(
    accounts: List<HudAccount>,
    edge: HudEdge,
    fallbackLabel: String,
    maxAlong: Dp
): Set<UsageTargetKey> {
    if (edge.isHorizontal) return emptySet()
    fun fits(column: List<HudAccount>): Boolean =
        verticalCollapsed(column, fallbackLabel, compact = false).height <= maxAlong
    if (fits(accounts)) return emptySet()
    // sortedBy é estável: no empate a ordem da lista fica como está.
    val candidates = accounts
        .filterNot { account -> account.minimized }
        .sortedBy { account -> account.quotas.maxOfOrNull { quota -> quota.fraction } ?: 0f }
    val chosen = mutableSetOf<UsageTargetKey>()
    for (candidate in candidates) {
        chosen += candidate.targetKey
        val trial = accounts.map { account ->
            if (account.targetKey in chosen) account.copy(minimized = true) else account
        }
        if (fits(trial)) return chosen
    }
    return emptySet()
}
