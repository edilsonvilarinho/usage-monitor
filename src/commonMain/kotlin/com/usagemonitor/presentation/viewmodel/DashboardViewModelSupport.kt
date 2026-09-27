package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageSpike
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.detectSpike
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

// Regras sem estado do `DashboardViewModel`, fora da classe pelo limite de 800
// linhas (#304). Nenhuma lê nem escreve o cache do view model.

internal const val HTTP_RATE_LIMIT_MARKER = "HTTP 429"

/**
 * Fontes locais (issue #267) cuja última leitura sobrevive a uma falha, como a do
 * Codex. O dado guardado leva [ApiUsageNotice.SOURCE_UNSTABLE]: sem a marca o card
 * mostrava números congelados — janelas deslizantes de 5h/7d que não andam mais —
 * como se fossem da coleta corrente.
 */
private val LOCAL_SESSION_CACHE_SOURCES = setOf(ApiSource.GEMINI, ApiSource.CURSOR, ApiSource.ANTIGRAVITY)

/** O reset conhecido mais próximo ainda no futuro, entre todas as cotas. */
internal fun earliestKnownQuotaReset(snapshot: List<ApiUsageStats>, now: Instant): Instant? {
    var earliest: Instant? = null
    for (stats in snapshot) {
        for (quota in stats.quotas) {
            if (!quota.hasKnownResetAt || quota.periodEndAt <= now) {
                continue
            }
            val currentEarliest = earliest
            if (currentEarliest == null || quota.periodEndAt < currentEarliest) {
                earliest = quota.periodEndAt
            }
        }
    }
    return earliest
}

/** A quota Codex válida pode ser parcial: o plano pode expor uma só janela. */
internal fun isPersistableDashboardStats(stats: ApiUsageStats): Boolean {
    if (stats.source != ApiSource.CODEX) {
        return true
    }
    if (stats.quotas.isEmpty()) {
        return false
    }
    return stats.quotas.all(::isValidCodexQuota)
}

private fun isValidCodexQuota(quota: QuotaInfo): Boolean {
    return quota.unit == UsageUnit.PERCENTAGE &&
        quota.total > 0L && quota.used in 0L..quota.total
}

/** O estado da tela a partir do que cada alvo habilitado tem em cache, na ordem das fontes. */
internal fun buildDashboardUiState(
    enabledTargets: Set<UsageTargetKey>,
    statsByTarget: Map<UsageTargetKey, ApiUsageStats>,
    errorsByTarget: Map<UsageTargetKey, UiApiError>,
    riskByTarget: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>
): UiState {
    if (enabledTargets.isEmpty()) {
        return UiState.NoApisEnabled
    }

    val stats = enabledTargets
        .sortedWith(compareBy<UsageTargetKey> { it.source.ordinal }.thenBy { it.profileId.orEmpty() })
        .mapNotNull { target -> statsByTarget[target] }

    val errors = enabledTargets
        .sortedWith(compareBy<UsageTargetKey> { it.source.ordinal }.thenBy { it.profileId.orEmpty() })
        .mapNotNull { target -> errorsByTarget[target] }

    val riskSummaries = enabledTargets
        .mapNotNull { target -> riskByTarget[target]?.let { risks -> target to risks } }
        .toMap()

    return if (stats.isNotEmpty()) {
        UiState.Success(stats, errors, riskSummaries)
    } else {
        UiState.Error(errors)
    }
}

/**
 * O que fica no card de um alvo cuja coleta falhou: nada, a leitura anterior
 * intacta (recarga que pediu para preservar) ou a leitura anterior marcada com
 * [ApiUsageNotice.SOURCE_UNSTABLE] — Codex e as fontes locais mantêm a última
 * leitura válida, mas não podem apresentá-la como se fosse da coleta corrente.
 */
internal fun statsRetainedAfterFailure(
    target: UsageTargetKey,
    existingStats: ApiUsageStats?,
    preserveDataOnFailure: Boolean
): ApiUsageStats? {
    if (existingStats == null) {
        return null
    }
    val canPreserveCodexCache = target.source == ApiSource.CODEX && isPersistableDashboardStats(existingStats)
    val canPreserveLocalIntegrationCache = target.source in LOCAL_SESSION_CACHE_SOURCES
    if (canPreserveCodexCache || canPreserveLocalIntegrationCache) {
        return existingStats.copy(notices = existingStats.notices + ApiUsageNotice.SOURCE_UNSTABLE)
    }
    return if (preserveDataOnFailure) existingStats else null
}

/** O erro que a tela mostra para uma falha de coleta, com a mensagem já saneada. */
internal fun uiApiErrorOf(
    target: UsageTargetKey,
    error: Throwable,
    profiles: List<AnthropicProfileRef>
): UiApiError {
    val source = target.source
    val targetLabel = if (source == ApiSource.ANTHROPIC) {
        profiles.firstOrNull { it.id == target.profileId }?.let { "Anthropic — ${it.label}" }
    } else {
        null
    }
    val originalMessage = error.message ?: error::class.simpleName ?: "erro desconhecido"
    // Falha de conectividade (proxy ausente/incorreto, DNS, timeout de conexão)
    // é classificada pelo TIPO da exceção, não por substring da mensagem: o
    // texto de `ConnectException`/`SocketTimeoutException` varia por JVM e SO,
    // e não dá para confiar nele. O marcador fixo entra como prefixo — mesmo
    // mecanismo de `HTTP_RATE_LIMIT_MARKER` — para o `UiApiError`/`warningFor`
    // existentes reconhecerem por substring sem precisar de um enum de erro
    // novo. HTTP 407 (proxy exige credencial) não passa por aqui: chega como
    // resposta HTTP normal e cai no mecanismo de marcador de status já usado
    // por 429/503 (ver `RemoteApiDataSource.requireSuccess`).
    val rawMessage = if (isConnectivityFailure(error)) {
        "$NETWORK_CONNECTIVITY_MARKER ($originalMessage)"
    } else {
        originalMessage
    }
    val message = sanitizeUiErrorMessage(source, rawMessage)
    return UiApiError(target = target, message = message, rawMessage = rawMessage, targetLabel = targetLabel)
}

/** Um alvo por fonte habilitada, e um por perfil na Anthropic, na ordem das fontes. */
internal fun enabledTargetsOf(
    enabledSources: Set<ApiSource>,
    profiles: List<AnthropicProfileRef>
): Set<UsageTargetKey> {
    val targets = linkedSetOf<UsageTargetKey>()
    enabledSources.sortedBy { it.ordinal }.forEach { source ->
        if (source == ApiSource.ANTHROPIC) {
            profiles.forEach { profile ->
                targets += UsageTargetKey(ApiSource.ANTHROPIC, profile.id)
            }
        } else {
            targets += UsageTargetKey.forSource(source)
        }
    }
    return targets
}

internal data class PendingFetchRequest(
    val targets: Set<UsageTargetKey>,
    val preserveDataOnFailure: Boolean
)

/**
 * Junta um pedido de coleta ao que já espera na fila. Dois pedidos que preservam
 * os dados somam os alvos; qualquer outro vira uma coleta completa, sem
 * preservar — ela cobre os dois.
 */
internal fun mergePendingFetch(
    existing: PendingFetchRequest?,
    targets: Set<UsageTargetKey>,
    preserveDataOnFailure: Boolean,
    allTargets: () -> Set<UsageTargetKey>
): PendingFetchRequest {
    return when {
        existing == null -> PendingFetchRequest(targets, preserveDataOnFailure)
        !existing.preserveDataOnFailure || !preserveDataOnFailure -> {
            PendingFetchRequest(allTargets(), preserveDataOnFailure = false)
        }

        else -> {
            PendingFetchRequest(
                targets = existing.targets + targets,
                preserveDataOnFailure = true
            )
        }
    }
}

internal fun riskSummariesOf(series: List<UsageHistorySeries>): Map<QuotaSeriesKey, QuotaRiskSummary> {
    return series
        .mapNotNull { item -> item.riskSummary?.let { risk -> item.seriesKey to risk } }
        .toMap()
}

/** As anomalias de gasto de um alvo, lidas do mesmo relatório de histórico da projeção. */
internal fun spikesOf(
    series: List<UsageHistorySeries>,
    target: UsageTargetKey,
    stats: ApiUsageStats,
    capturedAt: Instant,
    timeZone: TimeZone,
    minFactor: Double
): List<UsageSpike> {
    val targetLabel = stats.profileLabel?.takeIf { label -> label.isNotBlank() } ?: stats.apiName
    return series.mapNotNull { item ->
        item.detectSpike(
            target = target,
            targetLabel = targetLabel,
            now = capturedAt,
            timeZone = timeZone,
            minFactor = minFactor
        )
    }
}
