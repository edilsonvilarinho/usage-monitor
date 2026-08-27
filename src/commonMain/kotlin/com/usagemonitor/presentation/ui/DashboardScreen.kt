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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.PersistentApiWarningBanner
import com.usagemonitor.presentation.ui.components.RefreshWarningDialog
import com.usagemonitor.presentation.ui.components.ResponsiveDashboardCardGrid
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.presentation.viewmodel.UiState

/**
 * Folga somada à espera para o relógio já ter cruzado o `periodEndAt` ao acordar.
 * O reset da fonte não é instantâneo; acordar no milissegundo exato não ajuda.
 */
private const val QUOTA_EXPIRY_MARGIN_MILLIS = 1_000L

/** Divisória de 1dp mais a barra de estado de 30dp do rodapé do Dashboard. */
private val DASHBOARD_FOOTER_HEIGHT = 31.dp

/**
 * Próximo `periodEndAt` ainda no futuro entre as cotas na tela.
 *
 * Cotas sem reset conhecido ficam de fora: o `periodEndAt` delas é o sentinela
 * distante que o mapper usa na ausência de `resets_at`.
 */
private fun nextQuotaExpiry(stats: List<ApiUsageStats>, now: Instant): Instant? {
    var earliest: Instant? = null
    for (item in stats) {
        for (quota in item.quotas) {
            if (!quota.hasKnownResetAt || quota.periodEndAt <= now) {
                continue
            }
            val currentEarliest = earliest
            if (currentEarliest == null || quota.periodEndAt < currentEarliest) {
                earliest = quota.periodEndAt
            }
        }
    }
    return earliest
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    appVersion: String,
    language: AppLanguage,
    cardOrder: List<UsageTargetKey>,
    minimizedCards: Set<UsageTargetKey>,
    onMoveCardToIndex: (UsageTargetKey, Int) -> Unit,
    onToggleCardMinimized: (UsageTargetKey) -> Unit,
    onOpenHistory: (ApiSource, UsageAccountKey?) -> Unit,
    onOpenSettings: () -> Unit,
    onRequiredHeightChanged: (Dp) -> Unit = {},
    onOpenCliSessions: (UsageTargetKey) -> Unit = {},
    onOpenTeamUsage: (UsageTargetKey) -> Unit = {},
    /** Presença da conta do card — a porta do integrante comum. */
    onOpenTeamPresence: (UsageTargetKey) -> Unit = {},
    /** Perfis que participam do time; vazio esconde o botão de todos os cards. */
    teamEnabledProfileIds: Set<String> = emptySet(),
    /**
     * Semáforo de saúde das sessões em curso, por card.
     *
     * Faz o botão correspondente piscar na cor da severidade enquanto houver
     * sessão com interação recente pedindo atenção. Vazio deixa tudo em repouso.
     */
    cliSessionPulses: Map<UsageTargetKey, SessionPulse> = emptyMap(),
    teamSessionPulses: Map<UsageTargetKey, SessionPulse> = emptyMap(),
    /** `null` esconde o botão de todas as contas — só quem administra o recebe. */
    onOpenAdminOverview: (() -> Unit)? = null,
    /**
     * Presença de **todas** as contas do servidor — a porta do administrador.
     *
     * Separada de [onOpenTeamPresence] porque os escopos são outros: aquela é a
     * conta de um card, esta é o servidor inteiro. Um parâmetro só obrigaria a
     * inventar um `UsageTargetKey` que não corresponde a card nenhum.
     */
    onOpenTeamPresenceOverview: (() -> Unit)? = null,
    /**
     * `false` no modo somente cards: a barra de estado sai da janela.
     *
     * Com ela saem a versão, a contagem regressiva e as quatro ações do rodapé —
     * inclusive a engrenagem. Quem liga o modo recebe as saídas por escrito no
     * próprio interruptor, e a bandeja continua abrindo as Configurações.
     */
    showFooter: Boolean = true,
    modifier: Modifier = Modifier,
    countdownUpdatesEnabled: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val nextRefreshAt by viewModel.nextRefreshAt.collectAsState()
    val refreshingTargets by viewModel.refreshingTargets.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRefreshAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // O vencimento de uma janela de cota é uma virada de relógio, não de dados:
    // sem este laço o card repetiria o reset já passado como se fosse futuro até
    // alguma coleta trazer números diferentes. A espera é até o próximo
    // `periodEndAt`, não um tique periódico — nada recompõe à toa.
    var quotaClock by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(uiState, countdownUpdatesEnabled) {
        if (!countdownUpdatesEnabled) {
            return@LaunchedEffect
        }
        val visibleStats = (uiState as? UiState.Success)?.data ?: return@LaunchedEffect
        while (true) {
            val current = Clock.System.now()
            quotaClock = current
            val nextExpiry = nextQuotaExpiry(visibleStats, current) ?: return@LaunchedEffect
            delay((nextExpiry - current).inWholeMilliseconds + QUOTA_EXPIRY_MARGIN_MILLIS)
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { toast ->
            val msg = decodeToastMessage(toast, language)
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.clearToast()
        }
    }

    Scaffold(
        bottomBar = {
            if (!showFooter) {
                return@Scaffold
            }
            FooterBar(
                appVersion = appVersion,
                language = language,
                nextRefreshAt = nextRefreshAt,
                onRefresh = { pendingRefreshAction = { viewModel.refresh() } },
                onOpenSettings = onOpenSettings,
                countdownUpdatesEnabled = countdownUpdatesEnabled,
                onOpenAdminOverview = onOpenAdminOverview,
                onOpenTeamPresence = onOpenTeamPresenceOverview
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
                            onOpenRelease = { viewModel.openUpdateReleasePage() },
                            onRestartAndUpdate = { viewModel.restartAndUpdateNow() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
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
                                    onRetryTarget = { target -> viewModel.refresh(target) }
                                )
                                is UiState.Success -> SuccessContent(
                                    apiStatsList = state.data,
                                    now = quotaClock,
                                    partialErrors = state.errors,
                                    riskSummaries = state.riskSummaries,
                                    refreshingTargets = refreshingTargets,
                                    cardOrder = cardOrder,
                                    minimizedCards = minimizedCards,
                                    language = language,
                                    onRefreshCard = { target -> pendingRefreshAction = { viewModel.refresh(target) } },
                                    onMoveCardToIndex = onMoveCardToIndex,
                                    onToggleCardMinimized = onToggleCardMinimized,
                                    onRequiredHeightChanged = { contentHeight ->
                                        val footerHeight = if (showFooter) {
                                            DASHBOARD_FOOTER_HEIGHT
                                        } else {
                                            0.dp
                                        }
                                        onRequiredHeightChanged(contentHeight + footerHeight)
                                    },
                                    onOpenHistoryCard = onOpenHistory,
                                    onOpenCliSessionsCard = onOpenCliSessions,
                                    onOpenTeamUsageCard = onOpenTeamUsage,
                                    onOpenTeamPresenceCard = onOpenTeamPresence,
                                    teamEnabledProfileIds = teamEnabledProfileIds,
                                    cliSessionPulses = cliSessionPulses,
                                    teamSessionPulses = teamSessionPulses,
                                    onRetryTarget = { target -> viewModel.refresh(target) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRefreshAction?.let { action ->
        RefreshWarningDialog(
            language = language,
            onConfirm = {
                action()
                pendingRefreshAction = null
            },
            onDismiss = { pendingRefreshAction = null }
        )
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
            AppButton(
                label = actionLabel,
                onClick = onOpenSettings,
                tone = AppButtonTone.PRIMARY
            )
        }
    }
}

/**
 * Carregando: esqueleto **estático**.
 *
 * O `ShimmerBox` sai daqui. Ele é a única animação infinita da app e continua
 * existindo, mas era usado justamente na tela de abertura — a primeira coisa que
 * um teste de componente do dashboard encontra, e a que trava o `waitForIdle`.
 */
@Composable
private fun LoadingContent(language: AppLanguage) {
    AppLoadingState(
        message = if (language == AppLanguage.PT) "Carregando dados das APIs..." else "Loading API data...",
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ErrorContent(
    errors: List<UiApiError>,
    language: AppLanguage,
    onRetryAll: () -> Unit,
    onRetryTarget: (UsageTargetKey) -> Unit
) {
    val warnings = errors.mapNotNull { error -> warningFor(error = error, language = language) }
    val genericErrors = errors.filterNot { error ->
        error.isConfigurationIssue || error.isRateLimitIssue || error.isServiceUnavailableIssue
    }

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
                        warning = warning,
                        onRetryTarget = onRetryTarget
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (genericErrors.isNotEmpty() || warnings.isEmpty()) {
                AppErrorState(
                    message = if (language == AppLanguage.PT) "Erro ao carregar dados" else "Failed to load data",
                    // Os erros genéricos entram como uma linha só: são as fontes
                    // que falharam sem motivo reconhecido, e cada uma tem uma
                    // frase própria vinda da camada de dados.
                    detail = genericErrors.joinToString("\n") { error -> error.formattedMessage }
                        .takeIf { text -> text.isNotEmpty() },
                    retryLabel = if (language == AppLanguage.PT) "Tentar novamente" else "Retry",
                    onRetry = onRetryAll
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    apiStatsList: List<ApiUsageStats>,
    /** Instante contra o qual o vencimento das janelas de cota é medido. */
    now: Instant,
    partialErrors: List<UiApiError>,
    riskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>,
    refreshingTargets: Set<UsageTargetKey>,
    cardOrder: List<UsageTargetKey>,
    minimizedCards: Set<UsageTargetKey>,
    language: AppLanguage,
    onRefreshCard: (UsageTargetKey) -> Unit,
    onMoveCardToIndex: (UsageTargetKey, Int) -> Unit,
    onToggleCardMinimized: (UsageTargetKey) -> Unit,
    onRequiredHeightChanged: (Dp) -> Unit = {},
    onOpenHistoryCard: (ApiSource, UsageAccountKey?) -> Unit,
    onOpenCliSessionsCard: (UsageTargetKey) -> Unit = {},
    onOpenTeamUsageCard: (UsageTargetKey) -> Unit = {},
    onOpenTeamPresenceCard: (UsageTargetKey) -> Unit = {},
    teamEnabledProfileIds: Set<String> = emptySet(),
    cliSessionPulses: Map<UsageTargetKey, SessionPulse> = emptyMap(),
    teamSessionPulses: Map<UsageTargetKey, SessionPulse> = emptyMap(),
    onRetryTarget: (UsageTargetKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemByTarget = apiStatsList.associateBy { stats -> stats.targetKey }
    val orderedTargets = cardOrder.toSet()
    val items = cardOrder.mapNotNull(itemByTarget::get) +
        apiStatsList.filter { stats -> stats.targetKey !in orderedTargets }
    val warnings = partialErrors.mapNotNull { error -> warningFor(error = error, language = language) }
    val genericErrors = partialErrors.filterNot { error ->
        error.isConfigurationIssue || error.isRateLimitIssue || error.isServiceUnavailableIssue
    }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        // Sem folga própria para a barra de rolagem: ela flutua sobre o padding
        // direito da grade, que é largo o bastante para não cobrir card nenhum.
        // Somada ao padding da grade, a folga antiga deixava o lado direito com
        // 28dp contra 16 do esquerdo — assimetria que a janela estreita denuncia.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(rememberModalContentHeightReporter(onRequiredHeightChanged))
            ) {
                if (warnings.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        warnings.forEach { warning ->
                            PersistentApiWarningBanner(
                                title = warning.title,
                                description = warning.description,
                                actionLabel = warning.actionLabel,
                                onAction = warningActionFor(
                                    warning = warning,
                                    onRetryTarget = onRetryTarget
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
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
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
                    refreshingTargets = refreshingTargets,
                    minimizedCards = minimizedCards,
                    riskSummaries = riskSummaries,
                    language = language,
                    onRefreshCard = onRefreshCard,
                    onMoveCardToIndex = onMoveCardToIndex,
                    onToggleCardMinimized = onToggleCardMinimized,
                    onOpenHistoryCard = onOpenHistoryCard,
                    onOpenCliSessionsCard = onOpenCliSessionsCard,
                    onOpenTeamUsageCard = onOpenTeamUsageCard,
                    onOpenTeamPresenceCard = onOpenTeamPresenceCard,
                    teamEnabledProfileIds = teamEnabledProfileIds,
                    cliSessionPulses = cliSessionPulses,
                    teamSessionPulses = teamSessionPulses,
                    now = now,
                    // 12/8 e não 16/12: numa janela estreita — que é como o app
                    // costuma ficar — a margem antiga comia largura que o card usa
                    // para caber sem quebrar as cotas em duas linhas.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
                )
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}
