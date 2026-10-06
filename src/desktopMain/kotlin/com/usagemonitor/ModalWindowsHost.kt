package com.usagemonitor

import com.usagemonitor.presentation.ui.ComparisonScreen

import com.usagemonitor.presentation.ui.ComparisonLabels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.presentation.ui.AppDialogWindow
import com.usagemonitor.presentation.ui.CliSessionsScreen
import com.usagemonitor.presentation.ui.CodexCliSessionsScreen
import com.usagemonitor.presentation.ui.HelpWindow
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.ModalWindowEnvironment
import com.usagemonitor.presentation.ui.ReleaseNotesWindow
import com.usagemonitor.presentation.ui.TeamKeysAdminScreen
import com.usagemonitor.presentation.ui.TeamPresenceScreen
import com.usagemonitor.presentation.ui.TeamUsageScreen
import com.usagemonitor.presentation.ui.cliSessionsWindowTitle
import com.usagemonitor.presentation.ui.rememberLastNonNull
import com.usagemonitor.presentation.ui.teamPresenceWindowTitle
import com.usagemonitor.presentation.ui.teamUsageWindowTitle
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.update.ReleaseNotesController
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.PeriodType
import kotlin.time.Instant

/**
 * As janelas modais, menos as Configurações (`SettingsWindowHost`). Todas passam
 * pelo mesmo `AppDialogWindow` e recebem o mesmo [environment]: esquecer a escala
 * ou o movimento numa delas renderizaria errado sem erro nenhum.
 *
 * Fechar uma janela ao vivo avisa o view model: sem isso o laço continuaria
 * indexando ou consultando o servidor de cinco em cinco segundos escondido.
 */
@Composable
internal fun ModalWindowsHost(
    graph: AppGraph,
    viewModels: AppViewModels,
    modal: AppModalState,
    windows: AppWindowStates,
    environment: ModalWindowEnvironment,
    language: AppLanguage,
    teamSettings: TeamIntegrationSettings,
    releaseNotes: ReleaseNotesController
) {
    // A janela do histórico continua existindo, escondida, depois de fechada; o
    // título e a fonte são os da última abertura, senão ela esmaeceria vazia.
    val historySource = rememberLastNonNull(modal.historySource)
    if (historySource != null) {
        AppDialogWindow(
            visible = modal.historySource != null,
            title = historyWindowTitle(historySource, language),
            state = windows.history,
            environment = environment,
            diagnosticName = "histórico",
            minWidthDp = 320,
            minHeightDp = DEFAULT_MODAL_MIN_HEIGHT.value.toInt(),
            onCloseRequest = { modal.historySource = null },
            openGeneration = modal.historyOpenGeneration
        ) {
            HistoryScreen(
                viewModel = viewModels.history,
                language = language,
                onBack = { modal.historySource = null },
                focusedSource = historySource,
                showSourceSelector = false
            )
        }
    }

    AppDialogWindow(
        visible = modal.isCliSessionsOpen,
        title = cliSessionsWindowTitle(language, modal.cliSessionsProfileLabel),
        state = windows.cliSessions,
        environment = environment,
        diagnosticName = "sessões CLI",
        minWidthDp = CLI_SESSIONS_MIN_WINDOW_WIDTH_DP,
        minHeightDp = CLI_SESSIONS_MIN_WINDOW_HEIGHT_DP,
        onCloseRequest = {
            modal.isCliSessionsOpen = false
            viewModels.cliSessions.closeWindow()
        },
        openGeneration = modal.cliSessionsOpenGeneration,
        prewarm = true
    ) {
        CliSessionsScreen(viewModel = viewModels.cliSessions, language = language)
    }

    AppDialogWindow(
        visible = modal.isCodexCliSessionsOpen,
        title = if (language == AppLanguage.PT) "Sessões Codex CLI" else "Codex CLI sessions",
        state = windows.codexCliSessions,
        environment = environment,
        diagnosticName = "sessões Codex CLI",
        minWidthDp = 820,
        minHeightDp = 520,
        onCloseRequest = {
            modal.isCodexCliSessionsOpen = false
            viewModels.codexCliSessions.closeWindow()
        },
        openGeneration = modal.codexCliSessionsOpenGeneration
    ) {
        CodexCliSessionsScreen(viewModel = viewModels.codexCliSessions, language = language)
    }

    AppDialogWindow(
        visible = modal.isComparisonOpen,
        title = ComparisonLabels.title(language),
        state = windows.comparison,
        environment = environment,
        diagnosticName = "comparação entre modelos",
        minWidthDp = 860,
        minHeightDp = 480,
        onCloseRequest = { modal.isComparisonOpen = false },
        openGeneration = modal.comparisonOpenGeneration
    ) {
        ComparisonScreen(viewModel = viewModels.comparison, language = language)
    }

    // A mesma origem nas duas telas do time: é ela que separa a sessão desta
    // máquina da de um colega.
    val localDeviceId = teamSettings.deviceId.takeIf { id -> id.isNotBlank() }
    AppDialogWindow(
        visible = modal.isTeamUsageOpen,
        title = teamUsageWindowTitle(
            language = language,
            accountLabel = modal.teamUsageAccountLabel,
            isAdminOverview = modal.teamUsageIsAdminOverview
        ),
        state = windows.teamUsage,
        environment = environment,
        diagnosticName = "uso do time",
        minWidthDp = TEAM_USAGE_MIN_WINDOW_WIDTH_DP,
        minHeightDp = TEAM_USAGE_MIN_WINDOW_HEIGHT_DP,
        onCloseRequest = {
            modal.isTeamUsageOpen = false
            viewModels.teamUsage.closeWindow()
        },
        openGeneration = modal.teamUsageOpenGeneration
    ) {
        TeamUsageScreen(viewModel = viewModels.teamUsage, language = language, localDeviceId = localDeviceId)
    }

    AppDialogWindow(
        visible = modal.isTeamPresenceOpen,
        title = teamPresenceWindowTitle(
            language = language,
            accountLabel = modal.teamPresenceAccountLabel,
            isAdminOverview = modal.teamPresenceIsAdminOverview
        ),
        state = windows.teamPresence,
        environment = environment,
        diagnosticName = "presença do time",
        minWidthDp = TEAM_PRESENCE_MIN_WINDOW_WIDTH_DP,
        minHeightDp = TEAM_PRESENCE_MIN_WINDOW_HEIGHT_DP,
        onCloseRequest = {
            modal.isTeamPresenceOpen = false
            viewModels.teamPresence.closeWindow()
        },
        openGeneration = modal.teamPresenceOpenGeneration
    ) {
        TeamPresenceScreen(
            viewModel = viewModels.teamPresence,
            language = language,
            localDeviceId = localDeviceId,
            canManage = teamSettings.isAdminMode
        )
    }

    HelpWindow(
        visible = modal.isHelpOpen,
        language = language,
        environment = environment,
        onCloseRequest = { modal.isHelpOpen = false }
    )

    // Sem notas ela não compõe nada; a decisão inteira mora no controlador.
    ReleaseNotesWindow(
        controller = releaseNotes,
        language = language,
        environment = environment,
        onOpenReleasePage = { url -> graph.appUpdateReleaseOpener.open(url) }
    )

    AppDialogWindow(
        visible = modal.isTeamKeysOpen,
        title = if (language == AppLanguage.PT) "Chaves das contas" else "Account keys",
        state = windows.teamKeys,
        environment = environment,
        diagnosticName = "chaves das contas",
        minWidthDp = 320,
        minHeightDp = DEFAULT_MODAL_MIN_HEIGHT.value.toInt(),
        onCloseRequest = { modal.isTeamKeysOpen = false }
    ) {
        TeamKeysAdminScreen(viewModel = viewModels.teamKeys, language = language)
    }
}

/**
 * O que as janelas de sessões e de time precisam do Dashboard.
 *
 * Os filtros de 5h e 7d recortam a janela de quota da conta, não as últimas
 * horas corridas: o reset vem do mesmo `resets_at` dos medidores do card. Os
 * créditos vêm da API da Anthropic, que só o Dashboard consulta. Só são
 * repassados com a janela aberta.
 */
@Composable
internal fun SessionWindowBindings(
    viewModels: AppViewModels,
    modal: AppModalState,
    dashboardState: UiState,
    monthlyBudgetMicros: Long
) {
    val cliProfileId = modal.cliSessionsProfileId
    val cliQuotaWindows = remember(dashboardState, cliProfileId) { quotaWindowsForProfile(dashboardState, cliProfileId) }
    LaunchedEffect(cliQuotaWindows, modal.isCliSessionsOpen) {
        if (modal.isCliSessionsOpen) {
            viewModels.cliSessions.setQuotaWindows(cliQuotaWindows)
        }
    }
    val cliCredits = remember(dashboardState, cliProfileId) { accountCreditsForProfile(dashboardState, cliProfileId) }
    LaunchedEffect(cliCredits, modal.isCliSessionsOpen) {
        if (modal.isCliSessionsOpen) {
            viewModels.cliSessions.setAccountCredits(cliCredits)
        }
    }
    LaunchedEffect(monthlyBudgetMicros, modal.isCliSessionsOpen) {
        if (modal.isCliSessionsOpen) {
            viewModels.cliSessions.setBudgetLimitMicros(monthlyBudgetMicros)
        }
    }
    // O time é uma conta Anthropic: a janela de 5h dele ancora no mesmo reset,
    // senão os números do time não fecham com os locais.
    val teamProfileId = modal.teamUsageProfileId
    val teamQuotaWindows = remember(dashboardState, teamProfileId) { quotaWindowsForProfile(dashboardState, teamProfileId) }
    LaunchedEffect(teamQuotaWindows, modal.isTeamUsageOpen) {
        if (modal.isTeamUsageOpen) {
            viewModels.teamUsage.setQuotaWindows(teamQuotaWindows)
        }
    }
}

/**
 * Reset da quota de 5h da conta aberta na tela de Sessões CLI.
 *
 * Devolve janelas vazias enquanto a conta não tiver coleta bem-sucedida — o
 * filtro então cai para a janela corrida em vez de esvaziar a lista.
 */
internal fun quotaWindowsForProfile(state: UiState, profileId: String?): CliQuotaWindows {
    if (profileId == null || state !is UiState.Success) {
        return CliQuotaWindows()
    }

    val stats = state.data.firstOrNull { item ->
        item.source == ApiSource.ANTHROPIC && item.targetKey.profileId == profileId
    } ?: return CliQuotaWindows()

    return CliQuotaWindows(fiveHourEndsAt = stats.quotaEndAt(PeriodType.INTERVAL))
}

/**
 * Créditos de uso da conta, na moeda **real** dela.
 *
 * `null` quando o recurso está desligado — `AnthropicMapper` só cria a terceira
 * cota com `is_enabled` verdadeiro, então a ausência aqui significa exatamente
 * isso e não uma falha de leitura.
 */
internal fun accountCreditsForProfile(state: UiState, profileId: String?): AccountCreditUsage? {
    if (profileId == null || state !is UiState.Success) {
        return null
    }

    val stats = state.data.firstOrNull { item ->
        item.source == ApiSource.ANTHROPIC && item.targetKey.profileId == profileId
    } ?: return null

    val quota = stats.quotas.firstOrNull { item ->
        item.label == AnthropicQuotaLabels.EXTRA_CREDITS
    } ?: return null

    return AccountCreditUsage(
        usedMinorUnits = quota.rawUsed.takeIf { value -> value > 0L } ?: quota.used,
        limitMinorUnits = quota.rawTotal.takeIf { value -> value > 0L } ?: quota.total,
        currencyCode = quota.currencyCode
    )
}

internal fun ApiUsageStats.quotaEndAt(periodType: PeriodType): Instant? {
    return quotas
        .firstOrNull { quota -> quota.periodType == periodType && quota.hasKnownResetAt }
        ?.periodEndAt
}

internal fun historyWindowTitle(source: ApiSource, language: AppLanguage): String {
    val sourceName = source.displayName(language)

    return if (language == AppLanguage.PT) {
        "Histórico - $sourceName"
    } else {
        "History - $sourceName"
    }
}
