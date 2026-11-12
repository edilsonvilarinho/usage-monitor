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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.UsageExportFormat
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.presentation.CliSessionSort
import com.usagemonitor.presentation.CliSessionSortColumn
import com.usagemonitor.presentation.codexCacheRateOf
import com.usagemonitor.presentation.nextCliSessionSort
import com.usagemonitor.presentation.sortCodexCliSessions
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
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
import com.usagemonitor.presentation.ui.components.AppSortableColumnHeaderLabel
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
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsViewModel

// Com a coluna de vazão (#384) as sete colunas somam 880dp com os vãos; ID e
// projeto cederam 30dp cada para a linha continuar sem quebrar em 960dp.
private val CODEX_SESSION_ID_COLUMN = 130.dp
private val CODEX_SESSION_PROJECT_COLUMN = 140.dp
private val CODEX_SESSION_THROUGHPUT_COLUMN = 84.dp
private val CODEX_SESSION_ACTIVE_COLUMN = 76.dp
private val CODEX_SESSION_RESPONSES_COLUMN = 84.dp
private val CODEX_SESSION_TOKENS_COLUMN = 130.dp
private val CODEX_SESSION_CACHE_COLUMN = 90.dp
internal val CODEX_METRIC_BLOCK_WIDTH = 168.dp

/** Tag do cabeçalho da lista Codex; o teste seleciona a coluna por ele, não pelo rótulo repetido. */
const val CODEX_SESSION_COLUMN_HEADER_TAG = "codexSessionColumnHeader"

@Composable
fun CodexCliSessionsScreen(
    viewModel: CodexCliSessionsViewModel,
    language: AppLanguage,
    // Rótulo do perfil da janela (#560); nulo quando o host não tem perfil para mostrar.
    profileLabel: String? = null
) {
    val state by viewModel.uiState.collectAsState()
    // Ordenação da lista de sessões; null é a ordem publicada pela consulta.
    var sessionSort by remember { mutableStateOf<CliSessionSort?>(null) }

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
                                text = CliSessionsLabels.lastChange(
                                    current.lastChangedAt?.let { formatInstant(it) },
                                    language
                                ),
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
                if (profileLabel != null) {
                    Text(
                        text = profileLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LiveBadge(language = language)
                AppSegmentedControl(
                    options = CliSessionRange.entries.map { AppSegment(CliSessionsLabels.rangeLabel(it, language)) },
                    selectedIndex = when (state) {
                        is CodexCliSessionsUiState.Success -> CliSessionRange.entries.indexOf(
                            (state as CodexCliSessionsUiState.Success).range
                        )
                        else -> 0
                    },
                    onSelect = { index -> viewModel.setRange(CliSessionRange.entries[index]) }
                )
                // Mesmos tons e tags da janela Anthropic (CliSessionsScreen): contorno, não fantasma.
                AppButton(
                    label = ExportLabels.exportCsv(language),
                    onClick = { viewModel.exportCurrent(UsageExportFormat.CSV) },
                    modifier = Modifier.testTag(EXPORT_CSV_TAG)
                )
                AppButton(
                    label = ExportLabels.exportJson(language),
                    onClick = { viewModel.exportCurrent(UsageExportFormat.JSON) },
                    modifier = Modifier.testTag(EXPORT_JSON_TAG)
                )
                AppButton(
                    label = ExportLabels.exportPdf(language),
                    onClick = { viewModel.exportReport(language) },
                    modifier = Modifier.testTag(EXPORT_PDF_TAG)
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
                    sort = sessionSort,
                    onSortChange = { column -> sessionSort = nextCliSessionSort(sessionSort, column) },
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
    sort: CliSessionSort?,
    onSortChange: (CliSessionSortColumn) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.exportOutcome != null) {
        val outcome = state.exportOutcome
        AppBanner(
            title = when (outcome) {
                is CodexCliExportOutcome.Saved -> CommonLabels.exportDone(language)
                is CodexCliExportOutcome.Failed -> CommonLabels.exportFailed(language)
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
        CliUsageBreakdownPane(
            breakdown = state.usageBreakdown,
            errorMessage = null,
            language = language,
            memberAxisLabel = BreakdownLabels.codexMemberAxis(language),
            memberRowLabel = { bucketLabel -> BreakdownLabels.codexMemberRow(bucketLabel, language) },
            unpricedMode = true,
            modifier = modifier
        )
        return
    }

    CodexCliSessionTable(
        sessions = state.sessions,
        language = language,
        sort = sort,
        onSortChange = onSortChange,
        onOpenSession = onOpenSession,
        modifier = modifier
    )
}

@Composable
private fun CodexCliSessionTable(
    sessions: List<CodexCliSessionSummary>,
    language: AppLanguage,
    sort: CliSessionSort?,
    onSortChange: (CliSessionSortColumn) -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier
) {
    // A ordenação só reordena a exibição; a lista publicada pelo view model não muda.
    val ordered = remember(sessions, sort) { sortCodexCliSessions(sessions, sort) }
    AppDataSurfaceFlush(
        modifier = modifier,
        header = {
            CodexSessionColumnHeader(language = language, sort = sort, onSortChange = onSortChange)
        }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = ordered,
                key = { _, session -> session.sessionId }
            ) { index, session ->
                CodexCliSessionRow(
                    session = session,
                    language = language,
                    onClick = { onOpenSession(session.sessionId) },
                    showDivider = index != ordered.lastIndex
                )
            }
        }
    }
}

@Composable
private fun CodexSessionColumnHeader(
    language: AppLanguage,
    sort: CliSessionSort?,
    onSortChange: (CliSessionSortColumn) -> Unit
) {
    AppColumnHeaderRow(
        startGutter = 0.dp,
        modifier = Modifier.padding(vertical = AppSpacing.sm).testTag(CODEX_SESSION_COLUMN_HEADER_TAG)
    ) {
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnSession(language),
            modifier = Modifier.width(CODEX_SESSION_ID_COLUMN)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnProject(language),
            modifier = Modifier.width(CODEX_SESSION_PROJECT_COLUMN)
        )
        SortableCodexSessionHeader(
            column = CliSessionSortColumn.TOKENS,
            label = CliSessionsLabels.columnTokens(language),
            modifier = Modifier.width(CODEX_SESSION_TOKENS_COLUMN),
            sort = sort,
            language = language,
            onSortChange = onSortChange
        )
        SortableCodexSessionHeader(
            column = CliSessionSortColumn.CACHE,
            label = CliSessionsLabels.columnCache(language),
            modifier = Modifier.width(CODEX_SESSION_CACHE_COLUMN),
            sort = sort,
            language = language,
            onSortChange = onSortChange
        )
        SortableCodexSessionHeader(
            column = CliSessionSortColumn.RESPONSES,
            label = CliSessionsLabels.responses(language),
            modifier = Modifier.width(CODEX_SESSION_RESPONSES_COLUMN),
            sort = sort,
            language = language,
            onSortChange = onSortChange
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.activeTime(language),
            modifier = Modifier.width(CODEX_SESSION_ACTIVE_COLUMN)
        )
        SortableCodexSessionHeader(
            column = CliSessionSortColumn.THROUGHPUT,
            label = CliSessionsLabels.throughput(language),
            modifier = Modifier.width(CODEX_SESSION_THROUGHPUT_COLUMN),
            sort = sort,
            language = language,
            onSortChange = onSortChange
        )
    }
}

/** Cabeçalho ordenável da lista Codex; mesmo contrato do `SortableSessionHeader` da lista Anthropic. */
@Composable
private fun SortableCodexSessionHeader(
    column: CliSessionSortColumn,
    label: String,
    modifier: Modifier,
    sort: CliSessionSort?,
    language: AppLanguage,
    onSortChange: (CliSessionSortColumn) -> Unit
) {
    AppSortableColumnHeaderLabel(
        label = label,
        sortDescending = sort?.takeIf { it.column == column }?.descending,
        onClick = { onSortChange(column) },
        descendingStateDescription = CliSessionsLabels.sortDescendingState(language),
        ascendingStateDescription = CliSessionsLabels.sortAscendingState(language),
        modifier = modifier
    )
}

/** Tempo ativo da linha: `null` = não medido, mostrado como "—"; zero continua "0min". */
private fun codexActiveCell(activeMillis: Long?): String =
    if (activeMillis == null) "—" else formatActiveTime(activeMillis)

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
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        AppCellValue(shortCodexId(session.sessionId))
                        CopySessionCommandButton(
                            sessionId = session.sessionId,
                            language = language,
                            command = codexResumeSessionCommand(session.sessionId)
                        )
                    }
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
                    value = codexActiveCell(session.activeMillis),
                    modifier = Modifier.width(CODEX_SESSION_ACTIVE_COLUMN)
                )
                AppCellValue(
                    value = formatThroughput(session.throughput),
                    modifier = Modifier.width(CODEX_SESSION_THROUGHPUT_COLUMN)
                )
            }
            // Segunda linha: contagens e origem da sessão, numa frase só (#560).
            Text(
                text = codexSessionDetailLine(session, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Terceira linha: a mistura de modelos, que saiu da grade (#445). Mistura
            // vazia não desenha nada.
            // Sem mistura (dado antigo ou sessão de modelo único sem contagem), o nome do
            // modelo principal ocupa a linha, para o modelo não sumir da lista (#560).
            val primaryModel = session.primaryModel
            if (session.modelMix.isEmpty && primaryModel != null) {
                Text(
                    text = primaryModel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(sessionModelMixLineTag(session.sessionId))
                )
            } else {
                SessionModelMixLine(
                    mix = session.modelMix,
                    language = language,
                    modifier = Modifier.testTag(sessionModelMixLineTag(session.sessionId))
                )
            }
        }
    }
}

/** Segunda linha da sessão Codex: `● N respostas · M turnos · app · origem · versão`. */
internal fun codexSessionDetailLine(session: CodexCliSessionSummary, language: AppLanguage): String {
    val parts = listOfNotNull(
        codexResponsesCountLabel(session.responseCount, language),
        CliSessionsLabels.turnsLabel(session.turnCount, language),
        codexApplicationLabel(session),
        session.source.name.lowercase(),
        session.cliVersion
    )
    return "● " + parts.joinToString(" · ")
}

private fun codexResponsesCountLabel(count: Int, language: AppLanguage): String =
    localized(
        language,
        if (count == 1) "1 resposta" else "$count respostas",
        if (count == 1) "1 response" else "$count responses"
    )

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
    val rate = codexCacheRateOf(session) ?: return 0f
    return rate.coerceIn(0.0, 1.0).toFloat()
}

internal fun codexCachePercent(session: CodexCliSessionSummary): String {
    return formatPercentageOfTotal(session.cachedInputTokens.toDouble(), session.inputTokens)
}
