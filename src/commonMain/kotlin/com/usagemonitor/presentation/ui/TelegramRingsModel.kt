package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.presentation.ui.components.riskLevelLabel
import kotlin.time.Instant

/** Um arco do anel: percentual (0–100) e o risco que dá a cor; `null` é neutro. */
data class TelegramRingArc(val percent: Int, val risk: UsageRiskLevel?)

/**
 * Um anel por conta no `/status img` (#408, AA14), na regra da HUD: até três arcos,
 * a janela mais longa por fora, o maior percentual no centro e a palavra do pior
 * risco embaixo — cor nunca informa sozinha.
 */
data class TelegramRing(val label: String, val centerPercent: Int, val riskWord: String?, val arcs: List<TelegramRingArc>)

data class TelegramRings(val rings: List<TelegramRing>, val collectedAt: Instant?)

/** Teto de arcos por anel, o mesmo da HUD. */
const val TELEGRAM_RING_MAX_ARCS = 3

/**
 * Só cota em percentual vira arco; conta sem nenhuma (saldo) fica fora da imagem —
 * o `/status` em texto continua mostrando o saldo.
 */
fun buildTelegramRings(snapshot: UsageSnapshot?, language: AppLanguage): TelegramRings {
    val rings = snapshot?.accounts.orEmpty().mapNotNull { account ->
        val windows = account.quotas.filter { quota -> quota.percent != null }
        if (windows.isEmpty()) return@mapNotNull null
        // Janela mais longa por fora: reinício mais distante primeiro; sem reinício, por último.
        val ordered = windows.sortedWith(compareByDescending<UsageSnapshotQuota> { quota -> quota.resetsAt ?: Instant.DISTANT_PAST }.thenBy { quota -> quota.label })
        val arcs = ordered.take(TELEGRAM_RING_MAX_ARCS).map { quota -> TelegramRingArc(quota.percent ?: 0, quota.risk) }
        TelegramRing(
            label = account.label,
            centerPercent = windows.maxOf { quota -> quota.percent ?: 0 },
            riskWord = account.worstRisk?.let { level -> riskLevelLabel(level, language) },
            arcs = arcs
        )
    }
    return TelegramRings(rings, snapshot?.lastCollectedAt)
}
