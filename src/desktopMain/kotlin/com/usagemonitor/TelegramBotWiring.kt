package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.TelegramDailySpend
import com.usagemonitor.domain.entity.TelegramPing
import kotlin.time.Instant
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.ui.TelegramChart
import com.usagemonitor.presentation.ui.buildTelegramChart
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
        spikes = { dashboard.spikes.value }
    )
)

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
            listOfNotNull(runCatching { source.displayName(language) to graph.getUsageHistory(source, range, null, now) }.getOrNull())
        } else {
            accounts.mapIndexedNotNull { index, account ->
                val name = if (accounts.size > 1) "${source.displayName(language)} ${index + 1}" else source.displayName(language)
                runCatching { name to graph.getUsageHistory(source, range, account.key, now) }.getOrNull()
            }
        }
    }
    return buildTelegramChart(reports, range, now).takeUnless { chart -> chart.isEmpty }
}
