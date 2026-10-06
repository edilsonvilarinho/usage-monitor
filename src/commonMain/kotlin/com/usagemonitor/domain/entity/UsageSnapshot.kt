package com.usagemonitor.domain.entity

import kotlin.time.Instant

/**
 * Retrato das cotas para fora da janela do app (#388 web local, #387 bot):
 * só metadados de uso — fonte, conta, cota, percentual, reinício. Nunca prompt,
 * resposta, caminho de projeto ou credencial.
 */
data class UsageSnapshot(
    val generatedAt: Instant,
    val accounts: List<UsageSnapshotAccount>
)

data class UsageSnapshotAccount(
    val source: ApiSource,
    val label: String,
    /** Sessão CLI ativa ou uso detectado pela cota agora (o arco da HUD). */
    val active: Boolean,
    val quotas: List<UsageSnapshotQuota>
)

data class UsageSnapshotQuota(
    val label: String,
    val periodType: PeriodType,
    val unit: UsageUnit,
    val used: Long,
    val total: Long,
    /** 0–100 para cota com janela; `null` para saldo e atividade sem limite. */
    val percent: Int?,
    /** `null` quando a fonte não informa reinício (saldo pré-pago). */
    val resetsAt: Instant?,
    val currencyCode: String
)

/** Monta o retrato na ordem em que o dashboard publica as fontes. */
fun buildUsageSnapshot(
    stats: List<ApiUsageStats>,
    activeTargets: Set<UsageTargetKey>,
    now: Instant
): UsageSnapshot {
    return UsageSnapshot(
        generatedAt = now,
        accounts = stats.map { item ->
            UsageSnapshotAccount(
                source = item.source,
                label = item.profileLabel?.let { label -> "${item.apiName} · $label" } ?: item.apiName,
                active = item.targetKey in activeTargets,
                quotas = item.quotas.map { quota ->
                    val hasWindow = quota.hasKnownResetAt && quota.unit != UsageUnit.CURRENCY_USD && quota.total > 0L
                    UsageSnapshotQuota(
                        label = quota.label,
                        periodType = quota.periodType,
                        unit = quota.unit,
                        used = quota.used,
                        total = quota.total,
                        percent = if (hasWindow) (quota.percentageUsed * 100f).toInt() else null,
                        resetsAt = if (quota.hasKnownResetAt) quota.periodEndAt else null,
                        currencyCode = quota.currencyCode
                    )
                }
            )
        }
    )
}
