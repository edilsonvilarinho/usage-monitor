package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.combinedThroughput
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.theme.AppSpacing

private val CODEX_METRIC_WIDTH = 168.dp

/**
 * Totais do recorte, acima da lista e do Resumo (#384) — o mesmo bloco de
 * métricas do modal do Anthropic. Sem custo: o Codex não tem tarifa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodexCliSessionMetrics(sessions: List<CodexCliSessionSummary>, language: AppLanguage) {
    val throughput = sessions.map { session -> session.throughput }.combinedThroughput()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppMetricBlock(
                label = CliSessionsLabels.columnSessions(language),
                value = sessions.size.toString(),
                modifier = Modifier.width(CODEX_METRIC_WIDTH)
            )
            AppMetricBlock(
                label = CliSessionsLabels.columnTokens(language),
                value = formatQuantity(sessions.sumOf { session -> session.totalTokens }),
                modifier = Modifier.width(CODEX_METRIC_WIDTH)
            )
            AppMetricBlock(
                label = CliSessionsLabels.responses(language),
                value = sessions.sumOf { session -> session.responseCount }.toString(),
                modifier = Modifier.width(CODEX_METRIC_WIDTH)
            )
            AppMetricBlock(
                label = CliSessionsLabels.activeTime(language),
                value = codexActiveTimeValue(sessions),
                modifier = Modifier.width(CODEX_METRIC_WIDTH)
            )
            if (throughput?.tokensPerSecond != null) {
                AppMetricBlock(
                    label = CliSessionsLabels.throughput(language),
                    value = formatThroughput(throughput),
                    footer = CliSessionsLabels.throughputFooter(language),
                    modifier = Modifier.width(CODEX_METRIC_WIDTH)
                )
            }
        }
        // Composição do total, fora dos blocos pelo mesmo motivo do Anthropic (a fileira perdia o alinhamento).
        Text(
            text = codexTokensBreakdown(sessions, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Tempo ativo somado; `null` em todas as sessões = não medido, mostrado como "—". */
private fun codexActiveTimeValue(sessions: List<CodexCliSessionSummary>): String {
    val measured = sessions.mapNotNull { session -> session.activeMillis }
    return if (measured.isEmpty()) "—" else formatActiveTime(measured.sum())
}

/**
 * `in` é a parte faturável (input − cache lido), porque `input_tokens` do Codex
 * inclui o cache lido. Mesmo formato do Anthropic: `in · out · cache lido · cache gravado`.
 */
private fun codexTokensBreakdown(sessions: List<CodexCliSessionSummary>, language: AppLanguage): String {
    return CliSessionsLabels.tokensBreakdown(
        inputTokens = sessions.sumOf { session -> (session.inputTokens - session.cachedInputTokens).coerceAtLeast(0L) },
        outputTokens = sessions.sumOf { session -> session.outputTokens },
        cacheReadTokens = sessions.sumOf { session -> session.cachedInputTokens },
        cacheWriteTokens = sessions.sumOf { session -> session.cacheWriteInputTokens },
        language = language
    )
}
