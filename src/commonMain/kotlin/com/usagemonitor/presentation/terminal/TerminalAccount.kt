package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.buildUsageSnapshot
import com.usagemonitor.domain.entity.quotaZone
import com.usagemonitor.domain.entity.usageTargetPublicationOrder
import kotlin.time.Instant

/**
 * Uma conta como o modo terminal a mostra: a fonte e o apelido em campos separados
 * (a tabela do `status` tem uma coluna para cada), as cotas do retrato da HUD e os
 * limiares de alerta da conta, que dão a palavra de estado.
 */
data class TerminalAccount(
    val source: ApiSource,
    val storageKey: String,
    val name: String,
    val profile: String?,
    val fetchedAt: Instant?,
    val thresholds: List<Int>,
    val quotas: List<UsageSnapshotQuota>,
    /** A conta do histórico (#441): o modo interativo lê as 24 h dela. */
    val accountKey: UsageAccountKey? = null,
    /** O perfil do alvo (Anthropic e Codex extras), que também separa as sessões no índice. */
    val targetProfileId: String? = null,
    /** O plano que a fonte informa ("Max", "Pro"), quando informa. */
    val plan: String? = null
) {
    fun zoneOf(quota: UsageSnapshotQuota): QuotaZone? = quotaZone(quota.percent, thresholds)
}

/**
 * As contas da última leitura, na ordem do painel. As cotas saem de
 * [buildUsageSnapshot], o mesmo retrato da HUD, da web local e do bot: percentual só
 * para cota com janela, reinício só quando conhecido. Um dono só para essas regras.
 */
fun terminalAccounts(stats: List<ApiUsageStats>, alertSettings: UsageAlertSettings, now: Instant): List<TerminalAccount> {
    val ordered = stats.sortedWith(compareBy(usageTargetPublicationOrder) { item -> item.targetKey })
    val snapshot = buildUsageSnapshot(ordered, activeTargets = emptySet(), now = now)
    return ordered.zip(snapshot.accounts) { item, account ->
        TerminalAccount(
            source = item.source,
            storageKey = item.targetKey.storageKey,
            name = item.apiName,
            profile = item.profileLabel,
            fetchedAt = item.fetchedAt,
            thresholds = alertSettings.quotaPercentsFor(item.targetKey),
            quotas = account.quotas,
            accountKey = item.accountContext?.key,
            targetProfileId = item.targetKey.profileId,
            plan = item.planLabel
        )
    }
}
