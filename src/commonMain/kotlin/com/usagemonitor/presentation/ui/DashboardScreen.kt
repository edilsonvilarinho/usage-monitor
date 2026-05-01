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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.ui.components.ResponsiveDashboardCardGrid
import kotlinx.coroutines.flow.StateFlow

// Chaves de toast emitidas por DashboardViewModel: "RATE_LIMIT:<source>" e "ERROR:<source>:<msg>".
// O caractere ":" do payload original é escapado como "_" antes da emissão e restaurado aqui.
private const val RATE_LIMIT_PREFIX = "RATE_LIMIT:"
private const val ERROR_PREFIX = "ERROR:"

private fun decodeToastMessage(key: String, language: AppLanguage): String {
    val isPt = language == AppLanguage.PT
    if (key.startsWith(RATE_LIMIT_PREFIX)) {
        val sourceLabel = sourceLabelFromKey(key.removePrefix(RATE_LIMIT_PREFIX))
        return if (isPt) "$sourceLabel rate limited — tentando novamente..."
        else "$sourceLabel rate limited — retrying..."
    }
    if (key.startsWith(ERROR_PREFIX)) {
        val rest = key.removePrefix(ERROR_PREFIX)
        val separatorIndex = rest.indexOf(':')
        if (separatorIndex < 0) {
            return key
        }
        val sourceLabel = sourceLabelFromKey(rest.substring(0, separatorIndex))
        val apiMsg = rest.substring(separatorIndex + 1).replace("_", ":")
        return "$sourceLabel: $apiMsg"
    }
    return key
}

private fun sourceLabelFromKey(rawSource: String): String {
    return when (rawSource) {
        "ANTHROPIC" -> "Anthropic"
        "MINIMAX" -> "MiniMax"
        "CODEX" -> "Codex"
        else -> rawSource
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    appVersion: String,
    language: AppLanguage,
    enabledApis: StateFlow<Set<ApiSource>>,
    cardOrder: List<ApiSource>,
    minimizedCards: Set<ApiSource>,
    onMoveCardToIndex: (ApiSource, Int) -> Unit,
    onToggleCardMinimized: (ApiSource) -> Unit,
    onOpenHistory: (ApiSource) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    countdownUpdatesEnabled: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val nextRefreshAt by viewModel.nextRefreshAt.collectAsState()
    val refreshingSources by viewModel.refreshingSources.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val enabledApisState by enabledApis.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { key ->
            val msg = decodeToastMessage(key, language)
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.clearToast()
        }
    }

    Scaffold(
        bottomBar = {
            FooterBar(
                appVersion = appVersion,
                language = language,
                nextRefreshAt = nextRefreshAt,
                onRefresh = { viewModel.refresh() },
                onOpenSettings = onOpenSettings,
                countdownUpdatesEnabled = countdownUpdatesEnabled
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
                    appUpdateState?.let { updateState ->
                        AppUpdateBanner(
                            state = updateState,
                            language = language,
                            onRetryInstallation = { viewModel.retryUpdateInstallation() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

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
                            UiState.NoApisEnabled -> NoApisEnabledContent(
                                language = language,
                                onOpenSettings = onOpenSettings
                            )
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
                                cardOrder = cardOrder,
                                minimizedCards = minimizedCards,
                                language = language,
                                onRefreshCard = { source -> viewModel.refresh(source) },
                                onMoveCardToIndex = onMoveCardToIndex,
                                onToggleCardMinimized = onToggleCardMinimized,
                                onOpenHistoryCard = onOpenHistory,
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
private fun NoApisEnabledContent(
    language: AppLanguage,
    onOpenSettings: () -> Unit
) {
    val title = if (language == AppLanguage.PT) {
        "Nenhuma API monitorada está habilitada"
    } else {
        "No monitored APIs are enabled"
    }
    val description = if (language == AppLanguage.PT) {
        "Abra as configurações e habilite pelo menos uma opção em APIs monitoradas para começar a carregar seus dados de uso."
    } else {
        "Open settings and enable at least one option in Monitored APIs to start loading your usage data."
    }
    val actionLabel = if (language == AppLanguage.PT) "Abrir configurações" else "Open settings"

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onOpenSettings) {
                Text(actionLabel)
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
    cardOrder: List<ApiSource>,
    minimizedCards: Set<ApiSource>,
    language: AppLanguage,
    onRefreshCard: (ApiSource) -> Unit,
    onMoveCardToIndex: (ApiSource, Int) -> Unit,
    onToggleCardMinimized: (ApiSource) -> Unit,
    onOpenHistoryCard: (ApiSource) -> Unit,
    onRetryAnthropic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleItems = apiStatsList.filter { stats -> stats.source in enabledApis }
    val itemBySource = visibleItems.associateBy { stats -> stats.source }
    val orderedSources = cardOrder.toSet()
    val items = cardOrder.mapNotNull(itemBySource::get) +
        visibleItems.filter { stats -> stats.source !in orderedSources }
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
                minimizedCards = minimizedCards,
                language = language,
                onRefreshCard = onRefreshCard,
                onMoveCardToIndex = onMoveCardToIndex,
                onToggleCardMinimized = onToggleCardMinimized,
                onOpenHistoryCard = onOpenHistoryCard,
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

@Composable
private fun AppUpdateBanner(
    state: AppUpdateUiState,
    language: AppLanguage,
    onRetryInstallation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = updateBannerContent(state = state, language = language)

    PersistentApiWarningBanner(
        title = content.title,
        description = content.description,
        actionLabel = content.actionLabel,
        onAction = if (content.showRetryAction) onRetryInstallation else null,
        modifier = modifier
    )
}

private fun updateBannerContent(
    state: AppUpdateUiState,
    language: AppLanguage
): UpdateBannerContent {
    return when (state) {
        is AppUpdateUiState.Available -> {
            val title = if (language == AppLanguage.PT) {
                "Nova versão ${state.update.version} disponível"
            } else {
                "Version ${state.update.version} is available"
            }
            val description = if (language == AppLanguage.PT) {
                if (state.automaticInstallSupported) {
                    "A atualização automática está pronta para este ambiente."
                } else {
                    "A atualização automática não está disponível nesta plataforma. Atualize manualmente pela release publicada."
                }
            } else {
                if (state.automaticInstallSupported) {
                    "Automatic updating is ready on this environment."
                } else {
                    "Automatic updating is not available on this platform. Install the published release manually."
                }
            }

            UpdateBannerContent(
                title = title,
                description = description
            )
        }

        is AppUpdateUiState.Downloading -> UpdateBannerContent(
            title = if (language == AppLanguage.PT) {
                "Nova versão ${state.update.version} disponível"
            } else {
                "Version ${state.update.version} is available"
            },
            description = if (language == AppLanguage.PT) {
                "Baixando automaticamente o pacote da atualização para preparar a instalação."
            } else {
                "Automatically downloading the update package to prepare the installation."
            }
        )

        is AppUpdateUiState.Installing -> UpdateBannerContent(
            title = if (language == AppLanguage.PT) {
                "Atualização pronta para instalar"
            } else {
                "Update is ready to install"
            },
            description = if (language == AppLanguage.PT) {
                "Fechando o app para iniciar o instalador da versão ${state.update.version}."
            } else {
                "Closing the app to start the installer for version ${state.update.version}."
            }
        )

        is AppUpdateUiState.Failed -> {
            val targetVersion = state.update?.version
            val title = if (language == AppLanguage.PT) {
                if (targetVersion != null) {
                    "Falha ao preparar a atualização ${targetVersion}"
                } else {
                    "Falha ao preparar a atualização"
                }
            } else {
                if (targetVersion != null) {
                    "Failed to prepare update ${targetVersion}"
                } else {
                    "Failed to prepare the update"
                }
            }
            val description = if (language == AppLanguage.PT) {
                "Não foi possível baixar ou iniciar o instalador automaticamente. ${state.message}"
            } else {
                "The app could not download or start the installer automatically. ${state.message}"
            }

            UpdateBannerContent(
                title = title,
                description = description,
                actionLabel = if (state.automaticInstallSupported) {
                    if (language == AppLanguage.PT) "Tentar novamente" else "Retry"
                } else {
                    null
                },
                showRetryAction = state.automaticInstallSupported
            )
        }
    }
}

private data class DashboardWarning(
    val source: ApiSource,
    val title: String,
    val description: String,
    val actionLabel: String?
)

private data class UpdateBannerContent(
    val title: String,
    val description: String,
    val actionLabel: String? = null,
    val showRetryAction: Boolean = false
)
