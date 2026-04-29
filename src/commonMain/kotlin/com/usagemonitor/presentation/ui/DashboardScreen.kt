package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    appVersion: String,
    language: AppLanguage,
    enabledApis: StateFlow<Set<ApiSource>>,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val secondsUntilRefresh by viewModel.secondsUntilRefresh.collectAsState()
    val refreshingSources by viewModel.refreshingSources.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val enabledApisState by enabledApis.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { key ->
            val msg = when {
                key == "RATE_LIMIT:ANTHROPIC" -> if (language == AppLanguage.PT) "Anthropic rate limited — tentando novamente..." else "Anthropic rate limited — retrying..."
                key.startsWith("ERROR:ANTHROPIC:") -> {
                    val apiMsg = key.removePrefix("ERROR:ANTHROPIC:").replace("_", ":")
                    if (language == AppLanguage.PT) "Anthropic: $apiMsg" else "Anthropic: $apiMsg"
                }
                key == "RATE_LIMIT:MINIMAX" -> if (language == AppLanguage.PT) "MiniMax rate limited — tentando novamente..." else "MiniMax rate limited — retrying..."
                key.startsWith("ERROR:MINIMAX:") -> {
                    val apiMsg = key.removePrefix("ERROR:MINIMAX:").replace("_", ":")
                    if (language == AppLanguage.PT) "MiniMax: $apiMsg" else "MiniMax: $apiMsg"
                }
                key == "RATE_LIMIT:CODEX" -> if (language == AppLanguage.PT) "Codex rate limited — tentando novamente..." else "Codex rate limited — retrying..."
                key.startsWith("ERROR:CODEX:") -> {
                    val apiMsg = key.removePrefix("ERROR:CODEX:").replace("_", ":")
                    if (language == AppLanguage.PT) "Codex: $apiMsg" else "Codex: $apiMsg"
                }
                else -> key
            }
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.clearToast()
        }
    }

    Scaffold(
        bottomBar = {
            FooterBar(
                appVersion = appVersion,
                language = language,
                secondsUntilRefresh = secondsUntilRefresh,
                onRefresh = { viewModel.refresh() },
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Column(
            modifier = modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (val state = uiState) {
                            is UiState.Loading -> LoadingContent(language = language)
                            is UiState.Error -> ErrorContent(
                                errors = state.errors,
                                language = language,
                                onRetryAll = { viewModel.refresh() },
                                onRetryAnthropic = { viewModel.refresh(ApiSource.ANTHROPIC) }
                            )
                            is UiState.Success -> SuccessContent(
                                apiStatsList = state.data,
                                partialErrors = state.errors,
                                refreshingSources = refreshingSources,
                                enabledApis = enabledApisState,
                                language = language,
                                onRefreshCard = { source -> viewModel.refresh(source) },
                                onRetryAnthropic = { viewModel.refresh(ApiSource.ANTHROPIC) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(language: AppLanguage) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (language == AppLanguage.PT) "Carregando dados das APIs..." else "Loading API data...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    errors: List<UiApiError>,
    language: AppLanguage,
    onRetryAll: () -> Unit,
    onRetryAnthropic: () -> Unit
) {
    val warnings = errors.mapNotNull { error -> warningFor(error = error, language = language) }
    val genericErrors = errors.filterNot { error -> error.isConfigurationIssue }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            warnings.forEach { warning ->
                PersistentApiWarningBanner(
                    title = warning.title,
                    description = warning.description,
                    actionLabel = warning.actionLabel,
                    onAction = warningActionFor(
                        source = warning.source,
                        onRetryAnthropic = onRetryAnthropic
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (genericErrors.isNotEmpty() || warnings.isEmpty()) {
                Text(
                    text = if (language == AppLanguage.PT) "Erro ao carregar dados" else "Failed to load data",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )

                genericErrors.forEach { error ->
                    Text(
                        text = error.formattedMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(onClick = onRetryAll) {
                    Text(if (language == AppLanguage.PT) "Tentar novamente" else "Retry")
                }
            }
        }
    }
}

@Composable
private fun SuccessContent(
    apiStatsList: List<ApiUsageStats>,
    partialErrors: List<UiApiError>,
    refreshingSources: Set<ApiSource>,
    enabledApis: Set<ApiSource>,
    language: AppLanguage,
    onRefreshCard: (ApiSource) -> Unit,
    onRetryAnthropic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = apiStatsList.filter { stats -> stats.source in enabledApis }
    val warnings = partialErrors.mapNotNull { error -> warningFor(error = error, language = language) }
    val genericErrors = partialErrors.filterNot { error -> error.isConfigurationIssue }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 12.dp)
        ) {
            warnings.forEach { warning ->
                PersistentApiWarningBanner(
                    title = warning.title,
                    description = warning.description,
                    actionLabel = warning.actionLabel,
                    onAction = warningActionFor(
                        source = warning.source,
                        onRetryAnthropic = onRetryAnthropic
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            genericErrors.forEach { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠ ${error.formattedMessage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            ResponsiveDashboardCardGrid(
                items = items,
                refreshingSources = refreshingSources,
                language = language,
                onRefreshCard = onRefreshCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ResponsiveDashboardCardGrid(
    items: List<ApiUsageStats>,
    refreshingSources: Set<ApiSource>,
    language: AppLanguage,
    onRefreshCard: (ApiSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { 16.dp.roundToPx() }
    val compactThresholdPx = with(density) { 720.dp.roundToPx() }

    Layout(
        modifier = modifier,
        content = {
            items.forEachIndexed { index, stats ->
                ApiUsageCard(
                    source = stats.source,
                    apiName = stats.apiName,
                    quotas = stats.quotas,
                    showUsageDetails = stats.source != ApiSource.ANTHROPIC,
                    isRefreshing = stats.source in refreshingSources,
                    language = language,
                    animationDelayMillis = index * 90,
                    onRefresh = { onRefreshCard(stats.source) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(width = constraints.maxWidth, height = 0) {}
        }

        val columns = if (constraints.maxWidth < compactThresholdPx) 1 else 2
        val totalSpacing = spacingPx * (columns - 1)
        val itemWidth = ((constraints.maxWidth - totalSpacing).coerceAtLeast(0)) / columns
        val childConstraints = constraints.copy(
            minWidth = itemWidth,
            maxWidth = itemWidth,
            minHeight = 0
        )
        val placeables = measurables.map { measurable -> measurable.measure(childConstraints) }
        val rowHeights = placeables
            .chunked(columns)
            .map { row -> row.maxOf { placeable -> placeable.height } }
        val layoutHeight = rowHeights.sum() + spacingPx * (rowHeights.size - 1).coerceAtLeast(0)

        layout(width = constraints.maxWidth, height = layoutHeight) {
            var yPosition = 0
            var itemIndex = 0

            rowHeights.forEach { rowHeight ->
                for (columnIndex in 0 until columns) {
                    if (itemIndex >= placeables.size) {
                        break
                    }

                    placeables[itemIndex].placeRelative(
                        x = columnIndex * (itemWidth + spacingPx),
                        y = yPosition
                    )
                    itemIndex++
                }

                yPosition += rowHeight + spacingPx
            }
        }
    }
}

private fun warningActionFor(
    source: ApiSource,
    onRetryAnthropic: () -> Unit
): (() -> Unit)? {
    return when (source) {
        ApiSource.ANTHROPIC -> onRetryAnthropic
        ApiSource.MINIMAX -> null
        ApiSource.CODEX -> null
    }
}

private fun warningFor(
    error: UiApiError,
    language: AppLanguage
): DashboardWarning? {
    if (error.isAnthropicCredentialIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "Anthropic precisa de autenticação",
                description = "Faça login no Claude Code para recriar ou renovar `~/.claude/.credentials.json` e depois tente novamente.",
                actionLabel = "Tentar novamente"
            )
        } else {
            DashboardWarning(
                source = error.source,
                title = "Anthropic needs authentication",
                description = "Sign in with Claude Code to recreate or renew `~/.claude/.credentials.json`, then try again.",
                actionLabel = "Retry"
            )
        }
    }

    if (error.isMiniMaxEnvVarIssue) {
        return if (language == AppLanguage.PT) {
            DashboardWarning(
                source = error.source,
                title = "MiniMax precisa de MINIMAX_API_KEY",
                description = "Defina `MINIMAX_API_KEY` antes de abrir o app e reinicie o monitor. Exemplo no Windows: `set MINIMAX_API_KEY=sua_chave`.",
                actionLabel = null
            )
        } else {
            DashboardWarning(
                source = error.source,
                title = "MiniMax needs MINIMAX_API_KEY",
                description = "Set `MINIMAX_API_KEY` before opening the app and restart the monitor. Example on Windows: `set MINIMAX_API_KEY=your_key`.",
                actionLabel = null
            )
        }
    }

    return null
}

private data class DashboardWarning(
    val source: ApiSource,
    val title: String,
    val description: String,
    val actionLabel: String?
)
