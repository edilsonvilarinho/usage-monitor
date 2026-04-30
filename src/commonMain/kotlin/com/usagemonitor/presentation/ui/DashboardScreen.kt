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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val secondsUntilRefresh by viewModel.secondsUntilRefresh.collectAsState()
    val refreshingSources by viewModel.refreshingSources.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
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

@Composable
private fun ResponsiveDashboardCardGrid(
    items: List<ApiUsageStats>,
    refreshingSources: Set<ApiSource>,
    minimizedCards: Set<ApiSource>,
    language: AppLanguage,
    onRefreshCard: (ApiSource) -> Unit,
    onMoveCardToIndex: (ApiSource, Int) -> Unit,
    onToggleCardMinimized: (ApiSource) -> Unit,
    onOpenHistoryCard: (ApiSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { 16.dp.roundToPx() }
    val compactThresholdPx = with(density) { 720.dp.roundToPx() }
    val itemBounds = remember { mutableStateMapOf<ApiSource, CardGridBounds>() }
    var dragState by remember { mutableStateOf<CardDragState?>(null) }
    var dropTargetIndex by remember { mutableStateOf<Int?>(null) }

    Layout(
        modifier = modifier,
        content = {
            items.forEachIndexed { index, stats ->
                val isBeingDragged = dragState?.source == stats.source
                val isDropTarget = dropTargetIndex == index && !isBeingDragged
                val translation = if (isBeingDragged) {
                    dragState?.dragOffset ?: Offset.Zero
                } else {
                    Offset.Zero
                }

                key(stats.source) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(
                                when {
                                    isBeingDragged -> 3f
                                    isDropTarget -> 1f
                                    else -> 0f
                                }
                            )
                            .onGloballyPositioned { coordinates ->
                                itemBounds[stats.source] = CardGridBounds(
                                    topLeft = coordinates.positionInParent(),
                                    width = coordinates.size.width,
                                    height = coordinates.size.height
                                )
                            }
                            .graphicsLayer {
                                translationX = translation.x
                                translationY = translation.y
                            }
                    ) {
                        ApiUsageCard(
                            source = stats.source,
                            apiName = stats.apiName,
                            quotas = stats.quotas,
                            showUsageDetails = stats.source != ApiSource.ANTHROPIC,
                            isRefreshing = stats.source in refreshingSources,
                            isMinimized = stats.source in minimizedCards,
                            isBeingDragged = isBeingDragged,
                            isDragTarget = isDropTarget,
                            language = language,
                            animationDelayMillis = index * 90,
                            onRefresh = { onRefreshCard(stats.source) },
                            onOpenHistory = { onOpenHistoryCard(stats.source) },
                            onToggleMinimized = { onToggleCardMinimized(stats.source) },
                            onDragStart = {
                                dragState = CardDragState(source = stats.source)
                                dropTargetIndex = items.indexOfFirst { item -> item.source == stats.source }
                            },
                            onDrag = { dragAmount ->
                                val activeDrag = dragState
                                if (activeDrag != null && activeDrag.source == stats.source) {
                                    val updatedDrag = activeDrag.copy(
                                        dragOffset = activeDrag.dragOffset + dragAmount
                                    )
                                    dragState = updatedDrag

                                    val draggedBounds = itemBounds[stats.source]
                                    if (draggedBounds == null) {
                                        dropTargetIndex = null
                                    } else {
                                        val draggedCenter = draggedBounds.center + updatedDrag.dragOffset
                                        dropTargetIndex = resolveDropTargetIndex(
                                            orderedSources = items.map { item -> item.source },
                                            boundsBySource = itemBounds.mapValues { (source, bounds) ->
                                                CardGridSlot(
                                                    source = source,
                                                    left = bounds.topLeft.x,
                                                    top = bounds.topLeft.y,
                                                    width = bounds.width,
                                                    height = bounds.height
                                                )
                                            },
                                            draggedSource = stats.source,
                                            draggedCenter = draggedCenter
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                val activeDrag = dragState
                                val targetIndex = dropTargetIndex
                                dragState = null
                                dropTargetIndex = null

                                if (activeDrag != null && activeDrag.source == stats.source && targetIndex != null) {
                                    onMoveCardToIndex(stats.source, targetIndex)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(width = constraints.maxWidth, height = 0) {}
        }

        val columns = if (constraints.maxWidth < compactThresholdPx) 1 else 2
        val totalSpacing = spacingPx * (columns - 1)
        val itemWidth = ((constraints.maxWidth - totalSpacing).coerceAtLeast(0)) / columns
        val defaultChildConstraints = constraints.copy(
            minWidth = itemWidth,
            maxWidth = itemWidth,
            minHeight = 0
        )
        val fullRowConstraints = constraints.copy(
            minWidth = constraints.maxWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0
        )
        val rows = buildList {
            var index = 0

            while (index < measurables.size) {
                val remainingItems = measurables.size - index
                val isTrailingSingleCardRow = columns > 1 && remainingItems == 1
                val rowItemCount = if (isTrailingSingleCardRow) 1 else minOf(columns, remainingItems)
                val rowConstraints = if (isTrailingSingleCardRow) {
                    fullRowConstraints
                } else {
                    defaultChildConstraints
                }
                val rowPlaceables = List(rowItemCount) { rowIndex ->
                    measurables[index + rowIndex].measure(rowConstraints)
                }

                add(CardGridRow(placeables = rowPlaceables))
                index += rowItemCount
            }
        }
        val rowHeights = rows.map { row -> row.height }
        val layoutHeight = rowHeights.sum() + spacingPx * (rowHeights.size - 1).coerceAtLeast(0)

        layout(width = constraints.maxWidth, height = layoutHeight) {
            var yPosition = 0

            rows.forEach { row ->
                row.placeables.forEachIndexed { columnIndex, placeable ->
                    val xPosition = if (row.placeables.size == 1 && columns > 1) {
                        0
                    } else {
                        columnIndex * (itemWidth + spacingPx)
                    }

                    placeable.placeRelative(
                        x = xPosition,
                        y = yPosition
                    )
                }

                yPosition += row.height + spacingPx
            }
        }
    }
}

private data class CardDragState(
    val source: ApiSource,
    val dragOffset: Offset = Offset.Zero
)

private data class CardGridBounds(
    val topLeft: Offset,
    val width: Int,
    val height: Int
) {
    val center: Offset
        get() = Offset(
            x = topLeft.x + width / 2f,
            y = topLeft.y + height / 2f
        )
}

private data class CardGridRow(
    val placeables: List<androidx.compose.ui.layout.Placeable>
) {
    val height: Int
        get() = placeables.maxOf { placeable -> placeable.height }
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
                    "A atualização automática está pronta para este ambiente Windows."
                } else {
                    "A atualização automática não está disponível nesta plataforma. Atualize manualmente pela release publicada."
                }
            } else {
                if (state.automaticInstallSupported) {
                    "Automatic updating is ready for this Windows environment."
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
                "Baixando automaticamente o instalador do Windows para preparar a atualização."
            } else {
                "Automatically downloading the Windows installer to prepare the update."
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
                actionLabel = if (state.update?.windowsInstallerDownloadUrl != null) {
                    if (language == AppLanguage.PT) "Tentar novamente" else "Retry"
                } else {
                    null
                },
                showRetryAction = state.update?.windowsInstallerDownloadUrl != null
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
