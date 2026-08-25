package com.usagemonitor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import com.usagemonitor.data.datasource.LocalCredentialDataSource
import com.usagemonitor.data.datasource.LocalCodexAuthDataSource
import com.usagemonitor.data.datasource.LocalAnthropicCreditsDiagnosticsRecorder
import com.usagemonitor.data.datasource.LocalCodexDiagnosticsRecorder
import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.data.datasource.LocalKiloUsageDataSource
import com.usagemonitor.data.datasource.LocalOpenCodeUsageDataSource
import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.data.datasource.LocalTeamSyncStateDataSource
import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import com.usagemonitor.data.repository.AppUpdateRepositoryImpl
import com.usagemonitor.data.repository.CodexRepositoryImpl
import com.usagemonitor.data.repository.DashboardCacheRepositoryImpl
import com.usagemonitor.data.repository.DeepSeekRepositoryImpl
import com.usagemonitor.data.repository.KiloRepositoryImpl
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
import com.usagemonitor.data.repository.OpenCodeRepositoryImpl
import com.usagemonitor.data.repository.CliSessionRepositoryImpl
import com.usagemonitor.data.repository.TeamAdminRepositoryImpl
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.data.repository.UsageHistoryRepositoryImpl
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.AppTheme as ThemeMode
import com.usagemonitor.domain.usecase.DeleteTeamAccountUseCase
import com.usagemonitor.domain.usecase.GetActiveCliSessionPulsesUseCase
import com.usagemonitor.domain.usecase.GetActiveTeamSessionPulseUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
import com.usagemonitor.domain.usecase.GetCachedDashboardStatsUseCase
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetCliUsageBreakdownUseCase
import com.usagemonitor.domain.usecase.GetMonthlyBudgetStatusUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.CreateTeamKeyUseCase
import com.usagemonitor.domain.repository.InMemoryTeamServerClockOffset
import com.usagemonitor.domain.usecase.GetAdminTeamOverviewUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageTrendUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageUseCase
import com.usagemonitor.domain.usecase.ListTeamKeysUseCase
import com.usagemonitor.domain.usecase.RegenerateTeamKeyUseCase
import com.usagemonitor.domain.usecase.RevokeTeamKeyUseCase
import com.usagemonitor.domain.usecase.UnclaimTeamKeyAccountUseCase
import com.usagemonitor.domain.usecase.UpdateTeamKeyUseCase
import com.usagemonitor.domain.usecase.ClaimTeamKeyForAccountUseCase
import com.usagemonitor.domain.usecase.ValidateAdminTokenUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamMemberUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamSessionUseCase
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.domain.usecase.PushTeamUsageUseCase
import com.usagemonitor.domain.usecase.TouchTeamPresenceUseCase
import com.usagemonitor.domain.usecase.SyncCliSessionIndexUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.domain.usecase.SaveDashboardCacheUseCase
import com.usagemonitor.presentation.ui.DesktopDialogFrame
import com.usagemonitor.presentation.ui.DesktopWindowFrame
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.ui.CliSessionsScreen
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.TeamKeysAdminScreen
import com.usagemonitor.presentation.ui.TeamPresenceScreen
import com.usagemonitor.presentation.ui.TeamUsageScreen
import com.usagemonitor.presentation.ui.cliSessionsWindowTitle
import com.usagemonitor.presentation.ui.moveVisibleCardToIndex
import com.usagemonitor.presentation.ui.normalizeCardOrder
import com.usagemonitor.presentation.ui.teamPresenceWindowTitle
import com.usagemonitor.presentation.ui.teamUsageWindowTitle
import com.usagemonitor.presentation.ui.usageAlertMessage
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
import com.usagemonitor.presentation.ui.components.SettingsField
import com.usagemonitor.presentation.ui.components.SettingsToast
import com.usagemonitor.presentation.ui.components.SettingsToastEvent
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamConnectionUiStatus
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import com.usagemonitor.presentation.viewmodel.SessionPulseViewModel
import com.usagemonitor.presentation.viewmodel.TeamPulseTarget
import com.usagemonitor.presentation.viewmodel.TeamKeysAdminViewModel
import com.usagemonitor.presentation.viewmodel.TeamPresenceViewModel
import com.usagemonitor.presentation.viewmodel.TeamUsageViewModel
import com.usagemonitor.presentation.viewmodel.UsageAlertViewModel
import com.usagemonitor.update.DesktopAppUpdateReleaseOpener
import com.usagemonitor.update.isEnabled
import com.usagemonitor.update.rememberAutoUpdateController
import com.usagemonitor.update.writeUpdateScheduleFailureReceipt
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import java.util.prefs.Preferences
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private val DEFAULT_ENABLED_APIS = emptySet<ApiSource>()
private const val APP_ICON_RESOURCE_PATH = "/icons/app_icon.png"

/** Intervalo da indexação de transcripts em background, igual ao polling do dashboard. */
private const val CLI_SESSION_INDEX_INTERVAL_MILLIS = 10 * 60 * 1_000L

/**
 * Cadência da janela de sessões aberta. As sessões descrevem o Claude Code
 * rodando neste instante, então a tela se atualiza sozinha; uma passada custa um
 * `walk` sobre os `projects/` e um `SELECT` no índice.
 */
private const val CLI_SESSION_LIVE_INTERVAL_MILLIS = 5_000L

/**
 * Cadência da leitura do servidor de time com a janela aberta.
 *
 * Igual à da janela de sessões da máquina: as duas telas fazem a mesma promessa
 * ao usuário. A latência real com que um colega aparece é dominada pelo
 * intervalo de envio da máquina dele, não por este.
 */
private const val TEAM_USAGE_LIVE_INTERVAL_MILLIS = 5_000L

/**
 * Cadência da janela de presença.
 *
 * A mesma das outras duas janelas ao vivo: a promessa ao usuário é idêntica, e a
 * latência real com que alguém aparece é dominada pelo heartbeat de 30s da
 * máquina dele, não por este intervalo.
 */
private const val TEAM_PRESENCE_LIVE_INTERVAL_MILLIS = 5_000L

/**
 * Cadência do semáforo de sessões dos botões dos cards.
 *
 * A janela avaliada é de minutos, então meio minuto é precisão de sobra — e
 * mantém o tráfego para o servidor de time no mesmo patamar do envio, que já roda
 * de 30 em 30 segundos.
 */
private const val SESSION_PULSE_INTERVAL_MILLIS = 30_000L
private const val ENABLED_APIS_KEY = "enabledApis"
private const val IS_DARK_KEY = "isDark"
private const val LANGUAGE_KEY = "language"

/** Idioma persistido; valor irreconhecivel cai no default em vez de derrubar. */
private fun storedLanguage(settings: Settings): AppLanguage {
    return settings.getStringOrNull(LANGUAGE_KEY)
        ?.let { stored -> runCatching { AppLanguage.valueOf(stored) }.getOrNull() }
        ?: AppLanguage.PT
}
private const val AUTO_START_KEY = "autoStart"
private const val ALWAYS_ON_TOP_KEY = "alwaysOnTop"
private const val CARD_ORDER_KEY = "cardOrder"
private const val MINIMIZED_CARDS_KEY = "minimizedCards"
private const val NEXT_REFRESH_AT_KEY = "nextRefreshAtMillis"

private fun loadWindowIcon() = runCatching {
    val stream = object {}.javaClass.getResourceAsStream(APP_ICON_RESOURCE_PATH) ?: return@runCatching null
    stream.use { resourceStream ->
        ImageIO.read(resourceStream).toPainter()
    }
}.getOrNull()

@OptIn(kotlinx.coroutines.FlowPreview::class)
fun main(args: Array<String>) = application {

    // Forma de expressao de proposito: ela expoe `args` ao corpo sem reindentar
    // as mil linhas que vem abaixo, e mantem a regra de nao criar composable nova
    // aqui dentro.
    val startupDiagnostics = remember { StartupDiagnostics() }
    val startupOrigin = remember { StartupOrigin.from(args) }

    val focusRequests = remember { FocusRequestChannel() }

    val singleInstanceGuard = remember { SingleInstanceGuard.tryAcquire() }
    if (singleInstanceGuard == null) {
        // Sair calado aqui e o que faz clicar no atalho com o app ja rodando nao
        // produzir nada -- indistinguivel de "o app nao abre". O pedido de foco
        // fica no disco e a instancia viva o atende.
        focusRequests.request()
        startupDiagnostics.record(startupOrigin, StartupOutcome.SECOND_INSTANCE_EXIT)
        exitApplication()
        return@application
    }
    LaunchedEffect(startupDiagnostics, startupOrigin) {
        startupDiagnostics.record(startupOrigin, StartupOutcome.STARTED)
    }

    val httpClient = remember {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            install(Logging) {
                level = LogLevel.NONE
            }
        }
    }

    val preferencesNode = remember { Preferences.userRoot().node("com.usagemonitor") }
    val settings = remember(preferencesNode) { PreferencesSettings(preferencesNode) }

    val persistedApis = remember(settings) {
        readApiSourceCollection(settings, ENABLED_APIS_KEY)
            .toSet()
            .ifEmpty { DEFAULT_ENABLED_APIS }
    }
    // Valor inicial vem só das preferências persistidas (leitura em memória, sem custo de I/O).
    // A confirmação real do estado do SO (que pode envolver "reg query", bloqueante) acontece
    // depois, de forma assíncrona, para não atrasar o primeiro frame da janela.
    val storedAutoStartPreference = remember(settings) {
        settings.getBoolean(AUTO_START_KEY, false)
    }

    val persistedNextRefreshAt = remember(settings) {
        settings.getLong(NEXT_REFRESH_AT_KEY, -1L)
            .takeIf { it > 0 }
            ?.let { Instant.fromEpochMilliseconds(it) }
    }

    val enabledApis = remember { MutableStateFlow(persistedApis) }
    val profileRegistry = remember(preferencesNode) {
        AnthropicProfileRegistry(
            preferences = preferencesNode,
            defaultEnabled = ApiSource.ANTHROPIC in persistedApis
        )
    }
    val profileRecords by profileRegistry.profiles.collectAsState()
    val profileResolution = resolveAnthropicProfiles(profileRegistry, profileRecords)
    val enabledAnthropicProfiles = remember { MutableStateFlow(profileResolution.enabledProfiles) }
    enabledAnthropicProfiles.value = profileResolution.enabledProfiles
    val availableTargets = remember(profileRecords) { availableUsageTargets(profileRecords) }
    val persistedCardOrder = remember(settings) {
        normalizeCardOrder(readUsageTargetCollection(settings, CARD_ORDER_KEY), availableTargets)
    }
    val persistedMinimizedCards = remember(settings) {
        val storedValue = settings.getStringOrNull(MINIMIZED_CARDS_KEY)
        if (storedValue == null) {
            availableTargets.toSet()
        } else {
            readUsageTargetCollection(settings, MINIMIZED_CARDS_KEY).toSet()
        }
    }
    var cardOrder by remember { mutableStateOf(persistedCardOrder) }
    var minimizedCards by remember { mutableStateOf(persistedMinimizedCards) }
    // Estado efêmero de expansão do editor de conta Anthropic nas Configurações.
    // Não é persistido: reabrir o diálogo pode voltar ao estado colapsado.
    var expandedAnthropicProfileId by remember { mutableStateOf<String?>(null) }
    val persistedMainWindowState = remember(settings) {
        readPersistedMainWindowState(settings)
    }
    val persistedHistoryWindowState = remember(settings) {
        readPersistedHistoryWindowState(settings)
    }
    val persistedCliSessionsWindowState = remember(settings) {
        readPersistedCliSessionsWindowState(settings)
    }
    val persistedTeamUsageWindowState = remember(settings) {
        readPersistedTeamUsageWindowState(settings)
    }
    val persistedTeamPresenceWindowState = remember(settings) {
        readPersistedTeamPresenceWindowState(settings)
    }

    // A chave do servidor é segredo e vai para um arquivo com permissão restrita
    // ao dono, não para as preferências — estas são gravadas em claro no registro.
    val teamSettingsDataSource = remember { LocalTeamSettingsDataSource() }
    // `StateFlow` e não `mutableStateOf`: o repositório e o serviço de envio leem
    // as credenciais de fora da composição, e precisam sempre do valor corrente.
    val teamSettingsFlow = remember(teamSettingsDataSource) {
        MutableStateFlow(teamSettingsDataSource.load())
    }
    val teamSettings by teamSettingsFlow.collectAsState()

    val credentialDataSource = remember(httpClient, profileRegistry) {
        LocalCredentialDataSource(
            httpClient = httpClient,
            profileLocationProvider = { profile -> profileRegistry.locationFor(profile.id) }
        )
    }
    val codexAuthDataSource = remember { LocalCodexAuthDataSource() }
    val codexDiagnosticsRecorder = remember { LocalCodexDiagnosticsRecorder() }
    val anthropicCreditsDiagnosticsRecorder = remember { LocalAnthropicCreditsDiagnosticsRecorder() }
    val remoteApiDataSource = remember(
        httpClient,
        codexDiagnosticsRecorder,
        anthropicCreditsDiagnosticsRecorder
    ) {
        RemoteApiDataSource(
            httpClient = httpClient,
            codexDiagnosticsRecorder = codexDiagnosticsRecorder,
            anthropicCreditsDiagnosticsRecorder = anthropicCreditsDiagnosticsRecorder
        )
    }
    val usageHistoryDataSource = remember { LocalUsageHistoryDataSource() }
    // Cada conta Anthropic tem seu próprio config dir do Claude Code, e cada um
    // tem seu `projects/`. É daí que sai a atribuição de sessão para conta — os
    // transcripts em si não carregam identidade nenhuma.
    val cliSessionDataSource = remember(profileRegistry) {
        LocalCliSessionDataSource(
            projectRootsProvider = {
                profileRegistry.profiles.value.map { record ->
                    CliProjectRoot(
                        profileId = record.id,
                        directoryPath = File(record.configDirectory, "projects").absolutePath
                    )
                }
            }
        )
    }
    // Mesma conexão do índice de sessões, não uma segunda para o mesmo arquivo:
    // `useConnection` é sincronizado, então o envio espera a indexação terminar
    // em vez de disputar a escrita e receber `SQLITE_BUSY`.
    val teamSyncStateDataSource = remember(cliSessionDataSource) {
        LocalTeamSyncStateDataSource(cliSessionDataSource.sharedConnectionManager)
    }
    val remoteTeamDataSource = remember(httpClient) { RemoteTeamDataSource(httpClient) }
    val dashboardCacheDataSource = remember { LocalDashboardCacheDataSource() }
    val openCodeUsageDataSource = remember { LocalOpenCodeUsageDataSource() }
    val kiloUsageDataSource = remember { LocalKiloUsageDataSource() }

    val anthropicRepository = remember(credentialDataSource, remoteApiDataSource) {
        AnthropicRepositoryImpl(credentialDataSource, remoteApiDataSource)
    }
    val minimaxRepository = remember(remoteApiDataSource) {
        MiniMaxRepositoryImpl(remoteApiDataSource)
    }
    val codexRepository = remember(codexAuthDataSource, remoteApiDataSource) {
        CodexRepositoryImpl(codexAuthDataSource, remoteApiDataSource)
    }
    val deepSeekRepository = remember(remoteApiDataSource) {
        DeepSeekRepositoryImpl(remoteApiDataSource)
    }
    val openCodeRepository = remember(openCodeUsageDataSource) {
        OpenCodeRepositoryImpl(openCodeUsageDataSource)
    }
    val kiloRepository = remember(kiloUsageDataSource) {
        KiloRepositoryImpl(kiloUsageDataSource)
    }
    val usageHistoryRepository = remember(usageHistoryDataSource) {
        UsageHistoryRepositoryImpl(usageHistoryDataSource)
    }
    val cliSessionRepository = remember(cliSessionDataSource) {
        CliSessionRepositoryImpl(cliSessionDataSource)
    }
    val dashboardCacheRepository = remember(dashboardCacheDataSource) {
        DashboardCacheRepositoryImpl(dashboardCacheDataSource)
    }
    // Uma instância só: o desvio é entre o relógio desta máquina e o do servidor,
    // não de cada consumidor. Quem o mede é a batida de presença; quem o lê é a
    // classificação de quem está online.
    val teamServerClockOffset = remember { InMemoryTeamServerClockOffset() }
    val teamUsageRepository = remember(remoteTeamDataSource, teamSettingsFlow, teamServerClockOffset) {
        TeamUsageRepositoryImpl(
            remoteDataSource = remoteTeamDataSource,
            settingsProvider = { teamSettingsFlow.value },
            serverClockOffset = teamServerClockOffset
        )
    }
    val teamAdminRepository = remember(remoteTeamDataSource, teamSettingsFlow) {
        TeamAdminRepositoryImpl(
            remoteDataSource = remoteTeamDataSource,
            settingsProvider = { teamSettingsFlow.value }
        )
    }
    val appUpdateRepository = remember(remoteApiDataSource) {
        AppUpdateRepositoryImpl(remoteApiDataSource)
    }
    val appUpdateReleaseOpener = remember { DesktopAppUpdateReleaseOpener() }
    // Uma chamada, e todo o estado da atualização automática mora fora daqui:
    // `main()` está no limite do backend JVM.
    val autoUpdate = rememberAutoUpdateController(settings = settings, httpClient = httpClient)

    val recordUsageSnapshot = remember(usageHistoryRepository) {
        RecordUsageSnapshotUseCase(usageHistoryRepository)
    }
    val getUsageHistory = remember(usageHistoryRepository) {
        GetUsageHistoryUseCase(usageHistoryRepository)
    }
    val saveDashboardCache = remember(dashboardCacheRepository) {
        SaveDashboardCacheUseCase(dashboardCacheRepository)
    }
    val getCachedDashboardStats = remember(dashboardCacheRepository) {
        GetCachedDashboardStatsUseCase(dashboardCacheRepository)
    }

    // Referência da janela principal: a bandeja e o diálogo de exportação
    // precisam dela e nenhum dos dois vive dentro do `Window`.
    var mainWindowRef by remember { mutableStateOf<java.awt.Window?>(null) }
    val isAppVisible = remember { MutableStateFlow(true) }
    val viewModel = remember(anthropicRepository, minimaxRepository, codexRepository, deepSeekRepository, openCodeRepository, kiloRepository, enabledApis, enabledAnthropicProfiles, recordUsageSnapshot, getUsageHistory, saveDashboardCache, getCachedDashboardStats, isAppVisible) {
        DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepository),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepository),
            getCodexUsage = GetCodexUsageUseCase(codexRepository),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepository),
            getKiloUsage = GetKiloUsageUseCase(kiloRepository),
            getOpenCodeUsage = GetOpenCodeUsageUseCase(openCodeRepository),
            enabledApis = enabledApis,
            recordUsageSnapshot = recordUsageSnapshot,
            getUsageHistory = getUsageHistory,
            getCachedDashboardStats = getCachedDashboardStats,
            saveDashboardCache = saveDashboardCache,
            checkForAppUpdate = CheckForAppUpdateUseCase(appUpdateRepository),
            appUpdateReleaseOpener = appUpdateReleaseOpener,
            appUpdateInstaller = autoUpdate.installer,
            autoUpdateEnabled = autoUpdate.enabled,
            onRestartAndUpdateRequested = { autoUpdate.requestRestart() },
            onUpdateScheduleFailure = ::writeUpdateScheduleFailureReceipt,
            currentAppVersion = CURRENT_APP_VERSION,
            isAppVisible = isAppVisible,
            anthropicProfiles = enabledAnthropicProfiles,
            persistedNextRefreshAt = persistedNextRefreshAt,
            onNextRefreshAtChanged = { instant -> settings.putLong(NEXT_REFRESH_AT_KEY, instant.toEpochMilliseconds()) }
        )
    }
    val historyViewModel = remember(getUsageHistory, enabledApis) {
        HistoryViewModel(
            getUsageHistory = getUsageHistory,
            enabledApis = enabledApis
        )
    }
    // A indexação corre em background desde o arranque, em `Dispatchers.IO`: o
    // Claude Code apaga transcripts antigos, e depender de o usuário abrir a
    // janela antes disso perderia o histórico. A lista em si só carrega quando a
    // janela abre (`autoLoad = false`).
    // Uma instância só: a tela de Sessões CLI e o envio para o time indexam o
    // mesmo banco, e duas cópias do caso de uso não trariam nada.
    val syncCliSessionIndex = remember(cliSessionRepository) {
        SyncCliSessionIndexUseCase(cliSessionRepository)
    }
    // A janela principal só existe depois da composição; por isso o writer
    // recebe uma função e não a referência.
    // O idioma sai das preferencias na hora de gerar, e nao do estado da tela:
    // ele so e declarado bem mais abaixo, e o relatorio precisa do idioma
    // corrente, nao do que estava valendo quando o app subiu.
    val usageExportWriter = remember(settings) {
        DesktopUsageExportWriter(
            parentWindow = { mainWindowRef },
            language = { storedLanguage(settings) }
        )
    }
    val cliSessionsViewModel = remember(cliSessionRepository) {
        CliSessionsViewModel(
            getCliSessions = GetCliSessionsUseCase(cliSessionRepository),
            getCliSessionDetail = GetCliSessionDetailUseCase(cliSessionRepository),
            syncCliSessionIndex = syncCliSessionIndex,
            getCliUsageBreakdown = GetCliUsageBreakdownUseCase(cliSessionRepository),
            exportWriter = usageExportWriter,
            getMonthlyBudgetStatus = GetMonthlyBudgetStatusUseCase(cliSessionRepository),
            autoLoad = false,
            backgroundIndexIntervalMillis = CLI_SESSION_INDEX_INTERVAL_MILLIS,
            liveIntervalMillis = CLI_SESSION_LIVE_INTERVAL_MILLIS
        )
    }
    val teamUsageViewModel = remember(teamUsageRepository, teamAdminRepository) {
        TeamUsageViewModel(
            getTeamUsage = GetTeamUsageUseCase(teamUsageRepository),
            getTeamSessionDetail = GetTeamSessionDetailUseCase(teamUsageRepository),
            getAdminOverview = GetAdminTeamOverviewUseCase(teamAdminRepository),
            getAdminTeamSessionDetail = GetAdminTeamSessionDetailUseCase(teamAdminRepository),
            removeAdminTeamMember = RemoveAdminTeamMemberUseCase(teamAdminRepository),
            removeAdminTeamSession = RemoveAdminTeamSessionUseCase(teamAdminRepository),
            getTeamUsageTrend = GetTeamUsageTrendUseCase(teamUsageRepository),
            exportWriter = usageExportWriter,
            liveIntervalMillis = TEAM_USAGE_LIVE_INTERVAL_MILLIS
        )
    }
    val teamPresenceViewModel = remember(teamUsageRepository, teamAdminRepository, teamServerClockOffset) {
        TeamPresenceViewModel(
            getTeamPresence = GetTeamPresenceUseCase(teamUsageRepository, teamServerClockOffset),
            getAdminTeamPresence = GetAdminTeamPresenceUseCase(
                teamAdminRepository,
                teamServerClockOffset
            ),
            removeTeamMember = RemoveAdminTeamMemberUseCase(teamAdminRepository),
            deleteTeamAccount = DeleteTeamAccountUseCase(teamAdminRepository),
            liveIntervalMillis = TEAM_PRESENCE_LIVE_INTERVAL_MILLIS
        )
    }
    // Semáforo dos botões dos cards: lê o índice local de todas as contas e, para
    // as que participam do time, o servidor. Reusa o mesmo `syncCliSessionIndex`
    // das outras telas — o índice é um só.
    val sessionPulseViewModel = remember(cliSessionRepository, teamUsageRepository, profileRegistry) {
        SessionPulseViewModel(
            getCliPulses = GetActiveCliSessionPulsesUseCase(cliSessionRepository),
            getTeamPulse = GetActiveTeamSessionPulseUseCase(teamUsageRepository),
            syncCliSessionIndex = syncCliSessionIndex,
            teamTargetsProvider = {
                buildSessionPulseTargets(
                    registry = profileRegistry,
                    settings = teamSettingsFlow.value
                )
            },
            isAppVisible = isAppVisible,
            intervalMillis = SESSION_PULSE_INTERVAL_MILLIS
        )
    }
    val cliSessionPulses by sessionPulseViewModel.cliPulses.collectAsState()
    val teamSessionPulses by sessionPulseViewModel.teamPulses.collectAsState()
    // Preferências de alerta como flow, e não como estado da composição: quem as
    // consome é o view model, que vive fora dela.
    val alertSettingsFlow = remember(settings) { MutableStateFlow(readPersistedAlertSettings(settings)) }
    val usageAlertViewModel = remember(viewModel, sessionPulseViewModel, alertSettingsFlow) {
        UsageAlertViewModel(
            dashboardState = viewModel.uiState,
            cliPulses = sessionPulseViewModel.cliPulses,
            alertSettings = alertSettingsFlow
        )
    }
    val teamKeysViewModel = remember(teamAdminRepository) {
        TeamKeysAdminViewModel(
            listKeys = ListTeamKeysUseCase(teamAdminRepository),
            createKey = CreateTeamKeyUseCase(teamAdminRepository),
            updateKey = UpdateTeamKeyUseCase(teamAdminRepository),
            regenerateKey = RegenerateTeamKeyUseCase(teamAdminRepository),
            revokeKey = RevokeTeamKeyUseCase(teamAdminRepository),
            unclaimAccount = UnclaimTeamKeyAccountUseCase(teamAdminRepository)
        )
    }
    val validateAdminToken = remember(teamAdminRepository) {
        ValidateAdminTokenUseCase(teamAdminRepository)
    }
    val claimTeamKeyForAccount = remember(teamAdminRepository) {
        ClaimTeamKeyForAccountUseCase(teamAdminRepository)
    }
    // O envio roda com a janela do time fechada: se dependesse dela, o consumo de
    // quem nunca abre a tela nunca chegaria aos colegas.
    val teamSyncService = remember(teamSyncStateDataSource, teamUsageRepository, profileRegistry, syncCliSessionIndex) {
        TeamSyncService(
            syncStateDataSource = teamSyncStateDataSource,
            pushTeamUsage = PushTeamUsageUseCase(teamUsageRepository),
            settingsProvider = { teamSettingsFlow.value },
            targetsProvider = { buildTeamSyncTargets(profileRegistry) },
            // Sem indexar aqui, a latência do time não seria o intervalo deste
            // serviço e sim o do laço de background (10min): ele só envia o que
            // já está no índice.
            ensureIndexFresh = { syncCliSessionIndex() },
            // O heartbeat que alimenta a janela de presença. Sai em toda passada,
            // inclusive quando não há turno novo — é o que separa "app aberto" de
            // "houve consumo".
            touchTeamPresence = TouchTeamPresenceUseCase(teamUsageRepository)
        )
    }
    LaunchedEffect(teamSyncService, teamSettings.isActive) {
        if (teamSettings.isActive) {
            teamSyncService.start()
        } else {
            teamSyncService.stop()
        }
    }

    val shutdownStarted = remember { AtomicBoolean(false) }
    DisposableEffect(viewModel, historyViewModel, httpClient, singleInstanceGuard, usageHistoryDataSource, openCodeUsageDataSource, kiloUsageDataSource, profileRegistry) {
        val shutdownHook = Thread {
            if (shutdownStarted.compareAndSet(false, true)) {
                viewModel.onDestroy()
                historyViewModel.onDestroy()
                cliSessionsViewModel.onDestroy()
                teamUsageViewModel.onDestroy()
                teamPresenceViewModel.onDestroy()
                sessionPulseViewModel.onDestroy()
                usageAlertViewModel.onDestroy()
                teamSyncService.onDestroy()
                profileRegistry.close()
                httpClient.close()
                usageHistoryDataSource.close()
                cliSessionDataSource.close()
                openCodeUsageDataSource.close()
                kiloUsageDataSource.close()
                singleInstanceGuard.close()
                // Por último, e não primeiro: o instalador espera este processo
                // sair de qualquer forma, mas entregar o pacote antes de o SQLite
                // fechar seria abrir uma janela para a troca de arquivos correr
                // contra a escrita do banco.
                viewModel.scheduleUpdateOnExit()
            }
        }

        Runtime.getRuntime().addShutdownHook(shutdownHook)

        onDispose {
            runCatching {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            }
            if (shutdownStarted.compareAndSet(false, true)) {
                viewModel.onDestroy()
                historyViewModel.onDestroy()
                cliSessionsViewModel.onDestroy()
                teamUsageViewModel.onDestroy()
                teamPresenceViewModel.onDestroy()
                sessionPulseViewModel.onDestroy()
                usageAlertViewModel.onDestroy()
                teamSyncService.onDestroy()
                profileRegistry.close()
                httpClient.close()
                usageHistoryDataSource.close()
                cliSessionDataSource.close()
                openCodeUsageDataSource.close()
                kiloUsageDataSource.close()
                singleInstanceGuard.close()
                // Por último, e não primeiro: o instalador espera este processo
                // sair de qualquer forma, mas entregar o pacote antes de o SQLite
                // fechar seria abrir uma janela para a troca de arquivos correr
                // contra a escrita do banco.
                viewModel.scheduleUpdateOnExit()
            }
        }
    }

    val iconImage = remember { loadWindowIcon() }
    // A escala é lida antes das janelas: é ela que dimensiona o tamanho default de
    // cada uma quando não há nada persistido.
    var uiScalePercent by remember { mutableStateOf(readPersistedUiScalePercent(settings)) }
    // A escala que a janela principal já reflete. A razão do redimensionamento sai
    // daqui e nunca de 100 — duas mudanças seguidas multiplicariam duas vezes.
    var appliedUiScalePercent by remember { mutableStateOf(uiScalePercent) }
    var uiScaleSaveGeneration by remember { mutableStateOf(0) }
    // A área útil da tela é lida uma vez e vale para as sete janelas: nenhuma delas
    // tem moldura do sistema, então nascer maior que o monitor é nascer sem botão
    // de fechar.
    val screenWorkArea = remember { availableWindowAreaDp() }
    val mainWindowState = rememberPersistedMainWindowState(
        persistedState = persistedMainWindowState,
        uiScalePercent = uiScalePercent,
        workArea = screenWorkArea
    )
    val historyWindowState = rememberPersistedHistoryWindowState(
        persistedState = persistedHistoryWindowState,
        uiScalePercent = uiScalePercent,
        workArea = screenWorkArea
    )
    val cliSessionsWindowState = rememberPersistedCliSessionsWindowState(
        persistedState = persistedCliSessionsWindowState,
        uiScalePercent = uiScalePercent,
        workArea = screenWorkArea
    )
    val teamUsageWindowState = rememberPersistedTeamUsageWindowState(
        persistedState = persistedTeamUsageWindowState,
        uiScalePercent = uiScalePercent,
        workArea = screenWorkArea
    )
    val teamPresenceWindowState = rememberPersistedTeamPresenceWindowState(
        persistedState = persistedTeamPresenceWindowState,
        uiScalePercent = uiScalePercent,
        workArea = screenWorkArea
    )
    LaunchedEffect(mainWindowState, settings) {
        snapshotFlow {
            Triple(
                mainWindowState.isMinimized,
                mainWindowState.size,
                mainWindowState.placement
            )
        }
            .distinctUntilChanged()
            .debounce(250.milliseconds)
            .collect { (isMinimized, size, placement) ->
            isAppVisible.value = !isMinimized
            persistMainWindowState(
                settings = settings,
                snapshot = MainWindowSnapshot(
                    widthDp = size.width.value,
                    heightDp = size.height.value,
                    placement = placement
                )
            )
            }
    }
    LaunchedEffect(historyWindowState, settings) {
        snapshotFlow {
            Triple(
                historyWindowState.position,
                historyWindowState.size,
                historyWindowState.placement
            )
        }
            .distinctUntilChanged()
            .debounce(250.milliseconds)
            .collect { (position, size, placement) ->
            persistHistoryWindowState(
                settings = settings,
                snapshot = HistoryWindowSnapshot(
                    widthDp = size.width.value,
                    heightDp = size.height.value,
                    xDp = if (position.isSpecified) position.x.value else null,
                    yDp = if (position.isSpecified) position.y.value else null,
                    placement = placement
                )
            )
            }
    }
    LaunchedEffect(cliSessionsWindowState, settings) {
        snapshotFlow {
            Triple(
                cliSessionsWindowState.position,
                cliSessionsWindowState.size,
                cliSessionsWindowState.placement
            )
        }
            .distinctUntilChanged()
            .debounce(250.milliseconds)
            .collect { (position, size, placement) ->
                persistCliSessionsWindowState(
                    settings = settings,
                    snapshot = CliSessionsWindowSnapshot(
                        widthDp = size.width.value,
                        heightDp = size.height.value,
                        xDp = if (position.isSpecified) position.x.value else null,
                        yDp = if (position.isSpecified) position.y.value else null,
                        placement = placement
                    )
                )
            }
    }
    LaunchedEffect(teamUsageWindowState, settings) {
        snapshotFlow {
            Triple(
                teamUsageWindowState.position,
                teamUsageWindowState.size,
                teamUsageWindowState.placement
            )
        }
            .distinctUntilChanged()
            .debounce(250.milliseconds)
            .collect { (position, size, placement) ->
                persistTeamUsageWindowState(
                    settings = settings,
                    snapshot = TeamUsageWindowSnapshot(
                        widthDp = size.width.value,
                        heightDp = size.height.value,
                        xDp = if (position.isSpecified) position.x.value else null,
                        yDp = if (position.isSpecified) position.y.value else null,
                        placement = placement
                    )
                )
            }
    }
    LaunchedEffect(teamPresenceWindowState, settings) {
        snapshotFlow {
            Triple(
                teamPresenceWindowState.position,
                teamPresenceWindowState.size,
                teamPresenceWindowState.placement
            )
        }
            .distinctUntilChanged()
            .debounce(250.milliseconds)
            .collect { (position, size, placement) ->
                persistTeamPresenceWindowState(
                    settings = settings,
                    snapshot = TeamPresenceWindowSnapshot(
                        widthDp = size.width.value,
                        heightDp = size.height.value,
                        xDp = if (position.isSpecified) position.x.value else null,
                        yDp = if (position.isSpecified) position.y.value else null,
                        placement = placement
                    )
                )
            }
    }
    val enabledApisState by enabledApis.collectAsState()
    val profileUiModels = buildAnthropicProfileUiModels(
        records = profileRecords,
        inspections = profileResolution.inspections,
        duplicateProfileIds = profileResolution.duplicateProfileIds
    )
    LaunchedEffect(availableTargets) {
        cardOrder = normalizeCardOrder(cardOrder, availableTargets)
        minimizedCards = minimizedCards.filterTo(linkedSetOf()) { target -> target in availableTargets }
        writeUsageTargetCollection(settings, CARD_ORDER_KEY, cardOrder)
        writeUsageTargetCollection(settings, MINIMIZED_CARDS_KEY, minimizedCards)
    }
    var isDark by remember { mutableStateOf(settings.getBoolean(IS_DARK_KEY, true)) }
    var language by remember { mutableStateOf(storedLanguage(settings)) }
    var autoStartEnabled by remember { mutableStateOf(storedAutoStartPreference) }
    LaunchedEffect(settings) {
        if (AutoStartManager.isAutoStartSupported()) {
            val resolvedAutoStartEnabled = withContext(Dispatchers.IO) {
                AutoStartManager.isAutoStartEnabled()
            }
            if (resolvedAutoStartEnabled != storedAutoStartPreference) {
                settings.putBoolean(AUTO_START_KEY, resolvedAutoStartEnabled)
            }
            autoStartEnabled = resolvedAutoStartEnabled
            // Migracao por baixo: instalacao anterior a esta versao tem a entrada
            // de inicializacao sem o argumento de origem, e sem ele todo arranque
            // por autostart seria registrado como manual.
            withContext(Dispatchers.IO) {
                AutoStartManager.ensureAutoStartCommandCurrent()
            }
        }
    }
    var alwaysOnTopEnabled by remember { mutableStateOf(settings.getBoolean(ALWAYS_ON_TOP_KEY, false)) }
    // Modo somente cards: sem barra de título e sem rodapé. Booleano grava direto,
    // sem o coletor com debounce que a opacidade e a escala precisam — não há
    // slider aqui, e um clique não vira uma gravação por pixel.
    var cardsOnlyMode by remember { mutableStateOf(readPersistedCardsOnlyMode(settings)) }
    val setCardsOnlyMode: (Boolean) -> Unit = { enabled ->
        cardsOnlyMode = enabled
        persistCardsOnlyMode(settings, enabled)
    }
    val windowOpacitySupported = remember { isWindowOpacitySupported() }
    var windowOpacityPercent by remember { mutableStateOf(readPersistedWindowOpacityPercent(settings)) }
    var opacitySaveGeneration by remember { mutableStateOf(0) }
    LaunchedEffect(settings) {
        snapshotFlow { windowOpacityPercent }
            .distinctUntilChanged()
            // A primeira emissão é o valor que acabou de ser lido do registro:
            // regravá-lo não muda nada e faria o app subir avisando "salvo".
            .drop(1)
            .debounce(250.milliseconds)
            .collect { percent ->
                persistWindowOpacityPercent(settings, percent)
                // O aviso sai daqui, depois da gravação: emiti-lo no callback do
                // slider daria um toast por pixel arrastado.
                opacitySaveGeneration += 1
            }
    }
    // Primeira execução depois da atualização: a escala default subiu de 100 para
    // 115 e a janela persistida ficou do tamanho de antes, ou seja, passaria a
    // caber menos conteúdo. Vale a mesma regra do slider, uma vez só — gravar a
    // escala fecha a porta, porque a chave passa a existir.
    LaunchedEffect(settings, mainWindowState) {
        if (hasPersistedUiScale(settings)) {
            return@LaunchedEffect
        }
        persistUiScalePercent(settings, uiScalePercent)
        if (persistedMainWindowState.widthDp == null ||
            mainWindowState.placement != WindowPlacement.Floating
        ) {
            return@LaunchedEffect
        }
        mainWindowState.size = scaledWindowSize(
            current = mainWindowState.size,
            fromPercent = 100,
            toPercent = uiScalePercent,
            maxSize = availableWindowSizeDp()
        )
    }
    // Mesma anatomia do coletor de opacidade, e pelo mesmo motivo: gravar e
    // redimensionar no commit, não a cada tique do slider. O conteúdo já escala ao
    // vivo pela densidade; o que espera o debounce é o disco e a moldura da janela.
    LaunchedEffect(settings, mainWindowState) {
        snapshotFlow { uiScalePercent }
            .distinctUntilChanged()
            .drop(1)
            .debounce(250.milliseconds)
            .collect { percent ->
                persistUiScalePercent(settings, percent)
                val previous = appliedUiScalePercent
                appliedUiScalePercent = percent
                // Maximizada não tem tamanho próprio para escalar; o sistema já a
                // prende à tela inteira.
                if (mainWindowState.placement == WindowPlacement.Floating) {
                    mainWindowState.size = scaledWindowSize(
                        current = mainWindowState.size,
                        fromPercent = previous,
                        toPercent = percent,
                        maxSize = availableWindowSizeDp()
                    )
                }
                uiScaleSaveGeneration += 1
            }
    }
    val alertSettingsState by alertSettingsFlow.collectAsState()
    var monthlyBudgetMicros by remember { mutableStateOf(readPersistedBudgetMicros(settings)) }
    var isSettingsDialogOpen by remember { mutableStateOf(false) }
    var settingsOpenGeneration by remember { mutableStateOf(0) }
    var historyDialogSource by remember { mutableStateOf<ApiSource?>(null) }
    var historyOpenGeneration by remember { mutableStateOf(0) }
    var isCliSessionsOpen by remember { mutableStateOf(false) }
    var cliSessionsOpenGeneration by remember { mutableStateOf(0) }
    var cliSessionsProfileLabel by remember { mutableStateOf<String?>(null) }
    var cliSessionsProfileId by remember { mutableStateOf<String?>(null) }
    var isTeamUsageOpen by remember { mutableStateOf(false) }
    var teamUsageOpenGeneration by remember { mutableStateOf(0) }
    var teamUsageAccountLabel by remember { mutableStateOf<String?>(null) }
    var teamUsageProfileId by remember { mutableStateOf<String?>(null) }
    // Quem abriu a janela é quem sabe se ela é a visão global; o rótulo nulo da
    // conta não prova isso — conta sem rótulo cairia no mesmo ramo.
    var teamUsageIsAdminOverview by remember { mutableStateOf(false) }
    var isTeamPresenceOpen by remember { mutableStateOf(false) }
    var teamPresenceOpenGeneration by remember { mutableStateOf(0) }
    var teamPresenceAccountLabel by remember { mutableStateOf<String?>(null) }
    // Mesma razão de `teamUsageIsAdminOverview`: quem abriu é quem sabe.
    var teamPresenceIsAdminOverview by remember { mutableStateOf(false) }
    var teamConnectionState by remember { mutableStateOf(TeamConnectionUiState()) }
    var teamAdminConnectionState by remember { mutableStateOf(TeamConnectionUiState()) }
    var isTeamKeysOpen by remember { mutableStateOf(false) }
    val teamSyncStatus by teamSyncService.syncStatus.collectAsState()
    val teamScope = rememberCoroutineScope()

    /**
     * Confere o servidor e o vínculo da chave com **cada** conta marcada.
     *
     * O teste antigo consultava uma conta inventada só para exercitar a chave.
     * Isso funcionava enquanto qualquer chave lia qualquer conta; com autorização
     * por conta aquela consulta passaria a ser recusada e o botão reprovaria uma
     * configuração correta. Agora o alvo é real, e o erro aponta qual conta falhou.
     */
    val checkTeamConnection: () -> Unit = {
        teamConnectionState = TeamConnectionUiState(TeamConnectionUiStatus.CHECKING)
        teamScope.launch {
            val healthError = teamUsageRepository.checkConnection().exceptionOrNull()
            if (healthError != null) {
                teamConnectionState = TeamConnectionUiState(
                    status = TeamConnectionUiStatus.FAILED,
                    message = healthError.message
                        ?: if (language == AppLanguage.PT) "Falha desconhecida." else "Unknown failure."
                )
                return@launch
            }

            val current = teamSettingsFlow.value
            val targets = buildTeamSyncTargets(profileRegistry)
                .filter { target -> current.participates(target.profileId) }

            if (targets.isEmpty()) {
                teamConnectionState = TeamConnectionUiState(
                    status = TeamConnectionUiStatus.OK,
                    message = if (language == AppLanguage.PT) {
                        "Servidor OK. Marque uma conta para conferir a chave."
                    } else {
                        "Server OK. Select an account to check the key."
                    }
                )
                return@launch
            }

            val failures = mutableListOf<String>()
            for (target in targets) {
                val label = profileUiModels.firstOrNull { profile -> profile.id == target.profileId }
                    ?.label
                    ?: target.profileId
                // Vincula, e não apenas confere: o vínculo antes só nascia dentro
                // de um envio de turnos, então numa máquina já sincronizada ele
                // nunca acontecia e a leitura ficava recusada indefinidamente.
                val result = claimTeamKeyForAccount(target.accountKey)
                val error = result.exceptionOrNull()
                if (error != null) {
                    failures += "$label: ${error.message.orEmpty()}"
                } else if (result.getOrNull()?.authorized != true) {
                    failures += if (language == AppLanguage.PT) {
                        "$label: a chave não cobre esta conta."
                    } else {
                        "$label: the key does not cover this account."
                    }
                }
            }

            teamConnectionState = if (failures.isEmpty()) {
                TeamConnectionUiState(
                    status = TeamConnectionUiStatus.OK,
                    message = if (language == AppLanguage.PT) {
                        "Conexão OK e conta vinculada a esta chave."
                    } else {
                        "Connection OK and account linked to this key."
                    }
                )
            } else {
                TeamConnectionUiState(
                    status = TeamConnectionUiStatus.FAILED,
                    message = failures.joinToString(" • ")
                )
            }
        }
    }

    // Cada emissão precisa de um id próprio: dois avisos iguais em sequência —
    // salvar o mesmo campo duas vezes — seriam o mesmo valor e o diálogo não
    // reagiria ao segundo.
    var settingsToastEvent by remember { mutableStateOf<SettingsToastEvent?>(null) }
    var settingsToastGeneration by remember { mutableStateOf(0) }
    val showSettingsToast: (SettingsToast) -> Unit = { toast ->
        settingsToastGeneration += 1
        settingsToastEvent = SettingsToastEvent(id = settingsToastGeneration, toast = toast)
    }
    /** Traduz o resultado da gravação no aviso correspondente. */
    val reportSettingsSave: (SettingsField, Boolean) -> Unit = { field, saved ->
        showSettingsToast(
            if (saved) SettingsToast.Saved(field) else SettingsToast.SaveFailed(field)
        )
    }
    // A opacidade é gravada pelo coletor com debounce declarado acima, que roda
    // fora do diálogo; o aviso é emitido aqui, onde `showSettingsToast` existe.
    // A geração inicial não conta: o `snapshotFlow` reemite o valor corrente
    // quando o app sobe, sem que ninguém tenha mexido em nada.
    LaunchedEffect(opacitySaveGeneration) {
        if (opacitySaveGeneration > 0) {
            showSettingsToast(SettingsToast.Saved(SettingsField.WINDOW_OPACITY))
        }
    }
    // Mesma razão do bloco acima: quem grava é o coletor com debounce, e a geração
    // inicial não conta porque ninguém mexeu em nada.
    LaunchedEffect(uiScaleSaveGeneration) {
        if (uiScaleSaveGeneration > 0) {
            showSettingsToast(SettingsToast.Saved(SettingsField.UI_SCALE))
        }
    }

    // Os filtros de 5h e 7d da tela de sessões recortam a janela de quota da
    // conta, não as últimas horas corridas. O reset vem do mesmo `resets_at` que
    // alimenta os medidores do card.
    val dashboardState by viewModel.uiState.collectAsState()
    val cliSessionsQuotaWindows = remember(dashboardState, cliSessionsProfileId) {
        quotaWindowsForProfile(dashboardState, cliSessionsProfileId)
    }
    LaunchedEffect(cliSessionsQuotaWindows, isCliSessionsOpen) {
        if (isCliSessionsOpen) {
            cliSessionsViewModel.setQuotaWindows(cliSessionsQuotaWindows)
        }
    }
    // Os créditos vêm da API da Anthropic, que só o dashboard consulta; a janela
    // de sessões conhece apenas o índice local, então eles chegam prontos daqui.
    val cliSessionsAccountCredits = remember(dashboardState, cliSessionsProfileId) {
        accountCreditsForProfile(dashboardState, cliSessionsProfileId)
    }
    LaunchedEffect(cliSessionsAccountCredits, isCliSessionsOpen) {
        if (isCliSessionsOpen) {
            cliSessionsViewModel.setAccountCredits(cliSessionsAccountCredits)
        }
    }
    LaunchedEffect(monthlyBudgetMicros, isCliSessionsOpen) {
        if (isCliSessionsOpen) {
            cliSessionsViewModel.setBudgetLimitMicros(monthlyBudgetMicros)
        }
    }
    // O time é uma conta Anthropic: a janela de 5h dele ancora no mesmo reset de
    // quota que o card mede, senão os números do time não fecham com os locais.
    val teamUsageQuotaWindows = remember(dashboardState, teamUsageProfileId) {
        quotaWindowsForProfile(dashboardState, teamUsageProfileId)
    }
    LaunchedEffect(teamUsageQuotaWindows, isTeamUsageOpen) {
        if (isTeamUsageOpen) {
            teamUsageViewModel.setQuotaWindows(teamUsageQuotaWindows)
        }
    }
    val shutdownApplication = remember(viewModel, historyViewModel, httpClient, usageHistoryDataSource, openCodeUsageDataSource, kiloUsageDataSource) {
        {
            if (shutdownStarted.compareAndSet(false, true)) {
                viewModel.onDestroy()
                historyViewModel.onDestroy()
                cliSessionsViewModel.onDestroy()
                teamUsageViewModel.onDestroy()
                teamPresenceViewModel.onDestroy()
                sessionPulseViewModel.onDestroy()
                usageAlertViewModel.onDestroy()
                teamSyncService.onDestroy()
                httpClient.close()
                usageHistoryDataSource.close()
                cliSessionDataSource.close()
                openCodeUsageDataSource.close()
                kiloUsageDataSource.close()
                singleInstanceGuard.close()
                // Por último, e não primeiro: o instalador espera este processo
                // sair de qualquer forma, mas entregar o pacote antes de o SQLite
                // fechar seria abrir uma janela para a troca de arquivos correr
                // contra a escrita do banco.
                viewModel.scheduleUpdateOnExit()
            }
            exitProcess(0)
        }
    }
    // "Reiniciar e atualizar agora" reusa a mesma saída ordenada do resto do app:
    // um segundo caminho de encerramento seria um segundo lugar para esquecer de
    // fechar o banco.
    autoUpdate.bindRestart(shutdownApplication)

    val restoreMainWindow = {
        mainWindowState.isMinimized = false
        mainWindowRef?.let { window -> activateWindow(window) }
        Unit
    }

    // O mesmo caminho do item "Abrir" da bandeja, e nao um segundo: um segundo
    // seria um segundo lugar para esquecer de desminimizar. A leitura vai para a
    // IO porque este efeito roda na thread da interface.
    LaunchedEffect(focusRequests) {
        while (isActive) {
            delay(FocusRequestChannel.POLL_INTERVAL_MILLIS)
            val focusRequested = withContext(Dispatchers.IO) { focusRequests.consume() }
            if (focusRequested) {
                restoreMainWindow()
            }
        }
    }

    if (isTraySupported) {
        val trayState = rememberTrayState()
        val worstRisk by usageAlertViewModel.worstRisk.collectAsState()
        val trayIcon = remember(iconImage, worstRisk) { TrayRiskIconPainter(iconImage, worstRisk) }

        Tray(
            icon = trayIcon,
            state = trayState,
            tooltip = "Usage Monitor",
            onAction = restoreMainWindow,
            menu = {
                Item(
                    text = if (language == AppLanguage.PT) "Abrir" else "Open",
                    onClick = restoreMainWindow
                )
                Item(
                    text = if (language == AppLanguage.PT) "Atualizar agora" else "Refresh now",
                    onClick = { viewModel.refresh() }
                )
                // As duas entram por causa do modo somente cards: com o rodapé
                // escondido, a engrenagem e a volta ao modo normal não existem em
                // lugar nenhum da janela até o mouse passar pelo topo dela.
                Item(
                    text = if (language == AppLanguage.PT) "Configurações" else "Settings",
                    onClick = {
                        isSettingsDialogOpen = true
                        settingsOpenGeneration++
                    }
                )
                Item(
                    text = if (cardsOnlyMode) {
                        if (language == AppLanguage.PT) "Sair do modo somente cards" else "Exit cards only mode"
                    } else {
                        if (language == AppLanguage.PT) "Somente os cards" else "Cards only"
                    },
                    onClick = { setCardsOnlyMode(!cardsOnlyMode) }
                )
                Separator()
                Item(
                    text = if (language == AppLanguage.PT) "Sair" else "Quit",
                    onClick = { shutdownApplication() }
                )
            }
        )

        // A língua entra na chave: trocar o idioma tem de recomeçar a coleta com o
        // valor novo, senão a notificação seguinte sairia no idioma anterior.
        LaunchedEffect(usageAlertViewModel, trayState, language) {
            usageAlertViewModel.alerts.collect { alert ->
                val message = usageAlertMessage(alert, language)
                trayState.sendNotification(
                    Notification(
                        title = message.title,
                        message = message.body,
                        type = Notification.Type.Warning
                    )
                )
            }
        }
    }

    Window(
        onCloseRequest = {
            shutdownApplication()
        },
        title = "Usage Monitor",
        icon = iconImage,
        state = mainWindowState,
        undecorated = true,
        alwaysOnTop = alwaysOnTopEnabled,
        // Terceira saída do modo somente cards, ao lado da bandeja e da faixa de
        // hover. Um modo que esconde o botão de fechar precisa de mais de um
        // caminho de volta, e o teclado é o único que funciona com a janela
        // coberta por outra.
        onKeyEvent = { event ->
            val isToggle = event.type == KeyEventType.KeyDown &&
                event.isCtrlPressed &&
                event.isShiftPressed &&
                event.key == Key.M
            if (isToggle) {
                setCardsOnlyMode(!cardsOnlyMode)
            }
            isToggle
        }
    ) {
        LaunchedEffect(window) {
            mainWindowRef = window
        }
        LaunchedEffect(windowOpacityPercent) {
            applyWindowOpacity(window, windowOpacityPercent)
        }
        AppTheme(isDark = isDark, uiScalePercent = uiScalePercent) {
            DesktopWindowFrame(
                title = "Usage Monitor",
                iconPainter = iconImage,
                windowState = mainWindowState,
                onCloseRequest = {
                    shutdownApplication()
                },
                compact = cardsOnlyMode,
                onExitCompact = { setCardsOnlyMode(false) }
            ) {
                DashboardScreen(
                    viewModel = viewModel,
                    appVersion = CURRENT_APP_VERSION,
                    language = language,
                    cardOrder = cardOrder,
                    minimizedCards = minimizedCards,
                    onMoveCardToIndex = { target, targetIndex ->
                        val visibleTargets = enabledUsageTargets(
                            enabledSources = enabledApisState,
                            enabledProfiles = profileResolution.enabledProfiles
                        )
                        val updatedOrder = moveVisibleCardToIndex(
                            currentOrder = cardOrder,
                            visibleTargets = visibleTargets,
                            target = target,
                            targetIndex = targetIndex
                        )
                        cardOrder = updatedOrder
                        writeUsageTargetCollection(settings, CARD_ORDER_KEY, updatedOrder)
                    },
                    onToggleCardMinimized = { target ->
                        val updatedMinimizedCards = if (target in minimizedCards) {
                            minimizedCards - target
                        } else {
                            minimizedCards + target
                        }
                        minimizedCards = updatedMinimizedCards
                        writeUsageTargetCollection(settings, MINIMIZED_CARDS_KEY, updatedMinimizedCards)
                    },
                    onOpenHistory = { source, accountKey ->
                        historyDialogSource = source
                        historyOpenGeneration++
                        historyViewModel.openForSource(source, accountKey)
                    },
                    onOpenSettings = {
                        isSettingsDialogOpen = true
                        settingsOpenGeneration++
                    },
                    // Só quem administra recebe o botão; `null` esconde. A conta
                    // não entra na condição de propósito: administrar o servidor
                    // não exige participar de nenhum time.
                    onOpenAdminOverview = if (teamSettings.isAdminMode) {
                        {
                            teamUsageAccountLabel = null
                            teamUsageProfileId = null
                            teamUsageIsAdminOverview = true
                            isTeamUsageOpen = true
                            teamUsageOpenGeneration++
                            teamUsageViewModel.openForAllAccounts()
                        }
                    } else {
                        null
                    },
                    // Também só para quem administra: aqui o escopo é o servidor
                    // inteiro. O integrante comum entra pelo botão do card, que
                    // já é escopado na conta dele.
                    onOpenTeamPresenceOverview = if (teamSettings.isAdminMode) {
                        {
                            teamPresenceAccountLabel = null
                            teamPresenceIsAdminOverview = true
                            isTeamPresenceOpen = true
                            teamPresenceOpenGeneration++
                            teamPresenceViewModel.openForAllAccounts()
                        }
                    } else {
                        null
                    },
                    onOpenCliSessions = { target ->
                        val profileId = target.profileId ?: DEFAULT_ANTHROPIC_PROFILE_ID
                        val label = profileRecords
                            .firstOrNull { record -> record.id == profileId }
                            ?.label
                        cliSessionsProfileLabel = label
                        cliSessionsProfileId = profileId
                        isCliSessionsOpen = true
                        cliSessionsOpenGeneration++
                        cliSessionsViewModel.openForProfile(
                            profileId = profileId,
                            profileLabel = label,
                            quotaWindows = quotaWindowsForProfile(dashboardState, profileId)
                        )
                    },
                    onOpenTeamUsage = { target ->
                        val profileId = target.profileId ?: DEFAULT_ANTHROPIC_PROFILE_ID
                        val accountContext = profileResolution.inspections[profileId]?.accountContext
                        // Sem `accountUuid` não há como agrupar as máquinas — e o
                        // botão nem deveria ter aparecido. Abortar é melhor que
                        // consultar o servidor com uma chave inventada.
                        val accountKey = accountContext?.key?.providerAccountId
                        if (accountKey != null) {
                            teamUsageAccountLabel = accountContext.displayLabel
                            teamUsageProfileId = profileId
                            teamUsageIsAdminOverview = false
                            isTeamUsageOpen = true
                            teamUsageOpenGeneration++
                            teamUsageViewModel.openForAccount(
                                accountKey = accountKey,
                                accountLabel = accountContext.displayLabel,
                                quotaWindows = quotaWindowsForProfile(dashboardState, profileId)
                            )
                            // Antecipa o envio desta máquina: sem isso a janela
                            // abriria mostrando o time sem o que foi feito aqui
                            // desde o último tique de 30s.
                            teamSyncService.requestImmediateSync()
                        }
                    },
                    onOpenTeamPresence = { target ->
                        val profileId = target.profileId ?: DEFAULT_ANTHROPIC_PROFILE_ID
                        val accountContext = profileResolution.inspections[profileId]?.accountContext
                        val accountKey = accountContext?.key?.providerAccountId
                        if (accountKey != null) {
                            teamPresenceAccountLabel = accountContext.displayLabel
                            teamPresenceIsAdminOverview = false
                            isTeamPresenceOpen = true
                            teamPresenceOpenGeneration++
                            teamPresenceViewModel.openForAccount(
                                accountKey = accountKey,
                                accountLabel = accountContext.displayLabel
                            )
                            // Antecipa a batida desta máquina: sem isso a janela
                            // abriria com o próprio usuário aparecendo offline
                            // por até 30 segundos.
                            teamSyncService.requestImmediateSync()
                        }
                    },
                    // Vazio quando a integração está desligada: o botão some de
                    // todos os cards sem nenhuma outra condição espalhada na tela.
                    teamEnabledProfileIds = if (teamSettings.isActive) {
                        teamSettings.participatingProfileIds
                    } else {
                        emptySet()
                    },
                    cliSessionPulses = cliSessionPulses,
                    teamSessionPulses = teamSessionPulses,
                    showFooter = !cardsOnlyMode
                )
            }
        }
    }

    historyDialogSource?.let { source ->
        Window(
            onCloseRequest = { historyDialogSource = null },
            title = historyWindowTitle(source, language),
            icon = iconImage,
            state = historyWindowState,
            resizable = true,
            undecorated = true
        ) {
            LaunchedEffect(historyOpenGeneration) {
                activateWindow(window)
            }
            AppTheme(isDark = isDark, uiScalePercent = uiScalePercent) {
                DesktopDialogFrame(
                    title = historyWindowTitle(source, language),
                    iconPainter = iconImage,
                    windowState = historyWindowState,
                    onCloseRequest = { historyDialogSource = null }
                ) {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        language = language,
                        onBack = { historyDialogSource = null },
                        focusedSource = source,
                        showSourceSelector = false
                    )
                }
            }
        }
    }

    if (isCliSessionsOpen) {
        val cliSessionsTitle = cliSessionsWindowTitle(language, cliSessionsProfileLabel)
        // Sem avisar o ViewModel, o laço ao vivo continuaria indexando de cinco em
        // cinco segundos com a janela fechada.
        val closeCliSessions = {
            isCliSessionsOpen = false
            cliSessionsViewModel.closeWindow()
        }
        Window(
            onCloseRequest = closeCliSessions,
            title = cliSessionsTitle,
            icon = iconImage,
            state = cliSessionsWindowState,
            resizable = true,
            undecorated = true
        ) {
            LaunchedEffect(cliSessionsOpenGeneration) {
                activateWindow(window)
            }
            ApplyWindowMinimumSize(
                window = window,
                widthDp = CLI_SESSIONS_MIN_WINDOW_WIDTH_DP,
                heightDp = CLI_SESSIONS_MIN_WINDOW_HEIGHT_DP,
                uiScalePercent = uiScalePercent,
                workArea = screenWorkArea
            )
            AppTheme(isDark = isDark, uiScalePercent = uiScalePercent) {
                DesktopDialogFrame(
                    title = cliSessionsTitle,
                    iconPainter = iconImage,
                    windowState = cliSessionsWindowState,
                    onCloseRequest = closeCliSessions
                ) {
                    CliSessionsScreen(
                        viewModel = cliSessionsViewModel,
                        language = language
                    )
                }
            }
        }
    }

    if (isTeamUsageOpen) {
        val teamTitle = teamUsageWindowTitle(
            language = language,
            accountLabel = teamUsageAccountLabel,
            isAdminOverview = teamUsageIsAdminOverview
        )
        // Sem avisar o ViewModel, o laço ao vivo continuaria consultando o
        // servidor de cinco em cinco segundos com a janela fechada.
        val closeTeamUsage = {
            isTeamUsageOpen = false
            teamUsageViewModel.closeWindow()
        }
        Window(
            onCloseRequest = closeTeamUsage,
            title = teamTitle,
            icon = iconImage,
            state = teamUsageWindowState,
            resizable = true,
            undecorated = true
        ) {
            LaunchedEffect(teamUsageOpenGeneration) {
                activateWindow(window)
            }
            ApplyWindowMinimumSize(
                window = window,
                widthDp = TEAM_USAGE_MIN_WINDOW_WIDTH_DP,
                heightDp = TEAM_USAGE_MIN_WINDOW_HEIGHT_DP,
                uiScalePercent = uiScalePercent,
                workArea = screenWorkArea
            )
            AppTheme(isDark = isDark, uiScalePercent = uiScalePercent) {
                DesktopDialogFrame(
                    title = teamTitle,
                    iconPainter = iconImage,
                    windowState = teamUsageWindowState,
                    onCloseRequest = closeTeamUsage
                ) {
                    TeamUsageScreen(
                        viewModel = teamUsageViewModel,
                        language = language
                    )
                }
            }
        }
    }

    if (isTeamPresenceOpen) {
        val presenceTitle = teamPresenceWindowTitle(
            language = language,
            accountLabel = teamPresenceAccountLabel,
            isAdminOverview = teamPresenceIsAdminOverview
        )
        // Sem avisar o ViewModel, o laço ao vivo continuaria consultando o
        // servidor de cinco em cinco segundos com a janela fechada.
        val closeTeamPresence = {
            isTeamPresenceOpen = false
            teamPresenceViewModel.closeWindow()
        }
        Window(
            onCloseRequest = closeTeamPresence,
            title = presenceTitle,
            icon = iconImage,
            state = teamPresenceWindowState,
            resizable = true,
            undecorated = true
        ) {
            LaunchedEffect(teamPresenceOpenGeneration) {
                activateWindow(window)
            }
            ApplyWindowMinimumSize(
                window = window,
                widthDp = TEAM_PRESENCE_MIN_WINDOW_WIDTH_DP,
                heightDp = TEAM_PRESENCE_MIN_WINDOW_HEIGHT_DP,
                uiScalePercent = uiScalePercent,
                workArea = screenWorkArea
            )
            AppTheme(isDark = isDark, uiScalePercent = uiScalePercent) {
                DesktopDialogFrame(
                    title = presenceTitle,
                    iconPainter = iconImage,
                    windowState = teamPresenceWindowState,
                    onCloseRequest = closeTeamPresence
                ) {
                    TeamPresenceScreen(
                        viewModel = teamPresenceViewModel,
                        language = language,
                        localDeviceId = teamSettings.deviceId.takeIf { it.isNotBlank() },
                        canManage = teamSettings.isAdminMode
                    )
                }
            }
        }
    }

    if (isTeamKeysOpen) {
        val keysTitle = if (language == AppLanguage.PT) {
            "Chaves das contas"
        } else {
            "Account keys"
        }
        DialogWindow(
            onCloseRequest = { isTeamKeysOpen = false },
            title = keysTitle,
            icon = iconImage,
            // O tamanho literal acompanha a escala: a 150% o conteúdo cresce e a
            // moldura fixa o espremeria. E é preso à área útil pelo mesmo motivo das
            // janelas: o diálogo também é `undecorated`.
            state = rememberDialogState(
                size = fitWindowSize(
                    DpSize(
                        width = 760.dp * uiScaleFactor(uiScalePercent),
                        height = 640.dp * uiScaleFactor(uiScalePercent)
                    ),
                    screenWorkArea
                )
            ),
            undecorated = true
        ) {
            AppTheme(isDark = isDark, uiScalePercent = uiScalePercent) {
                DesktopDialogFrame(
                    title = keysTitle,
                    iconPainter = iconImage,
                    onCloseRequest = { isTeamKeysOpen = false }
                ) {
                    TeamKeysAdminScreen(
                        viewModel = teamKeysViewModel,
                        language = language
                    )
                }
            }
        }
    }

    if (isSettingsDialogOpen) {
        DialogWindow(
            onCloseRequest = { isSettingsDialogOpen = false },
            title = if (language == AppLanguage.PT) "Configurações" else "Settings",
            icon = iconImage,
            // 820 de largura: as Configurações passaram a ter navegação lateral
            // de 150dp, e em 620 o conteúdo ficava com menos de 470 — estreito
            // demais para as linhas de rótulo + controle das seções de Time.
            state = rememberDialogState(
                size = fitWindowSize(
                    DpSize(
                        width = 820.dp * uiScaleFactor(uiScalePercent),
                        height = 720.dp * uiScaleFactor(uiScalePercent)
                    ),
                    screenWorkArea
                )
            ),
            resizable = true,
            undecorated = true
        ) {
            LaunchedEffect(settingsOpenGeneration) {
                activateWindow(window)
            }
            AppTheme(isDark = isDark, uiScalePercent = uiScalePercent) {
                DesktopDialogFrame(
                    title = if (language == AppLanguage.PT) "Configurações" else "Settings",
                    iconPainter = iconImage,
                    onCloseRequest = { isSettingsDialogOpen = false }
                ) {
                    SettingsDialogContent(
                        currentTheme = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT,
                        currentLanguage = language,
                        enabledApis = enabledApisState,
                        autoStartEnabled = autoStartEnabled,
                        alwaysOnTopEnabled = alwaysOnTopEnabled,
                        cardsOnlyMode = cardsOnlyMode,
                        windowOpacityPercent = windowOpacityPercent,
                        windowOpacityEnabled = windowOpacitySupported,
                        uiScalePercent = uiScalePercent,
                        onUiScaleChange = { percent ->
                            // Aviso e gravação não saem daqui pelo mesmo motivo da
                            // opacidade: quem persiste é o coletor com debounce.
                            uiScalePercent = clampUiScalePercent(percent)
                        },
                        onThemeToggle = {
                            isDark = !isDark
                            settings.putBoolean(IS_DARK_KEY, isDark)
                            showSettingsToast(SettingsToast.Saved(SettingsField.THEME))
                        },
                        onLanguageChange = { selectedLanguage ->
                            language = selectedLanguage
                            settings.putString(LANGUAGE_KEY, selectedLanguage.name)
                            showSettingsToast(SettingsToast.Saved(SettingsField.LANGUAGE))
                        },
                        onAutoStartChange = { enabled ->
                            // O registro do Windows pode recusar a escrita; nesse
                            // caso o estado volta ao que o sistema realmente tem e
                            // o aviso precisa dizer que falhou.
                            val applied = AutoStartManager.setAutoStart(enabled)
                            val updatedState = if (applied) {
                                enabled
                            } else {
                                AutoStartManager.isAutoStartEnabled()
                            }
                            autoStartEnabled = updatedState
                            settings.putBoolean(AUTO_START_KEY, updatedState)
                            reportSettingsSave(SettingsField.AUTO_START, applied)
                        },
                        onAlwaysOnTopChange = { enabled ->
                            alwaysOnTopEnabled = enabled
                            settings.putBoolean(ALWAYS_ON_TOP_KEY, enabled)
                            showSettingsToast(SettingsToast.Saved(SettingsField.ALWAYS_ON_TOP))
                        },
                        onCardsOnlyModeChange = { enabled ->
                            setCardsOnlyMode(enabled)
                            showSettingsToast(SettingsToast.Saved(SettingsField.CARDS_ONLY_MODE))
                        },
                        autoUpdateEnabled = autoUpdate.isEnabled(),
                        autoUpdateSupport = autoUpdate.support,
                        lastUpdateReceipt = autoUpdate.lastReceipt,
                        autoUpdateFeedOverride = autoUpdate.feedUrlOverride,
                        onAutoUpdateChange = { enabled -> autoUpdate.setEnabled(enabled) },
                        onWindowOpacityChange = { percent ->
                            // Aviso não sai daqui: quem persiste é o coletor com
                            // debounce lá em cima, e arrastar o slider dispararia
                            // um toast por pixel.
                            windowOpacityPercent = clampWindowOpacityPercent(percent)
                        },
                        alertSettings = alertSettingsState,
                        onAlertSettingsChange = { updated ->
                            alertSettingsFlow.value = updated
                            persistAlertSettings(settings, updated)
                            showSettingsToast(SettingsToast.Saved(SettingsField.ALERTS))
                        },
                        monthlyBudgetText = formatBudgetUsd(monthlyBudgetMicros),
                        onMonthlyBudgetCommit = { text ->
                            // Texto inválido não grava: o campo já recusa pelo
                            // `validate`, e gravar zero aqui desligaria o teto em
                            // silêncio no meio de uma digitação.
                            val parsed = parseBudgetUsd(text)
                            if (parsed != null) {
                                monthlyBudgetMicros = parsed
                                persistBudgetMicros(settings, parsed)
                                showSettingsToast(SettingsToast.Saved(SettingsField.ALERTS))
                            }
                        },
                        onApiToggle = { api, checked ->
                            val updatedApis = if (checked) {
                                enabledApis.value + api
                            } else {
                                enabledApis.value - api
                            }
                            enabledApis.value = updatedApis
                            writeApiSourceCollection(settings, ENABLED_APIS_KEY, updatedApis)
                            viewModel.refresh(api)
                            showSettingsToast(SettingsToast.Saved(SettingsField.MONITORED_APIS))
                        },
                        anthropicProfiles = profileUiModels,
                        onAnthropicProfileToggle = { profileId, checked ->
                            profileRegistry.setEnabled(profileId, checked)
                            enabledAnthropicProfiles.value = resolveAnthropicProfiles(
                                profileRegistry,
                                profileRegistry.profiles.value
                            ).enabledProfiles
                            viewModel.refresh(ApiSource.ANTHROPIC)
                            showSettingsToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
                        },
                        onAnthropicProfileRename = { profileId, label ->
                            profileRegistry.updateLabel(profileId, label)
                            showSettingsToast(
                                SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILE_LABEL)
                            )
                        },
                        onAddAnthropicProfile = {
                            val selectedDirectory = chooseAnthropicConfigDirectory()
                            if (selectedDirectory != null) {
                                profileRegistry.addManual(selectedDirectory)
                                enabledAnthropicProfiles.value = resolveAnthropicProfiles(
                                    profileRegistry,
                                    profileRegistry.profiles.value
                                ).enabledProfiles
                            }
                        },
                        onRemoveAnthropicProfile = { profileId ->
                            profileRegistry.removeFromMonitor(profileId)
                            enabledAnthropicProfiles.value = resolveAnthropicProfiles(
                                profileRegistry,
                                profileRegistry.profiles.value
                            ).enabledProfiles
                            viewModel.refresh(ApiSource.ANTHROPIC)
                            showSettingsToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
                        },
                        onRescanAnthropicProfiles = {
                            profileRegistry.rescan(restoreRemoved = true)
                            enabledAnthropicProfiles.value = resolveAnthropicProfiles(
                                profileRegistry,
                                profileRegistry.profiles.value
                            ).enabledProfiles
                            viewModel.refresh(ApiSource.ANTHROPIC)
                        },
                        expandedProfileId = expandedAnthropicProfileId,
                        onToggleProfileExpanded = { profileId ->
                            expandedAnthropicProfileId = if (expandedAnthropicProfileId == profileId) {
                                null
                            } else {
                                profileId
                            }
                        },
                        teamSettings = teamSettings,
                        teamConnection = teamConnectionState,
                        onTeamEnabledChange = { enabled ->
                            val saved = updateTeamSettings(
                                teamSettingsFlow,
                                teamSettingsDataSource
                            ) { current -> current.copy(enabled = enabled) }
                            // Mudar de servidor ou religar a integração não pode
                            // deixar um resultado antigo na tela como se fosse atual.
                            teamConnectionState = TeamConnectionUiState()
                            reportSettingsSave(SettingsField.TEAM_INTEGRATION, saved)
                        },
                        onTeamServerUrlChange = { url ->
                            val saved = updateTeamSettings(
                                teamSettingsFlow,
                                teamSettingsDataSource
                            ) { current -> current.copy(serverUrl = url) }
                            teamConnectionState = TeamConnectionUiState()
                            reportSettingsSave(SettingsField.TEAM_SERVER, saved)
                        },
                        onTeamApiKeyChange = { key ->
                            val saved = updateTeamSettings(
                                teamSettingsFlow,
                                teamSettingsDataSource
                            ) { current -> current.copy(apiKey = key) }
                            reportSettingsSave(SettingsField.TEAM_KEY, saved)
                            // Vincula na hora: uma chave que não cobre a conta
                            // marcada faria o envio falhar em silêncio a cada 30s,
                            // e o usuário só descobriria pela ausência dos dados.
                            if (saved) {
                                checkTeamConnection()
                                // A identidade tem de sair com a chave nova, senão
                                // o servidor continua sem saber a quem ela pertence
                                // até surgir turno novo no Claude Code.
                                teamSyncService.requestImmediateSync()
                            } else {
                                teamConnectionState = TeamConnectionUiState()
                            }
                        },
                        onTeamAliasChange = { alias ->
                            // O campo já barra apagar um apelido gravado; esta é a
                            // rede de baixo, para nenhum outro caminho zerá-lo.
                            if (alias.isBlank() && teamSettings.alias.isNotBlank()) {
                                showSettingsToast(SettingsToast.TeamAliasRequired)
                            } else {
                                val saved = updateTeamSettings(
                                    teamSettingsFlow,
                                    teamSettingsDataSource
                                ) { current -> current.copy(alias = alias) }
                                reportSettingsSave(SettingsField.TEAM_ALIAS, saved)
                                // O apelido só chega ao servidor dentro de um
                                // ingest: sem antecipar a passada, o nome novo
                                // esperaria o tique de 30s para aparecer ao time.
                                if (saved) {
                                    teamSyncService.requestImmediateSync()
                                }
                            }
                        },
                        onTeamProfileParticipationChange = { profileId, participates ->
                            val saved = updateTeamSettings(
                                teamSettingsFlow,
                                teamSettingsDataSource
                            ) { current ->
                                val updated = if (participates) {
                                    current.participatingProfileIds + profileId
                                } else {
                                    current.participatingProfileIds - profileId
                                }
                                current.copy(participatingProfileIds = updated)
                            }
                            reportSettingsSave(SettingsField.TEAM_ACCOUNTS, saved)
                            // Marcar uma conta é justamente o momento em que o
                            // vínculo da chave passa a importar.
                            if (saved && participates) {
                                checkTeamConnection()
                            }
                        },
                        onTeamTestConnection = checkTeamConnection,
                        teamSyncFailureMessage = teamSyncStatus
                            .takeIf { status -> status.isFailing }
                            ?.lastFailureMessage,
                        teamAdminConnection = teamAdminConnectionState,
                        onTeamAdminTokenChange = { token ->
                            val saved = updateTeamSettings(
                                teamSettingsFlow,
                                teamSettingsDataSource
                            ) { current -> current.copy(adminToken = token) }
                            teamAdminConnectionState = TeamConnectionUiState()
                            reportSettingsSave(SettingsField.TEAM_ADMIN_TOKEN, saved)
                        },
                        onTeamValidateAdminToken = {
                            teamAdminConnectionState =
                                TeamConnectionUiState(TeamConnectionUiStatus.CHECKING)
                            teamScope.launch {
                                val error = validateAdminToken().exceptionOrNull()
                                teamAdminConnectionState = if (error == null) {
                                    TeamConnectionUiState(
                                        status = TeamConnectionUiStatus.OK,
                                        message = if (language == AppLanguage.PT) {
                                            "Token válido."
                                        } else {
                                            "Token is valid."
                                        }
                                    )
                                } else {
                                    TeamConnectionUiState(
                                        status = TeamConnectionUiStatus.FAILED,
                                        message = error.message
                                            ?: if (language == AppLanguage.PT) {
                                                "Falha desconhecida."
                                            } else {
                                                "Unknown failure."
                                            }
                                    )
                                }
                            }
                        },
                        onTeamOpenKeysManager = {
                            isTeamKeysOpen = true
                            teamKeysViewModel.open()
                        },
                        onTeamExitAdminMode = {
                            val saved = updateTeamSettings(
                                teamSettingsFlow,
                                teamSettingsDataSource
                            ) { current -> current.copy(adminToken = "") }
                            teamAdminConnectionState = TeamConnectionUiState()
                            isTeamKeysOpen = false
                            reportSettingsSave(SettingsField.TEAM_ADMIN_TOKEN, saved)
                        },
                        toastEvent = settingsToastEvent
                    )
                }
            }
        }
    }
}

/**
 * Piso de arrasto da janela AWT, em `Dp` de interface.
 *
 * As três janelas de lista — Sessões CLI, Sessões do time e Presença — têm faixa
 * de legendas de coluna sobre linhas de largura fixa. Abaixo do orçamento de
 * colunas a linha quebra e as colunas param de alinhar; o tamanho persistido não
 * protege nada, porque quem arrasta a borda é o usuário.
 *
 * A unidade da janela AWT é a mesma `Dp` do `WindowState` — o Compose Desktop
 * converte 1:1 (`setSizeImpl` usa `size.width.value`), e a densidade do sistema
 * fica só no desenho. O que entra aqui é a **escala da interface**, pelo mesmo
 * motivo de `scaledWindowSize`: ela multiplica a densidade do conteúdo, então a
 * 150% a mesma janela mostra menos colunas e o piso precisa subir junto.
 *
 * O piso também é preso à área útil: a 150% ele daria 1410dp de largura, mais que
 * um monitor de 1366, e um piso maior que a tela não é piso — é janela que nem
 * arrastando a borda cabe.
 *
 * Fora de `main()` de propósito: aquele composable já está no limite do backend
 * JVM, e três cópias deste efeito seriam três lugares para o orçamento divergir.
 */
@Composable
private fun ApplyWindowMinimumSize(
    window: java.awt.Window,
    widthDp: Int,
    heightDp: Int,
    uiScalePercent: Int,
    workArea: ScreenWorkArea
) {
    val scale = uiScaleFactor(uiScalePercent)
    LaunchedEffect(window, scale, workArea, widthDp, heightDp) {
        val minimum = fitWindowSize(
            DpSize(width = widthDp.dp * scale, height = heightDp.dp * scale),
            workArea
        )
        window.minimumSize = java.awt.Dimension(
            minimum.width.value.roundToInt(),
            minimum.height.value.roundToInt()
        )
    }
}

@Composable
private fun rememberPersistedMainWindowState(
    persistedState: PersistedMainWindowState,
    uiScalePercent: Int,
    workArea: ScreenWorkArea
) = when {
    persistedState.widthDp != null && persistedState.heightDp != null -> {
        rememberWindowState(
            placement = persistedState.composePlacement,
            size = fitWindowSize(
                DpSize(
                    width = persistedState.composeWidth,
                    height = persistedState.composeHeight
                ),
                workArea
            )
        )
    }

    persistedState.placement == PersistedWindowPlacement.MAXIMIZED -> {
        rememberWindowState(
            placement = persistedState.composePlacement
        )
    }

    // 800×600 é o default do próprio `rememberWindowState`, agora explícito para
    // acompanhar a escala: na primeira execução não há tamanho persistido, e sem
    // isto o app subiria com a moldura de 100% e o conteúdo de 115%.
    else -> rememberWindowState(
        size = fitWindowSize(
            DpSize(
                width = 800.dp * uiScaleFactor(uiScalePercent),
                height = 600.dp * uiScaleFactor(uiScalePercent)
            ),
            workArea
        )
    )
}

private fun readApiSourceCollection(
    settings: PreferencesSettings,
    key: String
): List<ApiSource> {
    return settings.getStringOrNull(key)
        ?.split(",")
        ?.filter { token -> token.isNotBlank() }
        ?.mapNotNull { token -> runCatching { ApiSource.valueOf(token) }.getOrNull() }
        ?: emptyList()
}

private fun writeApiSourceCollection(
    settings: PreferencesSettings,
    key: String,
    sources: Collection<ApiSource>
) {
    settings.putString(
        key,
        sources.joinToString(",") { source -> source.name }
    )
}

internal data class AnthropicProfileResolution(
    val enabledProfiles: List<AnthropicProfileRef>,
    val inspections: Map<String, AnthropicProfileInspection>,
    val duplicateProfileIds: Set<String>
)

internal fun resolveAnthropicProfiles(
    registry: AnthropicProfileRegistry,
    records: List<AnthropicProfileRecord>
): AnthropicProfileResolution {
    val inspections = records.associate { record -> record.id to registry.inspect(record) }
    val seenAccounts = linkedSetOf<UsageAccountKey>()
    val duplicateIds = linkedSetOf<String>()
    val enabledProfiles = mutableListOf<AnthropicProfileRef>()

    records.filter { it.enabled }.forEach { record ->
        val accountKey = inspections[record.id]?.accountContext?.key
        if (accountKey != null && !seenAccounts.add(accountKey)) {
            duplicateIds += record.id
        } else {
            enabledProfiles += record.ref
        }
    }

    return AnthropicProfileResolution(enabledProfiles, inspections, duplicateIds)
}

private fun buildAnthropicProfileUiModels(
    records: List<AnthropicProfileRecord>,
    inspections: Map<String, AnthropicProfileInspection>,
    duplicateProfileIds: Set<String>
): List<AnthropicProfileUiModel> {
    return records.map { record ->
        val inspection = inspections[record.id]
        val duplicate = record.id in duplicateProfileIds
        val status = when {
            duplicate -> AnthropicProfileUiStatus.DUPLICATE
            inspection?.status == AnthropicProfileInspectionStatus.READY -> AnthropicProfileUiStatus.READY
            inspection?.status == AnthropicProfileInspectionStatus.INVALID -> AnthropicProfileUiStatus.INVALID
            else -> AnthropicProfileUiStatus.INCOMPLETE
        }
        AnthropicProfileUiModel(
            id = record.id,
            label = record.label,
            path = record.configDirectory,
            enabled = record.enabled,
            removable = record.id != DEFAULT_ANTHROPIC_PROFILE_ID,
            identityLabel = inspection?.accountContext?.displayLabel,
            status = status,
            detail = if (duplicate) "Já monitorada por outro perfil habilitado" else inspection?.detail
        )
    }
}

/**
 * Aplica uma mudança nas configurações de time e persiste.
 *
 * O `StateFlow` é atualizado antes da gravação: a UI reflete a digitação na hora
 * e a escrita em disco vai atrás. Uma falha de gravação não pode derrubar o
 * diálogo de configurações — pior caso, a mudança não sobrevive ao reinício.
 */
/**
 * Aplica a alteração em memória e no disco.
 *
 * Devolve se a gravação passou: o aviso de "salvo" no diálogo não pode ser
 * emitido a partir da intenção, só do resultado — antes disto a falha era
 * engolida por um `runCatching` sem tratamento.
 */
private fun updateTeamSettings(
    settingsFlow: MutableStateFlow<TeamIntegrationSettings>,
    dataSource: LocalTeamSettingsDataSource,
    transform: (TeamIntegrationSettings) -> TeamIntegrationSettings
): Boolean {
    val updated = transform(settingsFlow.value)
    settingsFlow.value = updated
    return runCatching { dataSource.save(updated) }.isSuccess
}

/**
 * Contas Anthropic candidatas ao envio, com o `accountUuid` de cada perfil.
 *
 * A identidade é lida do disco a cada chamada (`inspect` lê `.credentials.json` e
 * `.claude.json`) em vez de ser cacheada: o usuário pode trocar de conta no
 * Claude Code com o app aberto, e um `accountUuid` velho mandaria o consumo dele
 * para o time errado. A chamada roda no laço de envio, em `Dispatchers.IO`.
 *
 * Perfis sem identidade resolvida ficam de fora — sem `accountUuid` não há chave
 * de agrupamento.
 */
private fun buildTeamSyncTargets(registry: AnthropicProfileRegistry): List<TeamSyncTarget> {
    return registry.profiles.value.mapNotNull { record ->
        val accountContext = registry.inspect(record).accountContext ?: return@mapNotNull null
        TeamSyncTarget(
            profileId = record.id,
            accountKey = accountContext.key.providerAccountId,
            organizationUuid = accountContext.key.workspaceId,
            organizationName = accountContext.workspaceName
        )
    }
}

/**
 * Contas que o semáforo do botão de time deve consultar.
 *
 * Mesma resolução do envio ([buildTeamSyncTargets]) filtrada pela condição que já
 * decide se o botão aparece no card: integração ligada e perfil marcado. Sem o
 * filtro, o app consultaria o servidor por contas cujo botão nem existe.
 */
private fun buildSessionPulseTargets(
    registry: AnthropicProfileRegistry,
    settings: TeamIntegrationSettings
): List<TeamPulseTarget> {
    if (!settings.isActive) {
        return emptyList()
    }

    return buildTeamSyncTargets(registry)
        .filter { target -> target.profileId in settings.participatingProfileIds }
        .map { target -> TeamPulseTarget(profileId = target.profileId, accountKey = target.accountKey) }
}

/**
 * Reset da quota de 5h da conta aberta na tela de Sessões CLI.
 *
 * Devolve janelas vazias enquanto a conta não tiver coleta bem-sucedida — o
 * filtro então cai para a janela corrida em vez de esvaziar a lista.
 */
private fun quotaWindowsForProfile(state: UiState, profileId: String?): CliQuotaWindows {
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
private fun accountCreditsForProfile(state: UiState, profileId: String?): AccountCreditUsage? {
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

private fun ApiUsageStats.quotaEndAt(periodType: PeriodType): Instant? {
    return quotas
        .firstOrNull { quota -> quota.periodType == periodType && quota.hasKnownResetAt }
        ?.periodEndAt
}

private fun availableUsageTargets(records: List<AnthropicProfileRecord>): List<UsageTargetKey> {
    val targets = mutableListOf<UsageTargetKey>()
    ApiSource.entries.forEach { source ->
        if (source == ApiSource.ANTHROPIC) {
            records.forEach { record -> targets += UsageTargetKey(source, record.id) }
        } else {
            targets += UsageTargetKey.forSource(source)
        }
    }
    return targets
}

private fun enabledUsageTargets(
    enabledSources: Set<ApiSource>,
    enabledProfiles: List<AnthropicProfileRef>
): Set<UsageTargetKey> {
    val targets = linkedSetOf<UsageTargetKey>()
    enabledSources.sortedBy { it.ordinal }.forEach { source ->
        if (source == ApiSource.ANTHROPIC) {
            enabledProfiles.forEach { profile -> targets += UsageTargetKey(source, profile.id) }
        } else {
            targets += UsageTargetKey.forSource(source)
        }
    }
    return targets
}

private fun readUsageTargetCollection(
    settings: PreferencesSettings,
    key: String
): List<UsageTargetKey> {
    return settings.getStringOrNull(key)
        ?.split(",")
        ?.filter { token -> token.isNotBlank() }
        ?.mapNotNull(UsageTargetKey::fromStorageKey)
        ?: emptyList()
}

private fun writeUsageTargetCollection(
    settings: PreferencesSettings,
    key: String,
    targets: Collection<UsageTargetKey>
) {
    settings.putString(key, targets.joinToString(",") { target -> target.storageKey })
}

private fun chooseAnthropicConfigDirectory(): File? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Selecionar diretório de configuração Anthropic"
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun historyWindowTitle(source: ApiSource, language: AppLanguage): String {
    val sourceName = source.displayName(language)

    return if (language == AppLanguage.PT) {
        "Histórico - $sourceName"
    } else {
        "History - $sourceName"
    }
}
