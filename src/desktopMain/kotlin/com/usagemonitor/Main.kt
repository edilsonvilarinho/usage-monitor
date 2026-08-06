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
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
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
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiStatus
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
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
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

    val preferencesNode = remember { Preferences.userRoot().node("com.usagemonitor") }
    val settings = remember(preferencesNode) { PreferencesSettings(preferencesNode) }

    val persistedApis = remember(settings) {
        readApiSourceCollection(settings, ENABLED_APIS_KEY)
            .toSet()
            .ifEmpty { DEFAULT_ENABLED_APIS }
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

    val credentialDataSource = remember(httpClient, profileRegistry) {
        LocalCredentialDataSource(
            httpClient = httpClient,
            profileLocationProvider = { profile -> profileRegistry.locationFor(profile.id) }
        )
    }
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
    val viewModel = remember(anthropicRepository, minimaxRepository, codexRepository, deepSeekRepository, openCodeRepository, kiloRepository, enabledApis, enabledAnthropicProfiles, recordUsageSnapshot, isAppVisible) {
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
            isAppVisible = isAppVisible,
            anthropicProfiles = enabledAnthropicProfiles
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
            state = rememberDialogState(width = 620.dp, height = 700.dp),
            resizable = true,
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
                        },
                        anthropicProfiles = profileUiModels,
                        onAnthropicProfileToggle = { profileId, checked ->
                            profileRegistry.setEnabled(profileId, checked)
                            enabledAnthropicProfiles.value = resolveAnthropicProfiles(
                                profileRegistry,
                                profileRegistry.profiles.value
                            ).enabledProfiles
                            viewModel.refresh(ApiSource.ANTHROPIC)
                        },
                        onAnthropicProfileRename = { profileId, label ->
                            profileRegistry.updateLabel(profileId, label)
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
