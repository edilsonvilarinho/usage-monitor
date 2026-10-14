package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.domain.entity.analysisThroughputCuts
import com.usagemonitor.domain.entity.buildSessionAnalysis
import com.usagemonitor.domain.entity.snapshotLabel
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlin.time.Clock

/**
 * Análise da sessão (rodada AB, B04): histórico de 7 dias da conta e a vazão do índice CLI
 * "desde" cada recorte que a análise pede. Anthropic lê o índice do perfil da conta (alvo Anthropic sempre tem perfil; o
 * mesmo das Sessões CLI); Codex lê o índice da máquina. Índice que falha deixa a vazão nula.
 * A janela relê ao vivo (rodada AD): ~20 ms de SQL por passada, medido no plano.
 */
internal fun sessionAnalysisReader(graph: AppGraph, dashboard: DashboardViewModel): suspend (String) -> SessionAnalysis? = reader@{ key ->
    val stats = (dashboard.uiState.value as? UiState.Success)?.data?.firstOrNull { item -> item.targetKey.storageKey == key } ?: return@reader null
    if (stats.source != ApiSource.ANTHROPIC && stats.source != ApiSource.CODEX) return@reader null
    val now = Clock.System.now()
    val report = runCatching { graph.getUsageHistory(stats.source, HistoryRange.LAST_7_DAYS, stats.accountContext?.key, now) }.getOrNull() ?: return@reader null
    val cumulative = analysisThroughputCuts(report.series, now).mapNotNull { since ->
        val byModel = if (stats.source == ApiSource.ANTHROPIC) {
            graph.cliSessionRepository.getModelThroughputs(profileId = stats.targetKey.profileId, sinceEpochMillis = since).getOrNull()
        } else {
            graph.codexCliSessionRepository.getModelUsage(since).getOrNull()
                ?.mapNotNull { usage -> usage.throughput?.let { throughput -> usage.model to throughput } }?.toMap()
        }
        byModel?.let { value -> since to value }
    }.toMap()
    val analysis = buildSessionAnalysis(stats.snapshotLabel, stats.source, report.series, cumulative, now)
    // O índice do Claude é sincronizado a cada 30 s; o do Codex, a cada 10 min — só ele diz a hora.
    if (stats.source == ApiSource.CODEX) analysis.copy(throughputIndexedAt = graph.codexIndexSyncedAt.value) else analysis
}
