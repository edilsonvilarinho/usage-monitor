package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.CodexCliExportOutcome
import com.usagemonitor.presentation.viewmodel.CodexCliSessionRange
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsViewModel

private val CODEX_SESSION_ID_COLUMN = 170.dp
private val CODEX_SESSION_PROJECT_COLUMN = 180.dp
private val CODEX_SESSION_MODEL_COLUMN = 130.dp
private val CODEX_SESSION_RESPONSES_COLUMN = 84.dp
private val CODEX_SESSION_TOKENS_COLUMN = 130.dp
private val CODEX_SESSION_CACHE_COLUMN = 90.dp
private val CODEX_TURN_NUMBER_COLUMN = 44.dp
private val CODEX_TURN_TOKENS_COLUMN = 110.dp
private val CODEX_METRIC_BLOCK_WIDTH = 168.dp

@Composable
fun CodexCliSessionsScreen(
    viewModel: CodexCliSessionsViewModel,
    language: AppLanguage
) {
    val state by viewModel.uiState.collectAsState()

    AppWindowScaffold(
        modifier = Modifier.fillMaxSize(),
        contentPadding = AppSpacing.lg,
        spacing = AppSpacing.md,
        statusBar = {
            val showStatusBar = when (val current = state) {
                is CodexCliSessionsUiState.Success -> current.detail == null
                else -> true
            }
            if (showStatusBar) {
                when (val current = state) {
                    is CodexCliSessionsUiState.Success -> {
                        if (current.isRefreshing) {
                            Text(
                                text = if (language == AppLanguage.PT) "Atualizando…" else "Refreshing…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = if (language == AppLanguage.PT) {
                                    "Atualizado ${formatInstant(current.readAt)}"
                                } else {
                                    "Updated ${formatInstant(current.readAt)}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> Unit
                }
                Spacer(modifier = Modifier.weight(1f))
                AppStatusIndicator(
                    label = if (language == AppLanguage.PT) "Fonte local: Codex" else "Local source: Codex",
                    tone = AppTone.INFO
                )
            }
        }
    ) {
        val showSessionToolbar = when (val current = state) {
            is CodexCliSessionsUiState.Success -> current.detail == null
            else -> true
        }
        if (showSessionToolbar) {
            AppToolbar(spacing = AppSpacing.sm) {
                Text(
                    text = if (language == AppLanguage.PT) "Sessões Codex" else "Codex sessions",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                LiveBadge(language = language)
                AppSegmentedControl(
                    options = listOf(
                        AppSegment("5h"),
                        AppSegment("7d"),
                        AppSegment(if (language == AppLanguage.PT) "Tudo" else "All")
                    ),
                    selectedIndex = when (state) {
                        is CodexCliSessionsUiState.Success -> CodexCliSessionRange.entries.indexOf(
                            (state as CodexCliSessionsUiState.Success).range
                        )
                        else -> 0
                    },
                    onSelect = { index -> viewModel.setRange(CodexCliSessionRange.entries[index]) }
                )
                AppButton(
                    label = if (language == AppLanguage.PT) "Atualizar" else "Refresh",
                    onClick = viewModel::refresh,
                    tone = AppButtonTone.PRIMARY
                )
                AppButton(
                    label = "CSV",
                    onClick = { viewModel.exportCurrent(UsageExportFormat.CSV) },
                    tone = AppButtonTone.GHOST
                )
                AppButton(
                    label = "JSON",
                    onClick = { viewModel.exportCurrent(UsageExportFormat.JSON) },
                    tone = AppButtonTone.GHOST
                )
            }
        }

        when (val current = state) {
            CodexCliSessionsUiState.Loading -> AppLoadingState(
                message = if (language == AppLanguage.PT) "Lendo rollouts locais…" else "Reading local rollouts…",
                modifier = Modifier.fillMaxSize()
            )
            is CodexCliSessionsUiState.Error -> AppErrorState(
                message = current.message,
                retryLabel = if (language == AppLanguage.PT) "Tentar novamente" else "Retry",
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            )
            is CodexCliSessionsUiState.Success -> CodexCliSessionContent(
                state = current,
                language = language,
                onOpenSession = viewModel::openSession,
                onCloseDetail = viewModel::closeDetail,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CodexCliSessionContent(
    state: CodexCliSessionsUiState.Success,
    language: AppLanguage,
    onOpenSession: (String) -> Unit,
    onCloseDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.exportOutcome != null) {
        val outcome = state.exportOutcome
        AppBanner(
            title = when (outcome) {
                is CodexCliExportOutcome.Saved -> if (language == AppLanguage.PT) "Exportação concluída" else "Export complete"
                is CodexCliExportOutcome.Failed -> if (language == AppLanguage.PT) "Falha na exportação" else "Export failed"
            },
            description = when (outcome) {
                is CodexCliExportOutcome.Saved -> outcome.path
                is CodexCliExportOutcome.Failed -> outcome.message
            },
            tone = when (outcome) {
                is CodexCliExportOutcome.Saved -> AppTone.OK
                is CodexCliExportOutcome.Failed -> AppTone.CRITICAL
            }
        )
    }
    if (state.indexWarning != null) {
        AppBanner(
            title = if (language == AppLanguage.PT) "Índice parcial" else "Partial index",
            description = state.indexWarning,
            tone = AppTone.WARNING
        )
    }
    if (state.detail != null) {
        CodexCliSessionDetailContent(
            state = state,
            language = language,
            onCloseDetail = onCloseDetail,
            modifier = modifier
        )
        return
    }
    if (state.sessions.isEmpty()) {
        AppEmptyState(
            message = if (language == AppLanguage.PT) "Nenhuma sessão Codex encontrada." else "No Codex sessions found.",
            detail = if (language == AppLanguage.PT) "A origem é local e não depende de login." else "The source is local and does not require login.",
            modifier = modifier
        )
        return
    }

    AppDataSurfaceFlush(
        modifier = modifier,
        header = { CodexSessionColumnHeader(language = language) }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = state.sessions,
                key = { _, session -> session.sessionId }
            ) { index, session ->
                CodexCliSessionRow(
                    session = session,
                    language = language,
                    onClick = { onOpenSession(session.sessionId) },
                    showDivider = index != state.sessions.lastIndex
                )
            }
        }
    }
}

@Composable
private fun CodexSessionColumnHeader(language: AppLanguage) {
    AppColumnHeaderRow(
        startGutter = 0.dp,
        modifier = Modifier.padding(vertical = AppSpacing.sm)
    ) {
        AppColumnHeaderLabel(
            label = if (language == AppLanguage.PT) "Sessão" else "Session",
            modifier = Modifier.width(CODEX_SESSION_ID_COLUMN)
        )
        AppColumnHeaderLabel(
            label = if (language == AppLanguage.PT) "Projeto" else "Project",
            modifier = Modifier.width(CODEX_SESSION_PROJECT_COLUMN)
        )
        AppColumnHeaderLabel(
            label = if (language == AppLanguage.PT) "Tokens (com cache)" else "Tokens (cached)",
            modifier = Modifier.width(CODEX_SESSION_TOKENS_COLUMN)
        )
        AppColumnHeaderLabel(
            label = if (language == AppLanguage.PT) "Cache" else "Cache",
            modifier = Modifier.width(CODEX_SESSION_CACHE_COLUMN)
        )
        AppColumnHeaderLabel(
            label = if (language == AppLanguage.PT) "Respostas" else "Responses",
            modifier = Modifier.width(CODEX_SESSION_RESPONSES_COLUMN)
        )
        AppColumnHeaderLabel(
            label = if (language == AppLanguage.PT) "Modelo" else "Model",
            modifier = Modifier.width(CODEX_SESSION_MODEL_COLUMN)
        )
    }
}

@Composable
private fun CodexCliSessionRow(
    session: CodexCliSessionSummary,
    language: AppLanguage,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    AppDataRow(onClick = onClick, showDivider = showDivider) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.width(CODEX_SESSION_ID_COLUMN),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    AppCellValue(shortCodexId(session.sessionId))
                    Text(
                        text = formatInstant(session.lastTs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    modifier = Modifier.width(CODEX_SESSION_PROJECT_COLUMN),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    AppCellValue(session.projectName ?: "—")
                    Text(
                        text = if (language == AppLanguage.PT) {
                            "${session.turnCount} turno(s)"
                        } else {
                            "${session.turnCount} turn(s)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppCellValue(
                    value = formatQuantity(session.totalTokens),
                    modifier = Modifier.width(CODEX_SESSION_TOKENS_COLUMN)
                )
                Column(
                    modifier = Modifier.width(CODEX_SESSION_CACHE_COLUMN),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    AppCellValue(codexCachePercent(session))
                    AppProgressTrack(fraction = codexCacheFraction(session), tone = AppTone.OK)
                }
                AppCellValue(
                    value = session.responseCount.toString(),
                    modifier = Modifier.width(CODEX_SESSION_RESPONSES_COLUMN)
                )
                AppCellValue(
                    value = session.primaryModel ?: "—",
                    modifier = Modifier.width(CODEX_SESSION_MODEL_COLUMN),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                AppStatusIndicator(label = codexApplicationLabel(session), tone = AppTone.INFO)
                Text(
                    text = listOfNotNull(session.source.name.lowercase(), session.cliVersion).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodexCliSessionDetailContent(
    state: CodexCliSessionsUiState.Success,
    language: AppLanguage,
    onCloseDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detail = state.detail ?: return
    val summary = detail.summary

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppToolbar(spacing = AppSpacing.sm) {
            AppButton(
                label = if (language == AppLanguage.PT) "Voltar" else "Back",
                onClick = onCloseDetail,
                tone = AppButtonTone.GHOST
            )
            Text(shortCodexId(summary.sessionId), style = MaterialTheme.typography.titleMedium)
        }

        AppDataSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 0.dp,
            verticalArrangement = Arrangement.Top
        ) {
            AppSectionHeader(
                title = summary.projectName ?: if (language == AppLanguage.PT) "Projeto desconhecido" else "Unknown project",
                subtitle = "${codexApplicationLabel(summary)} · ${summary.source.name.lowercase()}"
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                CodexMetadataValue(
                    label = if (language == AppLanguage.PT) "Diretório" else "Directory",
                    value = summary.cwd ?: "—"
                )
                CodexMetadataValue(
                    label = if (language == AppLanguage.PT) "Branch" else "Branch",
                    value = summary.gitBranch ?: "—"
                )
                CodexMetadataValue(
                    label = if (language == AppLanguage.PT) "Versão CLI" else "CLI version",
                    value = summary.cliVersion ?: "—"
                )
                CodexMetadataValue(
                    label = if (language == AppLanguage.PT) "Período" else "Period",
                    value = "${formatInstant(summary.firstTs)} → ${formatInstant(summary.lastTs)}"
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppMetricBlock(
                label = if (language == AppLanguage.PT) "Respostas" else "Responses",
                value = summary.responseCount.toString(),
                modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH)
            )
            AppMetricBlock(
                label = if (language == AppLanguage.PT) "Tokens (com cache)" else "Tokens (cached)",
                value = formatQuantity(summary.totalTokens),
                modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH)
            )
            AppMetricBlock(
                label = "Cache",
                value = codexCachePercent(summary),
                modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH)
            )
            AppMetricBlock(
                label = if (language == AppLanguage.PT) "Entrada" else "Input",
                value = formatQuantity(summary.inputTokens),
                modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH)
            )
            AppMetricBlock(
                label = if (language == AppLanguage.PT) "Saída" else "Output",
                value = formatQuantity(summary.outputTokens),
                footer = if (language == AppLanguage.PT) {
                    "Raciocínio ${formatQuantity(summary.reasoningOutputTokens)}"
                } else {
                    "Reasoning ${formatQuantity(summary.reasoningOutputTokens)}"
                },
                modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH)
            )
        }

        AppDataSurfaceFlush(
            modifier = Modifier.fillMaxWidth(),
            header = {
                Column {
                    AppSectionHeader(
                        title = if (language == AppLanguage.PT) "Uso por turno" else "Usage by turn",
                        subtitle = if (language == AppLanguage.PT) {
                            "${summary.responseCount} resposta(s) · ${formatQuantity(summary.totalTokens)} tokens"
                        } else {
                            "${summary.responseCount} response(s) · ${formatQuantity(summary.totalTokens)} tokens"
                        },
                        markerColor = AppAccents.current.codex
                    )
                    CodexTurnColumnHeader(language = language)
                }
            }
        ) {
            detail.turns.forEachIndexed { index, turn ->
                AppDataRow(showDivider = index != detail.turns.lastIndex) {
                    AppCellValue(
                        value = "#${turn.seq + 1}",
                        modifier = Modifier.width(CODEX_TURN_NUMBER_COLUMN)
                    )
                    AppCellValue(
                        value = turn.model ?: "—",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppCellValue(
                        value = shortCodexId(turn.responseId, 18),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppCellValue(
                        value = turn.usage.totalTokens.toString(),
                        modifier = Modifier.width(CODEX_TURN_TOKENS_COLUMN)
                    )
                }
            }
        }
    }
}

@Composable
private fun CodexTurnColumnHeader(language: AppLanguage) {
    AppColumnHeaderRow(
        startGutter = 0.dp,
        modifier = Modifier.padding(vertical = AppSpacing.sm)
    ) {
        AppColumnHeaderLabel(label = "#", modifier = Modifier.width(CODEX_TURN_NUMBER_COLUMN))
        AppColumnHeaderLabel(
            label = if (language == AppLanguage.PT) "Modelo" else "Model",
            modifier = Modifier.weight(1f)
        )
        AppColumnHeaderLabel(
            label = if (language == AppLanguage.PT) "Resposta" else "Response",
            modifier = Modifier.weight(1f)
        )
        AppColumnHeaderLabel(label = "Tokens", modifier = Modifier.width(CODEX_TURN_TOKENS_COLUMN))
    }
}

@Composable
private fun CodexMetadataValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

private fun shortCodexId(value: String, maxLength: Int = 8): String {
    return if (value.length <= maxLength) value else "${value.take(maxLength)}…"
}

private fun codexApplicationLabel(session: CodexCliSessionSummary): String {
    return when (session.originator?.trim()?.lowercase()) {
        "codex desktop" -> "Codex Desktop"
        "codex-tui", "codex tui" -> "Codex CLI"
        null, "" -> "Codex CLI"
        else -> session.originator.trim()
    }
}

private fun codexCacheFraction(session: CodexCliSessionSummary): Float {
    if (session.inputTokens <= 0L) return 0f
    return (session.cachedInputTokens.toDouble() / session.inputTokens.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat()
}

private fun codexCachePercent(session: CodexCliSessionSummary): String {
    return formatPercentageOfTotal(session.cachedInputTokens.toDouble(), session.inputTokens)
}
