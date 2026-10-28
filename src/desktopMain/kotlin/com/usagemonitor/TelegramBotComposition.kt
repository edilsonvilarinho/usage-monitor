package com.usagemonitor

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.resolveLocalHostName
import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.entity.TelegramDailySpend
import com.usagemonitor.domain.entity.TelegramPing
import kotlin.time.Instant
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.usecase.BuildModelComparisonUseCase
import com.usagemonitor.domain.usecase.GetReleaseNotesUseCase
import com.usagemonitor.domain.usecase.GetTeamPresenceUseCase
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/*
 * A montagem do bot sobre o [AppGraph], fora de `TelegramBotWiring.kt`: é raiz de
 * composição (lê credencial real e abre HTTP de verdade) e sai do Kover por nome (onda
 * 6.5 do plano `cobertura-95-execucao.md`). Os leitores que o bot recebe ficam lá,
 * contados e testados.
 */

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
    languageProvider = { graph.languageSelection.current() },
    enabledSources = { graph.enabledApis.value },
    // O mesmo caminho do interruptor da aba APIs: grava e recoleta a fonte.
    toggleSource = { source, on ->
        val current = graph.enabledApis.value
        persistEnabledApis(graph.store, if (on) current + source else current - source)
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
    chartProvider = { range ->
        telegramChartOf(graph.getUsageHistory, graph.enabledApis.value, graph.languageSelection.current(), range, Clock.System.now())
    },
    readings = TelegramBotReadings(
        ping = { TelegramPing(CURRENT_APP_VERSION, startedAt, dashboard.nextRefreshAt.value) },
        spikes = { dashboard.spikes.value },
        comparison = comparisonReader(
            BuildModelComparisonUseCase(graph.cliSessionRepository, graph.codexCliSessionRepository)::invoke,
            stats = { dashboard.lastStats() }
        ),
        accountHistory = accountHistoryReader(graph.getUsageHistory, stats = { dashboard.lastStats() }),
        // O mesmo pedido da segunda instância: um arquivo que o laço do `Main` atende.
        focusHud = { FocusRequestChannel().request() },
        machineName = machineName,
        teamPresence = teamPresenceReader(
            GetTeamPresenceUseCase(graph.teamUsageRepository, graph.teamServerClockOffset)::invoke,
            accountKeys = {
                buildSessionPulseTargets(graph.profileRegistry, graph.teamSettingsFlow.value).map { target -> target.accountKey }
            }
        ),
        teamClockOffsetMillis = { graph.teamServerClockOffset.offsetMillis },
        // Só a pronta (baixada e com SHA-256 conferido): a só "disponível" não tem o que instalar.
        readyUpdate = { (dashboard.appUpdateState.value as? AppUpdateUiState.Ready)?.update?.version },
        applyUpdate = { dashboard.restartAndUpdateNow() },
        appReady = { graph.appAnchored.first { anchored -> anchored } },
        releaseNotes = { version, previous -> GetReleaseNotesUseCase(graph.appUpdateRepository)(version, previous) },
        sourceHealth = { sourceHealthOf(dashboard.uiState.value) },
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

/** Os cards da última coleta; antes da primeira, ou com erro geral, nenhum. */
private fun DashboardViewModel.lastStats(): List<ApiUsageStats> = (uiState.value as? UiState.Success)?.data.orEmpty()

/** Janela do gasto no resumo diário do bot (#398, Y1). */
private const val DAILY_SPEND_WINDOW_MILLIS = 24 * 60 * 60 * 1_000L
