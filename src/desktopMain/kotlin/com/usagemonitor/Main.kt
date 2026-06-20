package com.usagemonitor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.data.datasource.LocalCredentialDataSource
import com.usagemonitor.data.datasource.LocalCodexAuthDataSource
import com.usagemonitor.data.datasource.LocalCodexDiagnosticsRecorder
import com.usagemonitor.data.datasource.LocalKiloUsageDataSource
import com.usagemonitor.data.datasource.LocalOpenCodeUsageDataSource
import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import com.usagemonitor.data.repository.AppUpdateRepositoryImpl
import com.usagemonitor.data.repository.CodexRepositoryImpl
import com.usagemonitor.data.repository.DeepSeekRepositoryImpl
import com.usagemonitor.data.repository.KiloRepositoryImpl
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
import com.usagemonitor.data.repository.OpenCodeRepositoryImpl
import com.usagemonitor.data.repository.UsageHistoryRepositoryImpl
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme as ThemeMode
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.presentation.ui.DesktopDialogFrame
import com.usagemonitor.presentation.ui.DesktopWindowFrame
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.moveVisibleCardToIndex
import com.usagemonitor.presentation.ui.normalizeCardOrder
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import com.usagemonitor.update.DesktopAppUpdateReleaseOpener
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

private val DEFAULT_ENABLED_APIS = emptySet<ApiSource>()
private const val APP_ICON_RESOURCE_PATH = "/icons/app_icon.png"
private const val ENABLED_APIS_KEY = "enabledApis"
private const val IS_DARK_KEY = "isDark"
private const val LANGUAGE_KEY = "language"
private const val AUTO_START_KEY = "autoStart"
private const val CARD_ORDER_KEY = "cardOrder"
private const val MINIMIZED_CARDS_KEY = "minimizedCards"

private fun loadWindowIcon() = runCatching {
    val stream = object {}.javaClass.getResourceAsStream(APP_ICON_RESOURCE_PATH) ?: return@runCatching null
    stream.use { resourceStream ->
        ImageIO.read(resourceStream).toPainter()
    }
}.getOrNull()

@OptIn(kotlinx.coroutines.FlowPreview::class)
fun main() = application {

    val singleInstanceGuard = remember { SingleInstanceGuard.tryAcquire() }
    if (singleInstanceGuard == null) {
        exitApplication()
        return@application
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

    val settings = remember {
        PreferencesSettings(Preferences.userRoot().node("com.usagemonitor"))
    }

    val persistedApis = remember(settings) {
        readApiSourceCollection(settings, ENABLED_APIS_KEY)
            .toSet()
            .ifEmpty { DEFAULT_ENABLED_APIS }
    }
    val persistedCardOrder = remember(settings) {
        normalizeCardOrder(readApiSourceCollection(settings, CARD_ORDER_KEY))
    }
    val persistedMinimizedCards = remember(settings) {
        val storedValue = settings.getStringOrNull(MINIMIZED_CARDS_KEY)
        if (storedValue == null) {
            ApiSource.entries.toSet()
        } else {
            readApiSourceCollection(settings, MINIMIZED_CARDS_KEY).toSet()
        }
    }

    val initialAutoStartEnabled = remember(settings) {
        val storedAutoStartPreference = settings.getBoolean(AUTO_START_KEY, false)
        val resolvedAutoStartEnabled = if (AutoStartManager.isAutoStartSupported()) {
            AutoStartManager.isAutoStartEnabled()
        } else {
            storedAutoStartPreference
        }

        if (storedAutoStartPreference != resolvedAutoStartEnabled) {
            settings.putBoolean(AUTO_START_KEY, resolvedAutoStartEnabled)
        }

        resolvedAutoStartEnabled
    }

    val enabledApis = remember { MutableStateFlow(persistedApis) }
    var cardOrder by remember { mutableStateOf(persistedCardOrder) }
    var minimizedCards by remember { mutableStateOf(persistedMinimizedCards) }
    val persistedMainWindowState = remember(settings) {
        readPersistedMainWindowState(settings)
    }
    val persistedHistoryWindowState = remember(settings) {
        readPersistedHistoryWindowState(settings)
    }

    val credentialDataSource = remember(httpClient) { LocalCredentialDataSource(httpClient) }
    val codexAuthDataSource = remember { LocalCodexAuthDataSource() }
    val codexDiagnosticsRecorder = remember { LocalCodexDiagnosticsRecorder() }
    val remoteApiDataSource = remember(httpClient, codexDiagnosticsRecorder) {
        RemoteApiDataSource(httpClient, codexDiagnosticsRecorder)
    }
    val usageHistoryDataSource = remember { LocalUsageHistoryDataSource() }
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
    val appUpdateRepository = remember(remoteApiDataSource) {
        AppUpdateRepositoryImpl(remoteApiDataSource)
    }
    val appUpdateReleaseOpener = remember { DesktopAppUpdateReleaseOpener() }

    val recordUsageSnapshot = remember(usageHistoryRepository) {
        RecordUsageSnapshotUseCase(usageHistoryRepository)
    }
    val getUsageHistory = remember(usageHistoryRepository) {
        GetUsageHistoryUseCase(usageHistoryRepository)
    }

    val isAppVisible = remember { MutableStateFlow(true) }
    val viewModel = remember(anthropicRepository, minimaxRepository, codexRepository, deepSeekRepository, openCodeRepository, kiloRepository, enabledApis, recordUsageSnapshot, isAppVisible) {
        DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepository),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepository),
            getCodexUsage = GetCodexUsageUseCase(codexRepository),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepository),
            getKiloUsage = GetKiloUsageUseCase(kiloRepository),
            getOpenCodeUsage = GetOpenCodeUsageUseCase(openCodeRepository),
            enabledApis = enabledApis,
            recordUsageSnapshot = recordUsageSnapshot,
            checkForAppUpdate = CheckForAppUpdateUseCase(appUpdateRepository),
            appUpdateReleaseOpener = appUpdateReleaseOpener,
            currentAppVersion = CURRENT_APP_VERSION,
            isAppVisible = isAppVisible
        )
    }
    val historyViewModel = remember(getUsageHistory, enabledApis) {
        HistoryViewModel(
            getUsageHistory = getUsageHistory,
            enabledApis = enabledApis
        )
    }

    val shutdownStarted = remember { AtomicBoolean(false) }
    DisposableEffect(viewModel, historyViewModel, httpClient, singleInstanceGuard, usageHistoryDataSource, openCodeUsageDataSource, kiloUsageDataSource) {
        val shutdownHook = Thread {
            if (shutdownStarted.compareAndSet(false, true)) {
                viewModel.onDestroy()
                historyViewModel.onDestroy()
                httpClient.close()
                usageHistoryDataSource.close()
                openCodeUsageDataSource.close()
                kiloUsageDataSource.close()
                singleInstanceGuard.close()
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
                httpClient.close()
                usageHistoryDataSource.close()
                openCodeUsageDataSource.close()
                kiloUsageDataSource.close()
                singleInstanceGuard.close()
            }
        }
    }

    val iconImage = remember { loadWindowIcon() }
    val mainWindowState = rememberPersistedMainWindowState(persistedMainWindowState)
    val historyWindowState = rememberPersistedHistoryWindowState(persistedHistoryWindowState)
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
    val enabledApisState by enabledApis.collectAsState()
    var isDark by remember { mutableStateOf(settings.getBoolean(IS_DARK_KEY, true)) }
    var language by remember {
        mutableStateOf(
            settings.getStringOrNull(LANGUAGE_KEY)
                ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.PT
        )
    }
    var autoStartEnabled by remember { mutableStateOf(initialAutoStartEnabled) }
    var isSettingsDialogOpen by remember { mutableStateOf(false) }
    var historyDialogSource by remember { mutableStateOf<ApiSource?>(null) }
    var historyOpenGeneration by remember { mutableStateOf(0) }
    val shutdownApplication = remember(viewModel, historyViewModel, httpClient, usageHistoryDataSource, openCodeUsageDataSource, kiloUsageDataSource) {
        {
            if (shutdownStarted.compareAndSet(false, true)) {
                viewModel.onDestroy()
                historyViewModel.onDestroy()
                httpClient.close()
                usageHistoryDataSource.close()
                openCodeUsageDataSource.close()
                kiloUsageDataSource.close()
                singleInstanceGuard.close()
            }
            exitProcess(0)
        }
    }

    Window(
        onCloseRequest = {
            shutdownApplication()
        },
        title = "Usage Monitor",
        icon = iconImage,
        state = mainWindowState,
        undecorated = true
    ) {
        AppTheme(isDark = isDark) {
            DesktopWindowFrame(
                title = "Usage Monitor",
                iconPainter = iconImage,
                windowState = mainWindowState,
                onCloseRequest = {
                    shutdownApplication()
                }
            ) {
                DashboardScreen(
                    viewModel = viewModel,
                    appVersion = CURRENT_APP_VERSION,
                    language = language,
                    enabledApis = enabledApis,
                    cardOrder = cardOrder,
                    minimizedCards = minimizedCards,
                    onMoveCardToIndex = { source, targetIndex ->
                        val updatedOrder = moveVisibleCardToIndex(
                            currentOrder = cardOrder,
                            visibleSources = enabledApisState,
                            source = source,
                            targetIndex = targetIndex
                        )
                        cardOrder = updatedOrder
                        writeApiSourceCollection(settings, CARD_ORDER_KEY, updatedOrder)
                    },
                    onToggleCardMinimized = { source ->
                        val updatedMinimizedCards = if (source in minimizedCards) {
                            minimizedCards - source
                        } else {
                            minimizedCards + source
                        }
                        minimizedCards = updatedMinimizedCards
                        writeApiSourceCollection(settings, MINIMIZED_CARDS_KEY, updatedMinimizedCards)
                    },
                    onOpenHistory = { source ->
                        historyDialogSource = source
                        historyOpenGeneration++
                        historyViewModel.openForSource(source)
                    },
                    onOpenSettings = { isSettingsDialogOpen = true }
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
                activateHistoryWindow(window)
            }
            AppTheme(isDark = isDark) {
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

    if (isSettingsDialogOpen) {
        DialogWindow(
            onCloseRequest = { isSettingsDialogOpen = false },
            title = if (language == AppLanguage.PT) "Configurações" else "Settings",
            icon = iconImage,
            state = rememberDialogState(width = 460.dp, height = 420.dp),
            resizable = false,
            undecorated = true
        ) {
            AppTheme(isDark = isDark) {
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
                        onThemeToggle = {
                            isDark = !isDark
                            settings.putBoolean(IS_DARK_KEY, isDark)
                        },
                        onLanguageChange = { selectedLanguage ->
                            language = selectedLanguage
                            settings.putString(LANGUAGE_KEY, selectedLanguage.name)
                        },
                        onAutoStartChange = { enabled ->
                            val updatedState = if (AutoStartManager.setAutoStart(enabled)) {
                                enabled
                            } else {
                                AutoStartManager.isAutoStartEnabled()
                            }
                            autoStartEnabled = updatedState
                            settings.putBoolean(AUTO_START_KEY, updatedState)
                        },
                        onApiToggle = { api, checked ->
                            val updatedApis = if (checked) {
                                enabledApis.value + api
                            } else {
                                enabledApis.value - api
                            }
                            enabledApis.value = updatedApis
                            writeApiSourceCollection(settings, ENABLED_APIS_KEY, updatedApis)
                            viewModel.refresh()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberPersistedMainWindowState(
    persistedState: PersistedMainWindowState
) = when {
    persistedState.widthDp != null && persistedState.heightDp != null -> {
        rememberWindowState(
            placement = persistedState.composePlacement,
            size = DpSize(
                width = persistedState.composeWidth,
                height = persistedState.composeHeight
            )
        )
    }

    persistedState.placement == PersistedWindowPlacement.MAXIMIZED -> {
        rememberWindowState(
            placement = persistedState.composePlacement
        )
    }

    else -> rememberWindowState()
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

private fun historyWindowTitle(source: ApiSource, language: AppLanguage): String {
    val sourceName = source.displayName(language)

    return if (language == AppLanguage.PT) {
        "Histórico - $sourceName"
    } else {
        "History - $sourceName"
    }
}
