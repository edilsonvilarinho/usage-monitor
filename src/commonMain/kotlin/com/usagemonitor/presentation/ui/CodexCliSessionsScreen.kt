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
import com.usagemonitor.domain.entity.UsageExportFormat
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
import com.usagemonitor.presentation.ui.components.AppModalRevealScope
import com.usagemonitor.presentation.ui.components.rememberSettledRevealKey
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.CliSessionsView
import com.usagemonitor.presentation.viewmodel.CodexCliExportOutcome
import com.usagemonitor.presentation.viewmodel.CodexCliSessionRange
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsViewModel

// Com a coluna de vazão (#384) as sete colunas somam 880dp com os vãos; ID e
// projeto cederam 30dp cada para a linha continuar sem quebrar em 960dp.
private val CODEX_SESSION_ID_COLUMN = 140.dp
private val CODEX_SESSION_PROJECT_COLUMN = 150.dp
private val CODEX_SESSION_THROUGHPUT_COLUMN = 84.dp
private val CODEX_SESSION_MODEL_COLUMN = 130.dp
private val CODEX_SESSION_RESPONSES_COLUMN = 84.dp
private val CODEX_SESSION_TOKENS_COLUMN = 130.dp
private val CODEX_SESSION_CACHE_COLUMN = 90.dp
internal val CODEX_METRIC_BLOCK_WIDTH = 168.dp

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
            // Mesmo padrão do modal do Anthropic (#384): abas Sessões/Resumo à
            // esquerda, selo ao vivo, janela e exportações. Sem botão de atualizar
            // — o laço de 5 s já relê, e o botão sugeria que a tela não se atualiza.
            AppToolbar(spacing = AppSpacing.sm) {
                AppTabs(
                    tabs = listOf(
                        AppTab(label = BreakdownLabels.tabSessions(language)),
                        AppTab(label = BreakdownLabels.tabBreakdown(language))
                    ),
                    selectedIndex = if ((state as? CodexCliSessionsUiState.Success)?.view == CliSessionsView.BREAKDOWN) 1 else 0,
                    onSelect = { index ->
                        viewModel.selectView(if (index == 1) CliSessionsView.BREAKDOWN else CliSessionsView.SESSIONS)
                    },
                    modifier = Modifier.weight(1f)
                )
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
                    label = "CSV",
                    onClick = { viewModel.exportCurrent(UsageExportFormat.CSV) },
                    tone = AppButtonTone.GHOST
                )
                AppButton(
                    label = "JSON",
                    onClick = { viewModel.exportCurrent(UsageExportFormat.JSON) },
                    tone = AppButtonTone.GHOST
                )
                AppButton(
                    label = ExportLabels.exportPdf(language),
                    onClick = { viewModel.exportReport(language) }
                )
            }
        }

        // Dado que chega, faixa nova e lista ↔ detalhe refazem o E9 no conteúdo;
        // a faixa troca a chave só quando a leitura dela chega.
        val success = state as? CodexCliSessionsUiState.Success
        val revealKey = rememberSettledRevealKey(
            key = if (success == null) state::class else listOf(success.range, success.detail != null),
            settled = success?.isRefreshing != true
        )
        AppModalRevealScope(replayKey = revealKey) {
            when (val current = state) {
                CodexCliSessionsUiState.Loading -> AppLoadingState(
                    message = if (language == AppLanguage.PT) "Lendo rollouts locais…" else "Reading local rollouts…",
                    modifier = Modifier.fillMaxSize()
                )
                is CodexCliSessionsUiState.Error -> AppErrorState(
                    message = current.message,
                    retryLabel = if (language == AppLanguage.PT) "Tentar novamente" else "Retry",
                    onRetry = { viewModel.refresh() },
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
        CodexCliSessionDetailPane(
            detail = state.detail,
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

    CodexCliSessionMetrics(state.sessions, language)
    if (state.view == CliSessionsView.BREAKDOWN) {
        CodexCliSessionBreakdown(state.sessions, language, modifier)
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
        AppColumnHeaderLabel(
            label = CliSessionsLabels.throughput(language),
            modifier = Modifier.width(CODEX_SESSION_THROUGHPUT_COLUMN)
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
                AppCellValue(
                    value = formatThroughput(session.throughput),
                    modifier = Modifier.width(CODEX_SESSION_THROUGHPUT_COLUMN)
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
internal fun shortCodexId(value: String, maxLength: Int = 8): String {
    return if (value.length <= maxLength) value else "${value.take(maxLength)}…"
}

internal fun codexApplicationLabel(session: CodexCliSessionSummary): String {
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

internal fun codexCachePercent(session: CodexCliSessionSummary): String {
    return formatPercentageOfTotal(session.cachedInputTokens.toDouble(), session.inputTokens)
}
