package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.worstQuotaRisk
import kotlinx.datetime.Instant

/**
 * A cota que hoje pesa mais no risco global — a fonte, a cota vencedora e a
 * projeção dela —, para a barra HUD (issue #164).
 *
 * `worstRisk` em [UsageAlertViewModel] já devolve o pior nível entre todas as
 * cotas, mas só o nível: a bandeja não precisa de mais que isso. A barra HUD
 * precisa também dizer **qual** fonte e **quando** ela reseta, e é isso que
 * este tipo carrega.
 */
internal data class WorstQuotaSnapshot(
    val stats: ApiUsageStats,
    val quota: QuotaInfo,
    val risk: QuotaRiskSummary
)

/**
 * Reaproveita [worstQuotaRisk] — a mesma regra usada pelo badge do
 * cabeçalho de cada card (cota vencida não entra, sem projeção não há
 * badge) — card a card, e escolhe o maior [com.usagemonitor.domain.entity.UsageRiskLevel.ordinal]
 * entre eles. Duplicar a regra aqui divergiria do que o card mostra.
 */
internal fun worstQuotaSnapshot(
    stats: List<ApiUsageStats>,
    riskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>,
    now: Instant
): WorstQuotaSnapshot? {
    return stats
        .asSequence()
        .mapNotNull { entry ->
            worstQuotaRisk(entry.quotas, riskSummaries[entry.targetKey].orEmpty(), now)
                ?.let { (quota, risk) -> WorstQuotaSnapshot(entry, quota, risk) }
        }
        .maxByOrNull { snapshot -> snapshot.risk.level.ordinal }
}
