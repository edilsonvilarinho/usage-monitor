package com.usagemonitor

import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.ClaimTeamKeyForAccountUseCase
import com.usagemonitor.domain.usecase.CreateTeamKeyUseCase
import com.usagemonitor.domain.usecase.DeleteTeamAccountUseCase
import com.usagemonitor.domain.usecase.GetActiveCliSessionPulsesUseCase
import com.usagemonitor.domain.usecase.GetActiveTeamSessionPulseUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamOverviewUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetAntigravityUsageUseCase
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetCliUsageBreakdownUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetCursorUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetGeminiUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetMonthlyBudgetStatusUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeGoUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenRouterUsageUseCase
import com.usagemonitor.domain.usecase.GetTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageTrendUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageUseCase
import com.usagemonitor.domain.usecase.ListBlockedTeamAccountsUseCase
import com.usagemonitor.domain.usecase.ListTeamKeysUseCase
import com.usagemonitor.domain.usecase.PushTeamUsageUseCase
import com.usagemonitor.domain.usecase.RegenerateTeamKeyUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamMemberUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamSessionUseCase
import com.usagemonitor.domain.usecase.RevokeTeamKeyUseCase
import com.usagemonitor.domain.usecase.TouchTeamPresenceUseCase
import com.usagemonitor.domain.usecase.UnblockTeamAccountUseCase
import com.usagemonitor.domain.usecase.UnclaimTeamKeyAccountUseCase
import com.usagemonitor.domain.usecase.UpdateTeamKeyUseCase
import com.usagemonitor.domain.usecase.ValidateAdminTokenUseCase
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsViewModel
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import com.usagemonitor.presentation.viewmodel.SessionPulseViewModel
import com.usagemonitor.presentation.viewmodel.TeamKeysAdminViewModel
import com.usagemonitor.presentation.viewmodel.TeamPresenceViewModel
import com.usagemonitor.presentation.viewmodel.TeamUsageViewModel
import com.usagemonitor.presentation.viewmodel.UsageAlertViewModel
import com.usagemonitor.update.AutoUpdateController
import com.usagemonitor.update.writeUpdateScheduleFailureReceipt
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

/**
 * Os view models e o serviço de envio do time, montados sobre o [AppGraph].
 *
 * Também é o dono **único** do encerramento ordenado. Antes havia três cópias
 * dele em `runUsageMonitor` — o gancho de desligamento da JVM, o `onDispose` e a
 * saída pela janela —, e elas tinham divergido: o gancho não fechava o índice do
 * Codex e a saída pela janela não fechava o registro de perfis.
 */
internal class AppViewModels(
    private val graph: AppGraph,
    autoUpdate: AutoUpdateController
) {
    private val breadcrumbs = graph.breadcrumbs
    private val shutdownStarted = AtomicBoolean(false)

    /**
     * Há sessão CLI rodando — a cadência de 60 s da coleta (issue #269). O
     * semáforo que sabe disso nasce depois do painel, então o painel recebe esta
     * ponte e o laço de [busyBridgeScope] a alimenta.
     */
    private val cliBusy = MutableStateFlow(false)
    private val busyBridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dashboard = DashboardViewModel(
        getAnthropicUsage = GetAnthropicUsageUseCase(graph.anthropicRepository),
        getMiniMaxUsage = GetMiniMaxUsageUseCase(graph.minimaxRepository),
        getCodexUsage = GetCodexUsageUseCase(graph.codexRepository),
        getDeepSeekUsage = GetDeepSeekUsageUseCase(graph.deepSeekRepository),
        getKiloUsage = GetKiloUsageUseCase(graph.kiloRepository),
        getGeminiUsage = GetGeminiUsageUseCase(graph.geminiRepository),
        getCursorUsage = GetCursorUsageUseCase(graph.cursorRepository),
        getAntigravityUsage = GetAntigravityUsageUseCase(graph.antigravityRepository),
        getOpenCodeUsage = GetOpenCodeUsageUseCase(graph.openCodeRepository),
        getOpenCodeGoUsage = GetOpenCodeGoUsageUseCase(graph.openCodeGoRepository),
        getOpenRouterUsage = GetOpenRouterUsageUseCase(graph.openRouterRepository),
        enabledApis = graph.enabledApis,
        recordUsageSnapshot = graph.recordUsageSnapshot,
        getUsageHistory = graph.getUsageHistory,
        getCachedDashboardStats = graph.getCachedDashboardStats,
        saveDashboardCache = graph.saveDashboardCache,
        checkForAppUpdate = CheckForAppUpdateUseCase(graph.appUpdateRepository),
        appUpdateReleaseOpener = graph.appUpdateReleaseOpener,
        appUpdateInstaller = autoUpdate.installer,
        autoUpdateEnabled = autoUpdate.enabled,
        receiveBetaUpdates = autoUpdate.receiveBetaUpdates,
        onRestartAndUpdateRequested = { autoUpdate.requestRestart() },
        onUpdateScheduleFailure = ::writeUpdateScheduleFailureReceipt,
        currentAppVersion = CURRENT_APP_VERSION,
        spikeFactorProvider = { graph.alertSettingsFlow.value.effectiveSpikeFactor },
        isAppVisible = graph.isAppVisible,
        isBusy = cliBusy,
        anthropicProfiles = graph.enabledAnthropicProfiles,
        codexProfiles = graph.enabledCodexProfiles,
        persistedNextRefreshAt = graph.persistedNextRefreshAt,
        onNextRefreshAtChanged = { instant ->
            graph.settings.putLong(NEXT_REFRESH_AT_KEY, instant.toEpochMilliseconds())
        },
        persistedRateLimitBackoffs = readPersistedRateLimitBackoffs(graph.settings),
        onRateLimitBackoffChanged = { backoffs -> persistRateLimitBackoffs(graph.settings, backoffs) },
        breadcrumbs = breadcrumbs
    )

    val history = HistoryViewModel(
        getUsageHistory = graph.getUsageHistory,
        enabledApis = graph.enabledApis,
        breadcrumbs = breadcrumbs
    )

    // A indexação corre em background desde o arranque, em `Dispatchers.IO`: o
    // Claude Code apaga transcripts antigos, e depender de o usuário abrir a
    // janela antes disso perderia o histórico. A lista em si só carrega quando a
    // janela abre (`autoLoad = false`).
    val cliSessions = CliSessionsViewModel(
        getCliSessions = GetCliSessionsUseCase(graph.cliSessionRepository),
        getCliSessionDetail = GetCliSessionDetailUseCase(graph.cliSessionRepository),
        syncCliSessionIndex = graph.syncCliSessionIndex,
        getCliUsageBreakdown = GetCliUsageBreakdownUseCase(graph.cliSessionRepository),
        exportWriter = graph.usageExportWriter,
        exportEncoder = DefaultUsageExportEncoder,
        getMonthlyBudgetStatus = GetMonthlyBudgetStatusUseCase(graph.cliSessionRepository),
        getStalledCliSessions = graph.getStalledCliSessions,
        stallThresholdProvider = { graph.alertSettingsFlow.value.effectiveStallThresholdMillis },
        autoLoad = false,
        backgroundIndexIntervalMillis = CLI_SESSION_INDEX_INTERVAL_MILLIS,
        liveIntervalMillis = CLI_SESSION_LIVE_INTERVAL_MILLIS,
        breadcrumbs = breadcrumbs
    )

    val codexCliSessions = CodexCliSessionsViewModel(
        getSessions = GetCodexCliSessionsUseCase(graph.codexCliSessionRepository),
        getDetail = GetCodexCliSessionDetailUseCase(graph.codexCliSessionRepository),
        exportWriter = graph.usageExportWriter,
        exportEncoder = DefaultUsageExportEncoder,
        liveIntervalMillis = CLI_SESSION_LIVE_INTERVAL_MILLIS,
        autoLoad = false,
        breadcrumbs = breadcrumbs
    )

    val teamUsage = TeamUsageViewModel(
        getTeamUsage = GetTeamUsageUseCase(graph.teamUsageRepository),
        getTeamSessionDetail = GetTeamSessionDetailUseCase(graph.teamUsageRepository),
        getAdminOverview = GetAdminTeamOverviewUseCase(graph.teamAdminRepository),
        getAdminTeamSessionDetail = GetAdminTeamSessionDetailUseCase(graph.teamAdminRepository),
        removeAdminTeamMember = RemoveAdminTeamMemberUseCase(graph.teamAdminRepository),
        removeAdminTeamSession = RemoveAdminTeamSessionUseCase(graph.teamAdminRepository),
        getTeamUsageTrend = GetTeamUsageTrendUseCase(graph.teamUsageRepository),
        exportWriter = graph.usageExportWriter,
        liveIntervalMillis = TEAM_USAGE_LIVE_INTERVAL_MILLIS,
        breadcrumbs = breadcrumbs
    )

    val teamPresence = TeamPresenceViewModel(
        getTeamPresence = GetTeamPresenceUseCase(graph.teamUsageRepository, graph.teamServerClockOffset),
        getAdminTeamPresence = GetAdminTeamPresenceUseCase(graph.teamAdminRepository, graph.teamServerClockOffset),
        removeTeamMember = RemoveAdminTeamMemberUseCase(graph.teamAdminRepository),
        deleteTeamAccount = DeleteTeamAccountUseCase(graph.teamAdminRepository),
        liveIntervalMillis = TEAM_PRESENCE_LIVE_INTERVAL_MILLIS,
        breadcrumbs = breadcrumbs
    )

    // Semáforo dos botões dos cards: lê o índice local de todas as contas e, para
    // as que participam do time, o servidor. Reusa o mesmo `syncCliSessionIndex`
    // das outras telas — o índice é um só.
    val sessionPulse = SessionPulseViewModel(
        getCliPulses = GetActiveCliSessionPulsesUseCase(graph.cliSessionRepository),
        getTeamPulse = GetActiveTeamSessionPulseUseCase(graph.teamUsageRepository),
        syncCliSessionIndex = graph.syncCliSessionIndex,
        // Detecção de sessão sem resposta: mora aqui porque a passada local
        // deste laço continua com a janela minimizada, que é o destinatário
        // do aviso.
        getStalledSessions = graph.getStalledCliSessions,
        // O Codex não entra no índice do Claude: a execução dele é lida do
        // estado do app desktop e dos rollouts, como no Codenotch.
        codexActivity = { nowMillis ->
            runCatching {
                withContext(Dispatchers.IO) { graph.codexActivityDataSource.isActive(nowMillis) }
            }
        },
        stallThresholdProvider = { graph.alertSettingsFlow.value.effectiveStallThresholdMillis },
        teamTargetsProvider = {
            buildSessionPulseTargets(registry = graph.profileRegistry, settings = graph.teamSettingsFlow.value)
        },
        isAppVisible = graph.isAppVisible,
        intervalMillis = SESSION_PULSE_INTERVAL_MILLIS,
        breadcrumbs = breadcrumbs
    )

    init {
        // Claude CLI pelo índice, Codex pela sonda própria: é o mesmo sinal que
        // acende o arco de sessão ativa da HUD.
        busyBridgeScope.launch {
            sessionPulse.activeTargets.collect { active -> cliBusy.value = active.isNotEmpty() }
        }
    }

    val usageAlert = UsageAlertViewModel(
        dashboardState = dashboard.uiState,
        cliPulses = sessionPulse.cliPulses,
        alertSettings = graph.alertSettingsFlow,
        stalledSessions = sessionPulse.stalledSessions,
        spikes = dashboard.spikes
    )

    val teamKeys = TeamKeysAdminViewModel(
        listKeys = ListTeamKeysUseCase(graph.teamAdminRepository),
        createKey = CreateTeamKeyUseCase(graph.teamAdminRepository),
        updateKey = UpdateTeamKeyUseCase(graph.teamAdminRepository),
        regenerateKey = RegenerateTeamKeyUseCase(graph.teamAdminRepository),
        revokeKey = RevokeTeamKeyUseCase(graph.teamAdminRepository),
        unclaimAccount = UnclaimTeamKeyAccountUseCase(graph.teamAdminRepository),
        deleteAccount = DeleteTeamAccountUseCase(graph.teamAdminRepository),
        listBlockedAccounts = ListBlockedTeamAccountsUseCase(graph.teamAdminRepository),
        unblockAccount = UnblockTeamAccountUseCase(graph.teamAdminRepository),
        breadcrumbs = breadcrumbs
    )

    val validateAdminToken = ValidateAdminTokenUseCase(graph.teamAdminRepository)
    val claimTeamKeyForAccount = ClaimTeamKeyForAccountUseCase(graph.teamAdminRepository)

    // O envio roda com a janela do time fechada: se dependesse dela, o consumo de
    // quem nunca abre a tela nunca chegaria aos colegas.
    val teamSync = TeamSyncService(
        syncStateDataSource = graph.teamSyncStateDataSource,
        pushTeamUsage = PushTeamUsageUseCase(graph.teamUsageRepository),
        settingsProvider = { graph.teamSettingsFlow.value },
        targetsProvider = { buildTeamSyncTargets(graph.profileRegistry) },
        // Sem indexar aqui, a latência do time não seria o intervalo deste
        // serviço e sim o do laço de background (10min): ele só envia o que
        // já está no índice.
        ensureIndexFresh = { graph.syncCliSessionIndex() },
        // O heartbeat que alimenta a janela de presença. Sai em toda passada,
        // inclusive quando não há turno novo — é o que separa "app aberto" de
        // "houve consumo".
        touchTeamPresence = TouchTeamPresenceUseCase(graph.teamUsageRepository),
        breadcrumbs = breadcrumbs
    )

    /**
     * Para tudo, fecha bancos e clientes e só então entrega a atualização
     * pendente. Idempotente: o gancho da JVM, o `onDispose` e a saída pela janela
     * podem chamar os três, e só o primeiro faz alguma coisa.
     */
    fun shutdown(singleInstanceGuard: SingleInstanceGuard) {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return
        }
        busyBridgeScope.cancel()
        dashboard.onDestroy()
        history.onDestroy()
        cliSessions.onDestroy()
        codexCliSessions.onDestroy()
        teamUsage.onDestroy()
        teamPresence.onDestroy()
        sessionPulse.onDestroy()
        usageAlert.onDestroy()
        teamKeys.onDestroy()
        teamSync.onDestroy()
        graph.profileRegistry.close()
        graph.httpClient.close()
        graph.cursorHttpClient.close()
        graph.usageHistoryDataSource.close()
        graph.cliSessionDataSource.close()
        graph.codexCliSessionDataSource.close()
        graph.openCodeUsageDataSource.close()
        graph.kiloUsageDataSource.close()
        singleInstanceGuard.close()
        // Por último, e não primeiro: o instalador espera este processo sair de
        // qualquer forma, mas entregar o pacote antes de o SQLite fechar seria
        // abrir uma janela para a troca de arquivos correr contra a escrita do banco.
        dashboard.scheduleUpdateOnExit()
    }
}

/** Intervalo da indexação de transcripts em background, igual ao polling do dashboard. */
internal const val CLI_SESSION_INDEX_INTERVAL_MILLIS = 10 * 60 * 1_000L

/**
 * Cadência da janela de sessões aberta. As sessões descrevem o Claude Code
 * rodando neste instante, então a tela se atualiza sozinha; uma passada custa um
 * `walk` sobre os `projects/` e um `SELECT` no índice.
 */
internal const val CLI_SESSION_LIVE_INTERVAL_MILLIS = 5_000L

/**
 * Cadência da leitura do servidor de time com a janela aberta.
 *
 * Igual à da janela de sessões da máquina: as duas telas fazem a mesma promessa
 * ao usuário. A latência real com que um colega aparece é dominada pelo
 * intervalo de envio da máquina dele, não por este.
 */
internal const val TEAM_USAGE_LIVE_INTERVAL_MILLIS = 5_000L

/**
 * Cadência da janela de presença.
 *
 * A mesma das outras duas janelas ao vivo: a promessa ao usuário é idêntica, e a
 * latência real com que alguém aparece é dominada pelo heartbeat de 30s da
 * máquina dele, não por este intervalo.
 */
internal const val TEAM_PRESENCE_LIVE_INTERVAL_MILLIS = 5_000L

/**
 * Cadência do semáforo de sessões dos botões dos cards.
 *
 * A janela avaliada é de minutos, então meio minuto é precisão de sobra — e
 * mantém o tráfego para o servidor de time no mesmo patamar do envio, que já roda
 * de 30 em 30 segundos.
 */
internal const val SESSION_PULSE_INTERVAL_MILLIS = 30_000L
