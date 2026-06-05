package com.usagemonitor.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.ui.components.ShimmerBox
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.ui.components.ResponsiveDashboardCardGrid
import kotlinx.coroutines.flow.StateFlow

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
        toastMessage?.let { toast ->
            val msg = decodeToastMessage(toast, language)
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
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AnimatedContent(
                            targetState = uiState::class,
                            transitionSpec = {
                                (fadeIn(tween(AppMotion.normal, easing = AppMotion.enterEasing)) +
                                    slideInVertically(tween(AppMotion.slow, easing = AppMotion.enterEasing)) { it / 12 })
                                    .togetherWith(fadeOut(tween(AppMotion.fast, easing = AppMotion.exitEasing)))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "dashboardStateContent"
                        ) { _ ->
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(2) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                cornerRadius = 16.dp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (language == AppLanguage.PT) "Carregando dados das APIs..." else "Loading API data...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
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
    val genericErrors = errors.filterNot { error -> error.isConfigurationIssue || error.isRateLimitIssue }

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
    val genericErrors = partialErrors.filterNot { error -> error.isConfigurationIssue || error.isRateLimitIssue }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 12.dp)
        ) {
            if (warnings.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                }
            }

            genericErrors.forEach { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
