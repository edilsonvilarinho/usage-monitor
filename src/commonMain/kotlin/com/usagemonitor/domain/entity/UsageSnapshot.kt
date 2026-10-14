package com.usagemonitor.domain.entity

import kotlin.time.Instant

/**
 * Retrato das cotas para fora da janela do app (#388 web local, #387 bot):
 * só metadados de uso — fonte, conta, cota, percentual, reinício. Nunca prompt,
 * resposta, caminho de projeto ou credencial.
 */
data class UsageSnapshot(
    /** Quando o retrato foi montado — a hora do pedido, não a da coleta. */
    val generatedAt: Instant,
    val accounts: List<UsageSnapshotAccount>
) {
    /**
     * Coleta mais recente entre as contas. É esta a hora que a página e o bot
     * mostram: [generatedAt] anda a cada pedido mesmo com o número parado há
     * minutos (a coleta é de 60 s com sessão CLI, 5 min sem).
     */
    val lastCollectedAt: Instant?
        get() = accounts.mapNotNull { account -> account.fetchedAt }.maxOrNull()
}

data class UsageSnapshotAccount(
    val source: ApiSource,
    val label: String,
    /** Sessão CLI ativa ou uso detectado pela cota agora (o arco da HUD). */
    val active: Boolean,
    val quotas: List<UsageSnapshotQuota>,
    /** Quando a leitura desta conta foi coletada; `null` antes da primeira coleta. */
    val fetchedAt: Instant? = null,
    /**
     * `UsageTargetKey.storageKey` da conta (#408, AA10): o limiar por conta é gravado por
     * ela, que não muda com o apelido. Id interno do app — não identifica ninguém.
     */
    val targetKey: String? = null
) {
    /** O pior risco entre as cotas, pela ordem do enum — a palavra da HUD. `null` sem projeção. */
    val worstRisk: UsageRiskLevel?
        get() = quotas.mapNotNull { quota -> quota.risk }.maxByOrNull { risk -> risk.ordinal }
}

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
    val currencyCode: String,
    /**
     * Risco da projeção, o mesmo que pinta o arco da HUD (`toneFor`). `null` é
     * "sem projeção" — cota vencida, saldo sem histórico, fonte observada — e a
     * HUD o mostra neutro; a página faz o mesmo em vez de inventar limiar próprio.
     */
    val risk: UsageRiskLevel? = null,
    /**
     * Quando a cota esgota no ritmo observado — o `estimatedExhaustionAt` da mesma
     * projeção de [risk] (#408, `/quando`). `null` é "sem data": com reinício
     * conhecido e risco [UsageRiskLevel.ON_TRACK], o reinício chega antes.
     */
    val exhaustsAt: Instant? = null
)

/**
 * Rótulo da conta no retrato: a fonte e, com mais de uma conta, o apelido. Um dono
 * só — o bot casa o histórico de cada conta com o cartão do `/status` por ele (#408).
 */
val ApiUsageStats.snapshotLabel: String
    get() = profileLabel?.let { label -> "$apiName · $label" } ?: apiName

/** Monta o retrato na ordem em que o dashboard publica as fontes. */
fun buildUsageSnapshot(
    stats: List<ApiUsageStats>,
    activeTargets: Set<UsageTargetKey>,
    now: Instant,
    riskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>> = emptyMap()
): UsageSnapshot {
    return UsageSnapshot(
        generatedAt = now,
        accounts = stats.map { item ->
            UsageSnapshotAccount(
                source = item.source,
                label = item.snapshotLabel,
                active = item.targetKey in activeTargets,
                fetchedAt = item.fetchedAt,
                targetKey = item.targetKey.storageKey,
                quotas = item.quotas.map { quota ->
                    val hasWindow = quota.hasKnownResetAt && quota.unit != UsageUnit.CURRENCY_USD && quota.total > 0L
                    // Mesmo filtro do `worstQuotaRisk`: cota vencida não tem projeção válida.
                    val summary = riskSummaries[item.targetKey]?.get(quota.seriesKey)?.takeUnless { quota.isExpiredAt(now) }
                    UsageSnapshotQuota(
                        label = quota.label,
                        periodType = quota.periodType,
                        unit = quota.unit,
                        used = quota.used,
                        total = quota.total,
                        percent = if (hasWindow) (quota.percentageUsed * 100f).toInt() else null,
                        resetsAt = if (quota.hasKnownResetAt) quota.periodEndAt else null,
                        currencyCode = quota.currencyCode,
                        risk = summary?.level,
                        exhaustsAt = summary?.estimatedExhaustionAt
                    )
                }
            )
        }
    )
}
