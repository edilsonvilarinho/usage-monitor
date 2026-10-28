package com.usagemonitor

import com.usagemonitor.domain.entity.ANALYSIS_SOURCES
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CodexCliModelUsage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.analysisThroughputCuts
import com.usagemonitor.domain.entity.buildSessionAnalysis
import com.usagemonitor.domain.entity.snapshotLabel
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Análise da sessão (rodada AB, B04): histórico de 7 dias da conta e a vazão do índice CLI
 * "desde" cada recorte que a análise pede. Anthropic lê o índice do perfil da conta (alvo Anthropic sempre tem perfil; o
 * mesmo das Sessões CLI); Codex lê o índice da máquina. Índice que falha deixa a vazão nula.
 * A janela relê ao vivo (rodada AD): ~20 ms de SQL por passada, medido no plano.
 */
internal fun sessionAnalysisReader(graph: AppGraph, dashboard: DashboardViewModel): suspend (String) -> SessionAnalysis? =
    sessionAnalysisReader(
        currentStats = { (dashboard.uiState.value as? UiState.Success)?.data.orEmpty() },
        history = { source, accountKey, now -> graph.getUsageHistory(source, HistoryRange.LAST_7_DAYS, accountKey, now) },
        claudeThroughputs = { profileId, since -> graph.cliSessionRepository.getModelThroughputs(profileId = profileId, sinceEpochMillis = since) },
        codexUsage = { since -> graph.codexCliSessionRepository.getModelUsage(since) },
        codexIndexedAt = { graph.codexIndexSyncedAt.value }
    )

/** O mesmo leitor sobre as partes do grafo de que ele precisa, para o teste. */
internal fun sessionAnalysisReader(
    currentStats: () -> List<ApiUsageStats>,
    history: suspend (ApiSource, UsageAccountKey?, Instant) -> ApiUsageHistoryReport,
    claudeThroughputs: suspend (profileId: String?, sinceEpochMillis: Long) -> Result<Map<String, OutputThroughput>>,
    codexUsage: suspend (sinceEpochMillis: Long) -> Result<List<CodexCliModelUsage>>,
    codexIndexedAt: () -> Instant?,
    clock: Clock = Clock.System
): suspend (String) -> SessionAnalysis? = reader@{ key ->
    val stats = currentStats().firstOrNull { item -> item.targetKey.storageKey == key } ?: return@reader null
    if (stats.source !in ANALYSIS_SOURCES) return@reader null
    val now = clock.now()
    val report = runCatching { history(stats.source, stats.accountContext?.key, now) }.getOrNull() ?: return@reader null
    val cumulative = analysisThroughputCuts(report.series, now).mapNotNull { since ->
        val byModel = if (stats.source == ApiSource.ANTHROPIC) {
            claudeThroughputs(stats.targetKey.profileId, since).getOrNull()
        } else {
            codexUsage(since).getOrNull()
                ?.mapNotNull { usage -> usage.throughput?.let { throughput -> usage.model to throughput } }?.toMap()
        }
        byModel?.let { value -> since to value }
    }.toMap()
    val analysis = buildSessionAnalysis(stats.snapshotLabel, stats.source, report.series, cumulative, now)
    // O índice do Claude é sincronizado a cada 30 s; o do Codex, a cada 10 min — só ele diz a hora.
    if (stats.source == ApiSource.CODEX) analysis.copy(throughputIndexedAt = codexIndexedAt()) else analysis
}
