package com.usagemonitor

import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.usecase.BuildModelComparisonUseCase
import com.usagemonitor.domain.usecase.ClaimTeamKeyForAccountUseCase
import com.usagemonitor.domain.usecase.CreateTeamKeyUseCase
import com.usagemonitor.domain.usecase.DeleteTeamAccountUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamOverviewUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageTrendUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageUseCase
import com.usagemonitor.domain.usecase.ListBlockedTeamAccountsUseCase
import com.usagemonitor.domain.usecase.ListTeamKeysUseCase
import com.usagemonitor.domain.usecase.RegenerateTeamKeyUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamMemberUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamSessionUseCase
import com.usagemonitor.domain.usecase.RevokeTeamKeyUseCase
import com.usagemonitor.domain.usecase.UnblockTeamAccountUseCase
import com.usagemonitor.domain.usecase.UnclaimTeamKeyAccountUseCase
import com.usagemonitor.domain.usecase.UpdateTeamKeyUseCase
import com.usagemonitor.domain.usecase.ValidateAdminTokenUseCase
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsViewModel
import com.usagemonitor.presentation.viewmodel.ComparisonViewModel
import com.usagemonitor.presentation.viewmodel.AnalysisViewModel
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import com.usagemonitor.presentation.viewmodel.TeamKeysAdminViewModel
import com.usagemonitor.presentation.viewmodel.TeamPresenceViewModel
import com.usagemonitor.presentation.viewmodel.TeamUsageViewModel
import com.usagemonitor.update.AutoUpdateController
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.map

/**
 * O motor de coleta ([CollectorEngine]) mais os view models das telas.
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

    /** O mesmo motor que o serviço sem tela roda sozinho (#440). */
    val engine = CollectorEngine(graph, autoUpdate, CollectorKind.APP)
    val dashboard get() = engine.dashboard
    val cliSessions get() = engine.cliSessions
    val sessionPulse get() = engine.sessionPulse
    val quotaActivity get() = engine.quotaActivity
    val hudActiveTargets get() = engine.hudActiveTargets
    val webAccess get() = engine.webAccess
    val usageAlert get() = engine.usageAlert
    val telegramBot get() = engine.telegramBot
    val teamSync get() = engine.teamSync

    fun currentSnapshot(): UsageSnapshot? = engine.currentSnapshot()

    val history = HistoryViewModel(
        getUsageHistory = graph.getUsageHistory,
        enabledApis = graph.enabledApis,
        breadcrumbs = breadcrumbs,
        exportWriter = graph.usageExportWriter
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

    // Comparação entre modelos e APIs (#386): lê sob demanda, ao abrir a janela.
    val comparison = ComparisonViewModel(
        buildComparison = BuildModelComparisonUseCase(graph.cliSessionRepository, graph.codexCliSessionRepository),
        dashboardState = dashboard.uiState,
        exportWriter = graph.usageExportWriter,
        breadcrumbs = breadcrumbs
    )

    // Análise da sessão (rodada AB): a mesma leitura do `/analise` do bot, ao vivo com a
    // janela visível (rodada AD) — relê quando a coleta da conta traz leitura nova.
    val analysis = AnalysisViewModel(
        load = sessionAnalysisReader(graph, dashboard),
        exportWriter = graph.usageExportWriter,
        pdfOf = { result, language -> TelegramAnalysisPdf.render(result, language) },
        freshness = { key ->
            dashboard.uiState.map { state -> (state as? UiState.Success)?.data?.firstOrNull { item -> item.targetKey.storageKey == key }?.fetchedAt }
        }
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

    /**
     * Para as telas e depois o motor, que fecha bancos e clientes e só então
     * entrega a atualização pendente. Idempotente: o gancho da JVM, o `onDispose`
     * e a saída pela janela podem chamar os três, e só o primeiro faz alguma coisa.
     */
    fun shutdown(singleInstanceGuard: SingleInstanceGuard) {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return
        }
        history.onDestroy()
        codexCliSessions.onDestroy()
        teamUsage.onDestroy()
        teamPresence.onDestroy()
        comparison.onDestroy()
        analysis.onDestroy()
        teamKeys.onDestroy()
        engine.shutdown(singleInstanceGuard)
    }
}

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
