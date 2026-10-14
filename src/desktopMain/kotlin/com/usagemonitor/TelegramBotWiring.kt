package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.resolveLocalHostName
import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.TelegramDailySpend
import com.usagemonitor.domain.entity.TelegramAccountHistory
import com.usagemonitor.domain.entity.TelegramPing
import com.usagemonitor.domain.entity.buildSessionAnalysis
import com.usagemonitor.domain.entity.analysisThroughputCuts
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.SourceHealthObservation
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.domain.entity.snapshotLabel
import kotlin.time.Instant
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.usecase.BuildModelComparisonUseCase
import com.usagemonitor.domain.usecase.GetTeamPresenceUseCase
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.ui.TelegramChart
import com.usagemonitor.presentation.ui.buildTelegramChart
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * Bot do Telegram (#387) montado sobre o [AppGraph]: repassa os alertas da
 * bandeja e atende comandos das conversas pareadas. Desligado até o usuário
 * ligar e colar o token.
 *
 * Arquivo próprio desde a #408: cada função nova do bot pede um provedor a mais,
 * e a fiação inteira morando em `AppViewModels` o faria crescer a cada uma.
 */
internal fun createTelegramBot(
    graph: AppGraph,
    dashboard: DashboardViewModel,
    alerts: Flow<UsageAlert>,
    snapshot: () -> UsageSnapshot?,
    /** Quando o app abriu, para o `/ping` dizer há quanto tempo está de pé. */
    startedAt: Instant = Clock.System.now()
): TelegramBotService {
    val machine by lazy { resolveLocalHostName() }
    return createTelegramBotService(graph, dashboard, alerts, snapshot, startedAt) { machine }
}

private fun createTelegramBotService(
    graph: AppGraph,
    dashboard: DashboardViewModel,
    alerts: Flow<UsageAlert>,
    snapshot: () -> UsageSnapshot?,
    startedAt: Instant,
    machineName: () -> String?
): TelegramBotService = TelegramBotService(
    api = TelegramBotApi(graph.httpClient),
    settingsFlow = graph.telegramSettingsFlow,
    saveSettings = graph.telegramSettingsDataSource::save,
    alertSettingsFlow = graph.alertSettingsFlow,
    saveAlertSettings = { updated ->
        graph.alertSettingsFlow.value = updated
        persistAlertSettings(graph.settings, updated)
    },
    alerts = alerts,
    snapshotProvider = snapshot,
    requestRefresh = { refreshForBot(dashboard) },
    languageProvider = { storedLanguage(graph.settings) },
    enabledSources = { graph.enabledApis.value },
    // O mesmo caminho do interruptor da aba APIs: grava e recoleta a fonte.
    toggleSource = { source, on ->
        val current = graph.enabledApis.value
        persistEnabledApis(graph, if (on) current + source else current - source)
        dashboard.refresh(source)
    },
    // Corte de 24 h por `sinceEpochMillis`: o repositório não tem fim de
    // janela, e "ontem" exigiria um `CliSessionRange` novo (proibido).
    spendProvider = {
        val since = Clock.System.now().toEpochMilliseconds() - DAILY_SPEND_WINDOW_MILLIS
        graph.cliSessionRepository.getUsageBreakdown(profileId = null, sinceEpochMillis = since).getOrNull()?.totals?.let { totals ->
            TelegramDailySpend(totals.costMicros, totals.sessionCount, totals.unpricedTurnCount)
        }
    },
    chartProvider = { range -> telegramChartOf(graph, range) },
    readings = TelegramBotReadings(
        ping = { TelegramPing(CURRENT_APP_VERSION, startedAt, dashboard.nextRefreshAt.value) },
        spikes = { dashboard.spikes.value },
        comparison = comparisonReader(graph, dashboard),
        accountHistory = accountHistoryReader(graph, dashboard),
        // O mesmo pedido da segunda instância: um arquivo que o laço do `Main` atende.
        focusHud = { FocusRequestChannel().request() },
        machineName = machineName,
        teamPresence = teamPresenceReader(graph),
        teamClockOffsetMillis = { graph.teamServerClockOffset.offsetMillis },
        // Só a pronta (baixada e com SHA-256 conferido): a só "disponível" não tem o que instalar.
        readyUpdate = { (dashboard.appUpdateState.value as? AppUpdateUiState.Ready)?.update?.version },
        applyUpdate = { dashboard.restartAndUpdateNow() },
        sourceHealth = { sourceHealthOf(dashboard.uiState.value, storedLanguage(graph.settings)) },
        sessionAnalysis = sessionAnalysisReader(graph, dashboard),
        exportUsage = { since, format ->
            graph.cliSessionRepository.getUsageBreakdown(profileId = null, sinceEpochMillis = since).getOrNull()?.let { breakdown ->
                // Projeto, branch e integrante ficam fora: o arquivo sai do PC para o Telegram.
                DefaultUsageExportEncoder.encodeBreakdown(
                    breakdown.copy(byProject = emptyList(), byBranch = emptyList(), byMember = emptyList()),
                    format
                )
            }
        }
    )
)

/**
 * A presença do time (#408, `/time`) nas mesmas contas do semáforo dos cards
 * (`buildSessionPulseTargets`: integração ligada e perfil participando). Uma pessoa em
 * duas contas aparece uma vez. Todas as leituras falhando é falha; algumas, não.
 */
private fun teamPresenceReader(graph: AppGraph): suspend () -> Result<List<TeamMemberPresence>>? {
    val presence = GetTeamPresenceUseCase(graph.teamUsageRepository, graph.teamServerClockOffset)
    return reader@{
        val keys = buildSessionPulseTargets(graph.profileRegistry, graph.teamSettingsFlow.value).map { target -> target.accountKey }.distinct()
        if (keys.isEmpty()) return@reader null
        val results = keys.map { key -> presence(key) }
        val failure = results.firstOrNull { result -> result.isFailure }?.exceptionOrNull()
        if (failure != null && results.all { result -> result.isFailure }) return@reader Result.failure(failure)
        Result.success(results.flatMap { result -> result.getOrNull()?.entries.orEmpty() }.distinctBy { entry -> entry.memberKey })
    }
}

/**
 * Fonte quebrada (#408, AA18): uma observação por conta da última coleta. A falha leva a
 * mensagem que a tela mostra (já saneada) e a hora dos números mantidos na tela; o 429 entra
 * como falha — a fonte de fato parou de atualizar, e a mensagem diz que é limite.
 */
private fun sourceHealthOf(state: UiState, language: AppLanguage): List<SourceHealthObservation>? {
    val data: List<ApiUsageStats>
    val errors: List<UiApiError>
    when (state) {
        is UiState.Success -> {
            data = state.data
            errors = state.errors
        }
        is UiState.Error -> {
            data = emptyList()
            errors = state.errors
        }
        UiState.NoApisEnabled -> return emptyList()
        UiState.Loading -> return null
    }
    val failingKeys = errors.map { error -> error.target.storageKey }.toSet()
    val failing = errors.map { error ->
        SourceHealthObservation(
            key = error.target.storageKey,
            label = error.targetLabel?.takeIf { label -> label.isNotBlank() } ?: error.source.displayName(),
            failure = error.message,
            lastReadingAt = data.firstOrNull { stats -> stats.targetKey == error.target }?.fetchedAt
        )
    }
    val healthy = data.filter { stats -> stats.targetKey.storageKey !in failingKeys }
        .map { stats -> SourceHealthObservation(stats.targetKey.storageKey, stats.snapshotLabel, failure = null) }
    return failing + healthy
}

/** A comparação entre modelos (#386) desde um instante, com as cotas da última coleta. */
private fun comparisonReader(graph: AppGraph, dashboard: DashboardViewModel): suspend (Long) -> List<ComparisonRow>? {
    val buildComparison = BuildModelComparisonUseCase(graph.cliSessionRepository, graph.codexCliSessionRepository)
    return { since ->
        val stats = (dashboard.uiState.value as? UiState.Success)?.data.orEmpty()
        buildComparison(since, stats).getOrNull()
    }
}

/**
 * O histórico de cada conta da última coleta, pela mesma chave que o painel usa
 * para a projeção (`accountContext.key`). Conta cujo SQLite falha fica fora.
 */
private fun accountHistoryReader(graph: AppGraph, dashboard: DashboardViewModel): suspend (HistoryRange) -> List<TelegramAccountHistory> = { range ->
    val now = Clock.System.now()
    val stats = (dashboard.uiState.value as? UiState.Success)?.data.orEmpty()
    stats.mapNotNull { item ->
        runCatching { TelegramAccountHistory(item.snapshotLabel, graph.getUsageHistory(item.source, range, item.accountContext?.key, now)) }.getOrNull()
    }
}

/** Janela do gasto no resumo diário do bot (#398, Y1). */
private const val DAILY_SPEND_WINDOW_MILLIS = 24 * 60 * 60 * 1_000L

/**
 * Histórico das fontes ligadas para o `/grafico` (#398, Y6). Uma série por conta:
 * sem `listAccounts`, a fonte tem uma conta só e vai sem chave. O nome é o da fonte,
 * numerado quando há mais de uma conta — nunca o e-mail da conta.
 */
private suspend fun telegramChartOf(graph: AppGraph, range: HistoryRange): TelegramChart? {
    val now = Clock.System.now()
    val language = storedLanguage(graph.settings)
    val reports = graph.enabledApis.value.sortedBy { source -> source.ordinal }.flatMap { source ->
        val accounts = runCatching { graph.getUsageHistory.listAccounts(source) }.getOrDefault(emptyList())
        if (accounts.isEmpty()) {
            listOfNotNull(runCatching { source.displayName() to graph.getUsageHistory(source, range, null, now) }.getOrNull())
        } else {
            accounts.mapIndexedNotNull { index, account ->
                val name = if (accounts.size > 1) "${source.displayName()} ${index + 1}" else source.displayName()
                runCatching { name to graph.getUsageHistory(source, range, account.key, now) }.getOrNull()
            }
        }
    }
    return buildTelegramChart(reports, range, now, language).takeUnless { chart -> chart.isEmpty }
}
