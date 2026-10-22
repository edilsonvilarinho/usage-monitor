package com.usagemonitor

import com.usagemonitor.data.datasource.CollectorRegistration
import com.usagemonitor.data.datasource.LocalCollectorRegistrationDataSource
import com.usagemonitor.data.datasource.LocalConfigRequestChannel
import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.data.export.JsonUsageSnapshotEncoder
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.buildUsageSnapshot
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetActiveCliSessionPulsesUseCase
import com.usagemonitor.domain.usecase.GetActiveTeamSessionPulseUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetAntigravityUsageUseCase
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetCliUsageBreakdownUseCase
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
import com.usagemonitor.domain.usecase.PushTeamUsageUseCase
import com.usagemonitor.domain.usecase.TouchTeamPresenceUseCase
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.QuotaActivityTracker
import com.usagemonitor.presentation.viewmodel.SessionPulseViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.viewmodel.UsageAlertViewModel
import com.usagemonitor.update.AutoUpdateController
import com.usagemonitor.update.writeUpdateScheduleFailureReceipt
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * O motor de coleta, sem janela (decisão D2 da #440): coleta adaptativa, semáforo de
 * sessões, alertas, índices de sessão, envio do time, bot do Telegram e acesso web.
 *
 * O app é este motor mais as telas (`AppViewModels`); o serviço sem tela
 * (`usage-monitor service run`) é só ele. Por isso nada aqui importa Compose: o
 * `ArchitectureRulesTest` cobra, porque o serviço roda com `java.awt.headless` e sem
 * as bibliotecas gráficas que o Compose carregaria.
 *
 * O trabalho que morava no `AppBackgroundWork` (índice do Codex, ligar e desligar o
 * envio do time) vive aqui também: no serviço não há composição para segurá-lo.
 */
internal class CollectorEngine(
    private val graph: AppGraph,
    autoUpdate: AutoUpdateController,
    /** Quem roda o motor, para o terminal dizer de onde vem a leitura. */
    kind: CollectorKind,
    private val registration: LocalCollectorRegistrationDataSource = LocalCollectorRegistrationDataSource(),
    private val configRequests: LocalConfigRequestChannel = LocalConfigRequestChannel()
) {
    private val breadcrumbs = graph.breadcrumbs
    private val shutdownStarted = AtomicBoolean(false)

    init {
        // Quem chega aqui já tomou a trava: o registro só vale enquanto ela está tomada.
        registration.write(CollectorRegistration(kind, ProcessHandle.current().pid(), Clock.System.now(), CURRENT_APP_VERSION))
    }

    /**
     * Há sessão CLI rodando — a cadência de 60 s da coleta (issue #269). O
     * semáforo que sabe disso nasce depois do painel, então o painel recebe esta
     * ponte e o laço de [engineScope] a alimenta.
     */
    private val cliBusy = MutableStateFlow(false)
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        intervalMillis = SESSION_PULSE_INTERVAL_MILLIS,
        breadcrumbs = breadcrumbs
    )

    init {
        // Claude CLI pelo índice, Codex pela sonda própria: é o mesmo sinal que
        // acende o arco de sessão ativa da HUD.
        engineScope.launch {
            sessionPulse.activeTargets.collect { active -> cliBusy.value = active.isNotEmpty() }
        }
    }

    // Uso detectado pela variação da cota (#385): acende o arco das fontes sem
    // CLI local. Fica fora do `cliBusy` de propósito — ver QuotaActivityTracker.
    val quotaActivity = QuotaActivityTracker(dashboardState = dashboard.uiState)

    /** Arco de sessão ativa da HUD: CLI (Claude e Codex) mais uso detectado pela cota. */
    val hudActiveTargets: StateFlow<Set<UsageTargetKey>> =
        combine(sessionPulse.activeTargets, quotaActivity.detectedTargets) { cli, detected -> cli + detected }
            .stateIn(engineScope, SharingStarted.Eagerly, emptySet())

    /**
     * HUD pela rede local (#388): o mesmo retrato que a HUD mostra — cotas do
     * dashboard mais o arco de uso —, servido só quando o usuário liga a opção.
     */
    val webAccess = LocalWebAccessService(
        snapshotJson = { currentSnapshot()?.let(JsonUsageSnapshotEncoder::encode) }
    )

    init {
        engineScope.launch {
            graph.webAccessSettingsFlow.collect { settings -> withContext(Dispatchers.IO) { webAccess.apply(settings) } }
        }
    }

    val usageAlert = UsageAlertViewModel(
        dashboardState = dashboard.uiState,
        cliPulses = sessionPulse.cliPulses,
        alertSettings = graph.alertSettingsFlow,
        stalledSessions = sessionPulse.stalledSessions,
        spikes = dashboard.spikes
    )

    /** O retrato que a HUD mostra, para a web local (#388) e o `/status` do bot (#387). */
    fun currentSnapshot(): UsageSnapshot? {
        val success = dashboard.uiState.value as? UiState.Success ?: return null
        return buildUsageSnapshot(success.data, hudActiveTargets.value, Clock.System.now(), success.riskSummaries)
    }

    /**
     * Bot do Telegram (#387): repassa os alertas da bandeja e atende comandos das
     * conversas pareadas. Desligado até o usuário ligar e colar o token.
     */
    val telegramBot = createTelegramBot(graph, dashboard, usageAlert.alerts, ::currentSnapshot).also { service -> service.start() }

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

    init {
        // O índice do Codex continua sendo atualizado com a janela fechada. A
        // operação já muda para Dispatchers.IO no datasource.
        engineScope.launch {
            while (isActive) {
                graph.codexCliSessionDataSource.syncIndex()
                graph.codexIndexSyncedAt.value = Clock.System.now()
                delay(CODEX_CLI_SESSION_INDEX_INTERVAL_MILLIS)
            }
        }
        // O envio do time segue a integração ligada ou desligada, de onde quer que
        // ela mude (Configurações, terminal).
        engineScope.launch {
            graph.teamSettingsFlow.map { settings -> settings.isActive }.distinctUntilChanged().collect { active ->
                if (active) teamSync.start() else teamSync.stop()
            }
        }
    }

    /**
     * As mudanças de configuração, pelo mesmo caminho para as Configurações, o bot e o
     * terminal (decisão D1 da #440). O coletor é o único escritor enquanto vive.
     */
    val configApplier = ConfigApplier(graph.store, ConfigHooks(refreshSource = { source -> dashboard.refresh(source) }))

    private val _appliedConfigChanges = MutableSharedFlow<ConfigChange>(extraBufferCapacity = CONFIG_CHANGE_BUFFER)

    /** As mudanças pedidas pelo terminal e já aplicadas: o app atualiza o que mora na tela. */
    val appliedConfigChanges: SharedFlow<ConfigChange> = _appliedConfigChanges.asSharedFlow()

    init {
        // Os pedidos do terminal (`control/requests/`). Atendidos um a um, na ordem de chegada.
        engineScope.launch(Dispatchers.IO) {
            configRequests.discardStale()
            while (isActive) {
                serveConfigRequests(configRequests, configApplier) { change -> _appliedConfigChanges.tryEmit(change) }
                delay(CONFIG_REQUEST_POLL_MILLIS)
            }
        }
    }

    /**
     * Para o motor, fecha bancos e clientes, solta a trava e só então entrega a
     * atualização pendente. Idempotente: o gancho da JVM, o `onDispose` e a saída
     * pela janela (ou o `SIGTERM` do serviço) podem chamar, e só o primeiro faz algo.
     * As telas param antes, em `AppViewModels.shutdown`.
     */
    fun shutdown(singleInstanceGuard: SingleInstanceGuard) {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return
        }
        engineScope.cancel()
        dashboard.onDestroy()
        cliSessions.onDestroy()
        sessionPulse.onDestroy()
        quotaActivity.onDestroy()
        webAccess.stop()
        telegramBot.onDestroy()
        usageAlert.onDestroy()
        teamSync.onDestroy()
        graph.store.close()
        graph.httpClient.close()
        graph.cursorHttpClient.close()
        graph.usageHistoryDataSource.close()
        graph.cliSessionDataSource.close()
        graph.codexCliSessionDataSource.close()
        graph.openCodeUsageDataSource.close()
        graph.kiloUsageDataSource.close()
        registration.clear()
        singleInstanceGuard.close()
        // Por último, e não primeiro: o instalador espera este processo sair de
        // qualquer forma, mas entregar o pacote antes de o SQLite fechar seria
        // abrir uma janela para a troca de arquivos correr contra a escrita do banco.
        dashboard.scheduleUpdateOnExit()
    }
}

/** De quanto em quanto o coletor olha os pedidos do terminal; o terminal espera até 10 s. */
internal const val CONFIG_REQUEST_POLL_MILLIS = 300L

private const val CONFIG_CHANGE_BUFFER = 16

/** Intervalo da indexação de transcripts em background, igual ao polling do dashboard. */
internal const val CLI_SESSION_INDEX_INTERVAL_MILLIS = 10 * 60 * 1_000L

/** Intervalo da indexação dos rollouts do Codex, com ou sem janela. */
internal const val CODEX_CLI_SESSION_INDEX_INTERVAL_MILLIS = 10 * 60 * 1_000L

/**
 * Cadência da janela de sessões aberta. As sessões descrevem o Claude Code
 * rodando neste instante, então a tela se atualiza sozinha; uma passada custa um
 * `walk` sobre os `projects/` e um `SELECT` no índice.
 */
internal const val CLI_SESSION_LIVE_INTERVAL_MILLIS = 5_000L

/**
 * Cadência do semáforo de sessões dos botões dos cards.
 *
 * A janela avaliada é de minutos, então meio minuto é precisão de sobra — e
 * mantém o tráfego para o servidor de time no mesmo patamar do envio, que já roda
 * de 30 em 30 segundos.
 */
internal const val SESSION_PULSE_INTERVAL_MILLIS = 30_000L
