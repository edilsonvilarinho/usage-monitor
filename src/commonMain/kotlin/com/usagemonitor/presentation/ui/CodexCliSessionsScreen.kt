package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.viewmodel.CodexCliSessionRange
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsViewModel

@Composable
fun CodexCliSessionsScreen(
    viewModel: CodexCliSessionsViewModel,
    language: AppLanguage
) {
    val state by viewModel.uiState.collectAsState()
    AppWindowScaffold(
        modifier = Modifier.fillMaxSize(),
        contentPadding = 16.dp,
        spacing = 12.dp,
        statusBar = {
            AppStatusIndicator(
                label = if (language == AppLanguage.PT) "Fonte local: Codex CLI" else "Local source: Codex CLI",
                tone = AppTone.INFO
            )
        }
    ) {
        AppToolbar {
            Text(
                text = if (language == AppLanguage.PT) "Sessões Codex CLI" else "Codex CLI sessions",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            rangeButton(viewModel, CodexCliSessionRange.LAST_5H, "5h", state)
            rangeButton(viewModel, CodexCliSessionRange.LAST_7D, "7d", state)
            rangeButton(viewModel, CodexCliSessionRange.ALL, if (language == AppLanguage.PT) "Tudo" else "All", state)
            AppButton(
                label = if (language == AppLanguage.PT) "Atualizar" else "Refresh",
                onClick = viewModel::refresh,
                tone = AppButtonTone.PRIMARY
            )
        }

        when (val current = state) {
            CodexCliSessionsUiState.Loading -> AppLoadingState(
                message = if (language == AppLanguage.PT) "Lendo rollouts locais…" else "Reading local rollouts…",
                modifier = Modifier.fillMaxWidth()
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
                onCloseDetail = viewModel::closeDetail
            )
        }
    }
}

@Composable
private fun rangeButton(
    viewModel: CodexCliSessionsViewModel,
    range: CodexCliSessionRange,
    label: String,
    state: CodexCliSessionsUiState
) {
    AppButton(
        label = label,
        onClick = { viewModel.setRange(range) },
        tone = if (state is CodexCliSessionsUiState.Success && state.range == range) {
            AppButtonTone.PRIMARY
        } else {
            AppButtonTone.GHOST
        }
    )
}

@Composable
private fun CodexCliSessionContent(
    state: CodexCliSessionsUiState.Success,
    language: AppLanguage,
    onOpenSession: (String) -> Unit,
    onCloseDetail: () -> Unit
) {
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
            onCloseDetail = onCloseDetail
        )
        return
    }
    if (state.sessions.isEmpty()) {
        AppEmptyState(
            message = if (language == AppLanguage.PT) "Nenhuma sessão Codex CLI encontrada." else "No Codex CLI sessions found.",
            detail = if (language == AppLanguage.PT) "A origem é local e não depende de login." else "The source is local and does not require login.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    AppDataSurface(modifier = Modifier.fillMaxSize(), contentPadding = 0.dp) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.sessions, key = { session -> session.sessionId }) { session ->
                CodexCliSessionRow(session = session, onClick = { onOpenSession(session.sessionId) })
            }
        }
    }
}

@Composable
private fun CodexCliSessionRow(
    session: CodexCliSessionSummary,
    onClick: () -> Unit
) {
    AppDataRow(onClick = onClick) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.projectName ?: session.sessionId, style = MaterialTheme.typography.titleSmall)
            Text(session.sessionId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(session.primaryModel ?: "—", style = MaterialTheme.typography.labelMedium)
        Text("${session.totalTokens} tokens", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CodexCliSessionDetailContent(
    state: CodexCliSessionsUiState.Success,
    language: AppLanguage,
    onCloseDetail: () -> Unit
) {
    val detail = state.detail ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppToolbar {
            AppButton(
                label = if (language == AppLanguage.PT) "Voltar" else "Back",
                onClick = onCloseDetail,
                tone = AppButtonTone.GHOST
            )
            Text(detail.summary.sessionId, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AppMetricBlock("Respostas", detail.summary.responseCount.toString(), Modifier.weight(1f))
            AppMetricBlock("Tokens", detail.summary.totalTokens.toString(), Modifier.weight(1f))
            AppMetricBlock("Entrada", detail.summary.inputTokens.toString(), Modifier.weight(1f))
            AppMetricBlock("Saída", detail.summary.outputTokens.toString(), Modifier.weight(1f))
            AppMetricBlock("Raciocínio", detail.summary.reasoningOutputTokens.toString(), Modifier.weight(1f))
        }
        AppDataSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            detail.turns.forEachIndexed { index, turn ->
                AppDataRow(showDivider = index != detail.turns.lastIndex) {
                    Text("#${turn.seq + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
                    Text(turn.model ?: "—", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(turn.responseId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(turn.usage.totalTokens.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
