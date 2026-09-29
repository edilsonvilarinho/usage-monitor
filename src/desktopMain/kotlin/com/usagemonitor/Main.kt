package com.usagemonitor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.application
import com.usagemonitor.data.diagnostics.LocalBreadcrumbRecorder
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.usecase.GetReleaseNotesUseCase
import com.usagemonitor.presentation.ui.ModalWindowEnvironment
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.update.ensureLinuxMenuIconCurrent
import com.usagemonitor.update.rememberAutoUpdateController
import com.usagemonitor.update.rememberReleaseNotesController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.imageio.ImageIO
import kotlin.system.exitProcess

// internal (não private): o reparo do ícone de menu (issue #133,
// `com.usagemonitor.update.ensureLinuxMenuIconCurrent`) reusa o mesmo
// recurso do ícone da janela -- um segundo literal para o mesmo caminho
// seria um segundo dono da mesma resposta.
internal const val APP_ICON_RESOURCE_PATH = "/icons/app_icon.png"

// O ícone muda de desenho com o tamanho (`tools/brand/render_icons.py`): o de
// 512 px traz o nome escrito, que reduzido a 16 px viraria borrão. Janela e
// bandeja leem cada um a faixa que é desenhada para o tamanho em que aparecem.
private const val WINDOW_ICON_RESOURCE_PATH = "/icons/app_icon_window.png"
private const val TRAY_ICON_RESOURCE_PATH = "/icons/app_icon_tray.png"

internal const val CODEX_CLI_SESSION_INDEX_INTERVAL_MILLIS = 10 * 60 * 1_000L

/** Ícone das janelas: as iniciais U·M, legíveis em 32–64 px. */
internal fun loadWindowIcon() = loadIconResource(WINDOW_ICON_RESOURCE_PATH)

/** Base do ícone da bandeja: só o núcleo com o disco, que é o que se lê em 16 px. */
internal fun loadTrayIcon() = loadIconResource(TRAY_ICON_RESOURCE_PATH)

private fun loadIconResource(path: String) = runCatching {
    val stream = object {}.javaClass.getResourceAsStream(path) ?: return@runCatching null
    stream.use { resourceStream ->
        ImageIO.read(resourceStream).toPainter()
    }
}.getOrNull()

@OptIn(kotlinx.coroutines.FlowPreview::class)
/**
 * Ponto de entrada.
 *
 * Corpo de bloco, e nao expressao: a trilha de eventos precisa nascer **antes**
 * da janela, porque quem a consome primeiro e o handler de excecao nao tratada,
 * que roda fora de qualquer composicao. A composicao fica em [runUsageMonitor],
 * que so compoe os hosts.
 */
fun main(args: Array<String>) {
    val breadcrumbs = LocalBreadcrumbRecorder()

    // Registrado ANTES da janela: uma exceção que derrube uma thread durante a
    // construção dos recursos -- que é onde o app some sem deixar nada -- só tem
    // handler se ele já estiver de pé aqui.
    CrashHandler(
        breadcrumbs = breadcrumbs,
        screenshots = RobotWindowScreenshotCapturer { appMainWindow }
    ).install()

    runUsageMonitor(args, breadcrumbs)
}

/**
 * A janela principal vista de **fora** da composição.
 *
 * `AppGraph.mainWindow` só existe depois de o grafo ser montado, e o handler de
 * crash é instalado antes disso e roda possivelmente com a composição já morta.
 * `@Volatile` porque quem escreve é a thread da UI e quem lê é a thread que caiu.
 */
@Volatile
internal var appMainWindow: java.awt.Window? = null

/**
 * Passo de navegacao: uma tela ou modal que o usuario abriu.
 *
 * Funcao de topo e nao literal repetido em cada lambda: o texto do passo e o que
 * o leitor do relatorio vai reconhecer, e oito formas de escrever a mesma frase
 * dariam oito trilhas diferentes para o mesmo app.
 */
internal fun BreadcrumbRecorder.recordScreenOpened(screen: String) {
    record(BreadcrumbCategory.NAVIGATION, "abriu $screen")
}

internal fun runUsageMonitor(
    args: Array<String>,
    breadcrumbs: BreadcrumbRecorder
) = application {
    val startup = remember { AppStartup(args) }
    val singleInstanceGuard = remember { SingleInstanceGuard.tryAcquire() }
    if (singleInstanceGuard == null) {
        // Sair calado aqui é o que faz clicar no atalho com o app já rodando não
        // produzir nada -- indistinguível de "o app não abre". O pedido de foco
        // fica no disco e a instância viva o atende.
        startup.focusRequests.request()
        startup.record(StartupOutcome.SECOND_INSTANCE_EXIT)
        exitApplication()
        return@application
    }
    LaunchedEffect(startup) {
        startup.record(StartupOutcome.STARTED)
    }

    // O grafo e os view models vivem fora da composição (`AppGraph`,
    // `AppViewModels`): são criados uma vez por processo, depois da checagem de
    // instância única.
    val graph = remember { AppGraph(breadcrumbs) }
    val settings = graph.settings
    // Uma chamada, e todo o estado da atualização automática mora fora daqui.
    val autoUpdate = rememberAutoUpdateController(settings = settings, httpClient = graph.httpClient)
    // Idem: decide, busca e lembra as novidades da versão recém-instalada.
    val releaseNotes = rememberReleaseNotesController(
        settings = settings,
        getReleaseNotes = GetReleaseNotesUseCase(graph.appUpdateRepository),
        receipt = autoUpdate.lastReceipt,
        breadcrumbs = breadcrumbs
    )
    val viewModels = remember { AppViewModels(graph, autoUpdate) }
    AppBackgroundWork(graph, viewModels, singleInstanceGuard)

    val profileRecords by graph.profileRegistry.profiles.collectAsState()
    val profileResolution = resolveAnthropicProfiles(graph.profileRegistry, profileRecords)
    graph.enabledAnthropicProfiles.value = profileResolution.enabledProfiles
    // Contas Codex extras (issue #329): o flow do view model segue o registro.
    val codexRecords by graph.codexProfileRegistry.profiles.collectAsState()
    val enabledCodexProfiles = codexRecords.filter { it.enabled }.map { it.ref }
    graph.enabledCodexProfiles.value = enabledCodexProfiles
    val availableTargets = remember(profileRecords, codexRecords) { availableUsageTargets(profileRecords, codexRecords) }
    val profileUiModels = buildAnthropicProfileUiModels(
        records = profileRecords,
        inspections = profileResolution.inspections,
        duplicateProfileIds = profileResolution.duplicateProfileIds
    )
    val teamSettings by graph.teamSettingsFlow.collectAsState()
    val dashboardState by viewModels.dashboard.uiState.collectAsState()

    val shell = remember {
        AppShellState(settings, initialTargets = availableTargets)
    }
    // A queda da sessão anterior é lida **e consumida** uma vez por arranque: este
    // é o mesmo arranque que abre o relatório, então ela foi oferecida.
    val pendingCrash = remember { consumePendingCrash() }
    val modal = remember { AppModalState(bugReportOpenAtStart = pendingCrash != null) }
    val compositionScope = rememberCoroutineScope()
    val feedback = remember {
        AppSettingsFeedback(
            graph = graph,
            viewModels = viewModels,
            scope = compositionScope,
            language = { shell.language },
            profileLabel = { profileId ->
                graph.profileRegistry.profiles.value.firstOrNull { record -> record.id == profileId }?.label
            }
        )
    }
    val settingsActions = remember { SettingsActions(graph, viewModels, shell, modal, feedback, compositionScope) }

    val iconImage = remember { loadWindowIcon() }
    val trayIconImage = remember { loadTrayIcon() }
    // A área útil é lida uma vez e vale para todas as janelas.
    val screenWorkArea = remember { availableWindowAreaDp() }
    val windows = rememberAppWindowStates(settings, shell.uiScalePercent, screenWorkArea)
    PersistAppWindowStates(windows, settings)
    AppPreferenceEffects(shell, settings, availableTargets, feedback, breadcrumbs)
    OpenSettingsWithoutApis(dashboardState, modal)
    SessionWindowBindings(viewModels, modal, dashboardState, shell.monthlyBudgetMicros)

    val shutdownApplication = remember(viewModels, singleInstanceGuard) {
        {
            viewModels.shutdown(singleInstanceGuard)
            exitProcess(0)
        }
    }
    // "Reiniciar o app e atualizar" reusa a mesma saída ordenada do resto do app:
    // um segundo caminho de encerramento seria um segundo lugar para esquecer de
    // fechar o banco.
    autoUpdate.bindRestart(shutdownApplication)

    // A barra HUD é a única janela de visualização: "Abrir" (bandeja, segunda
    // instância) traz a janela dela para a frente.
    val focusHud = {
        graph.mainWindow?.let { window -> activateWindow(window) }
        Unit
    }
    FocusRequestService(startup, focusHud)

    if (isTraySupported) {
        AppTrayHost(viewModels, shell, modal, trayIconImage, breadcrumbs, focusHud, shutdownApplication)
    }

    // As ações do rodapé moram no balão da engrenagem da barra HUD.
    val shellActions = buildShellActions(
        graph, viewModels, shell, modal, profileRecords, profileResolution, dashboardState, teamSettings
    )
    val cliSessionPulses by viewModels.sessionPulse.cliPulses.collectAsState()
    val teamSessionPulses by viewModels.sessionPulse.teamPulses.collectAsState()
    val accountColors = remember(profileRecords) { accountColorsOf(profileRecords) }
    val accountEmojis = remember(profileRecords) { accountEmojisOf(profileRecords) }

    // O que as janelas modais recebem igual, montado uma vez: esquecer a escala
    // ou o movimento numa delas renderizaria errado sem erro nenhum.
    val modalEnvironment = ModalWindowEnvironment(
        iconImage = iconImage,
        themePreset = shell.themePreset,
        uiScalePercent = shell.uiScalePercent,
        motion = shell.motion,
        screenWorkArea = screenWorkArea,
        breadcrumbs = breadcrumbs
    )
    ModalWindowsHost(graph, viewModels, modal, windows, modalEnvironment, shell.language, teamSettings, releaseNotes)
    BugReportWindow(graph, shell, modal, pendingCrash, windows, modalEnvironment)

    // A barra HUD numa janela própria (`HudWindow.kt`): a única de visualização.
    HudWindowHost(
            settings = settings,
            viewModel = viewModels.dashboard,
            usageAlertViewModel = viewModels.usageAlert,
            cardOrder = shell.cardOrder,
            language = shell.language,
            themePreset = shell.themePreset,
            uiScalePercent = shell.uiScalePercent,
            motion = shell.motion,
            windowOpacityPercent = shell.windowOpacityPercent,
            iconImage = iconImage,
            hudScreenArea = screenWorkArea,
            onWindowReady = { window -> anchorAppWindow(window, graph, startup) },
            actions = shellActions,
            // O que o card de cada conta oferece, para os botões do balão.
            teamEnabledProfileIds = if (teamSettings.isActive) teamSettings.participatingProfileIds else emptySet(),
            cliSessionPulses = cliSessionPulses,
            teamSessionPulses = teamSessionPulses,
            accountColors = accountColors,
            accountEmojis = accountEmojis,
            onCloseRequest = { shutdownApplication() },
            activeTargets = viewModels.sessionPulse.activeTargets,
            stalledSessions = viewModels.sessionPulse.stalledSessions
        )

    SettingsWindowHost(
        graph = graph,
        viewModels = viewModels,
        shell = shell,
        modal = modal,
        feedback = feedback,
        actions = settingsActions,
        autoUpdate = autoUpdate,
        profileUiModels = profileUiModels,
        state = windows.settings,
        environment = modalEnvironment
    )
}

/**
 * O trabalho de fundo que não pertence a janela nenhuma: a indexação do Codex, o
 * envio do time e o encerramento ordenado — o mesmo (`AppViewModels.shutdown`)
 * no gancho da JVM e na saída da composição.
 */
@Composable
private fun AppBackgroundWork(graph: AppGraph, viewModels: AppViewModels, singleInstanceGuard: SingleInstanceGuard) {
    // O índice local continua sendo atualizado com a janela fechada. A operação
    // já muda para Dispatchers.IO no datasource; o laço não bloqueia a UI.
    LaunchedEffect(graph) {
        while (isActive) {
            graph.codexCliSessionDataSource.syncIndex()
            delay(CODEX_CLI_SESSION_INDEX_INTERVAL_MILLIS)
        }
    }
    val teamSettings by graph.teamSettingsFlow.collectAsState()
    LaunchedEffect(viewModels.teamSync, teamSettings.isActive) {
        if (teamSettings.isActive) {
            viewModels.teamSync.start()
        } else {
            viewModels.teamSync.stop()
        }
    }
    DisposableEffect(viewModels, singleInstanceGuard) {
        val shutdownHook = Thread { viewModels.shutdown(singleInstanceGuard) }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        onDispose {
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
            viewModels.shutdown(singleInstanceGuard)
        }
    }
}

/**
 * Sem API habilitada o notch diz "Nenhuma API" e não tem o que mostrar — é o
 * estado da instalação nova. As Configurações abrem sozinhas uma vez por
 * arranque; fechá-las não as reabre, e a engrenagem continua levando até lá.
 */
@Composable
private fun OpenSettingsWithoutApis(dashboardState: UiState, modal: AppModalState) {
    val noApis = dashboardState is UiState.NoApisEnabled
    var offered by remember { mutableStateOf(false) }
    LaunchedEffect(noApis) {
        if (noApis && !offered) {
            offered = true
            modal.openSettings()
        }
    }
}

/**
 * Atende a segunda instância pelo mesmo caminho do item "Abrir" da bandeja, e
 * não um segundo. A leitura vai para a IO porque este efeito roda na thread da
 * interface.
 */
@Composable
private fun FocusRequestService(startup: AppStartup, focusHud: () -> Unit) {
    LaunchedEffect(startup) {
        while (isActive) {
            delay(FocusRequestChannel.POLL_INTERVAL_MILLIS)
            val focusRequested = withContext(Dispatchers.IO) { startup.focusRequests.consume() }
            if (focusRequested) {
                focusHud()
                withContext(Dispatchers.IO) { startup.record(StartupOutcome.FOCUS_REQUEST_SERVED) }
            }
        }
    }
}

