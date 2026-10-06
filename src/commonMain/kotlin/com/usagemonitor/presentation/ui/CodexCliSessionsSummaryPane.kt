package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.combinedThroughput
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.theme.AppSpacing

private val CODEX_METRIC_WIDTH = 168.dp
private val CODEX_BUCKET_NUMBER_COLUMN = 96.dp

/**
 * Totais do recorte, acima da lista e do Resumo (#384) — o mesmo bloco de
 * métricas do modal do Anthropic. Sem custo: o Codex não tem tarifa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodexCliSessionMetrics(sessions: List<CodexCliSessionSummary>, language: AppLanguage) {
    val throughput = sessions.map { session -> session.throughput }.combinedThroughput()
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
        if (throughput?.tokensPerSecond != null) {
            AppMetricBlock(
                label = CliSessionsLabels.throughput(language),
                value = formatThroughput(throughput),
                footer = CliSessionsLabels.throughputFooter(language),
                modifier = Modifier.width(CODEX_METRIC_WIDTH)
            )
        }
    }
}

/** Aba Resumo do Codex: por projeto e por modelo principal da sessão. */
@Composable
internal fun CodexCliSessionBreakdown(
    sessions: List<CodexCliSessionSummary>,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val pt = language == AppLanguage.PT
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        CodexBucketTable(
            title = if (pt) "Por projeto" else "By project",
            subtitle = null,
            axisLabel = if (pt) "Projeto" else "Project",
            buckets = codexBucketsBy(sessions) { session -> session.projectName },
            language = language
        )
        CodexBucketTable(
            title = if (pt) "Por modelo" else "By model",
            // O índice agrega por sessão: dizer "por modelo" sem a ressalva
            // faria a sessão que trocou de modelo parecer de um só.
            subtitle = if (pt) "Modelo principal de cada sessão" else "Primary model of each session",
            axisLabel = if (pt) "Modelo" else "Model",
            buckets = codexBucketsBy(sessions) { session -> session.primaryModel },
            language = language
        )
    }
}

@Composable
private fun CodexBucketTable(
    title: String,
    subtitle: String?,
    axisLabel: String,
    buckets: List<CodexSessionBucket>,
    language: AppLanguage
) {
    val pt = language == AppLanguage.PT
    AppDataSurfaceFlush(
        header = {
            Column {
                AppSectionHeader(title = title, subtitle = subtitle)
                AppColumnHeaderRow(startGutter = 0.dp) {
                    AppColumnHeaderLabel(axisLabel, Modifier.weight(1f))
                    AppColumnHeaderLabel(CliSessionsLabels.columnSessions(language), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                    AppColumnHeaderLabel(if (pt) "Respostas" else "Responses", Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                    AppColumnHeaderLabel(CliSessionsLabels.columnTokens(language), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                    AppColumnHeaderLabel(CliSessionsLabels.columnCache(language), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                    AppColumnHeaderLabel(CliSessionsLabels.throughput(language), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                }
            }
        }
    ) {
        Column {
            buckets.forEachIndexed { index, bucket ->
                AppDataRow(showDivider = index != buckets.lastIndex) {
                    AppCellValue(bucket.label, Modifier.weight(1f))
                    AppCellValue(bucket.sessionCount.toString(), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                    AppCellValue(bucket.responseCount.toString(), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                    AppCellValue(formatQuantity(bucket.totalTokens), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                    AppCellValue(formatPercent(bucket.cacheRate), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                    AppCellValue(formatThroughput(bucket.throughput), Modifier.width(CODEX_BUCKET_NUMBER_COLUMN))
                }
            }
        }
    }
}
