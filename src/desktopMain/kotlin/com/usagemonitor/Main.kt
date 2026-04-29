package com.usagemonitor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import com.usagemonitor.data.repository.CodexRepositoryImpl
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
import com.usagemonitor.data.repository.UsageHistoryRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme as ThemeMode
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.presentation.ui.DesktopDialogFrame
import com.usagemonitor.presentation.ui.DesktopWindowFrame
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private val DEFAULT_ENABLED_APIS = emptySet<ApiSource>()
private const val APP_ICON_RESOURCE_PATH = "/icons/app_icon.png"

private enum class AppScreen {
    DASHBOARD,
    HISTORY
}

private fun loadWindowIcon() = runCatching {
    val stream = object {}.javaClass.getResourceAsStream(APP_ICON_RESOURCE_PATH) ?: return@runCatching null
    stream.use { resourceStream ->
        ImageIO.read(resourceStream).toPainter()
    }
}.getOrNull()

fun main() = application {

    val singleInstance = java.util.concurrent.Semaphore(1)
    if (!singleInstance.tryAcquire()) {
        exitApplication()
    }

    val httpClient = remember {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    val settings = remember {
        PreferencesSettings(Preferences.userRoot().node("com.usagemonitor"))
    }

    val persistedApis = remember(settings) {
        settings.getStringOrNull("enabledApis")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { runCatching { ApiSource.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: DEFAULT_ENABLED_APIS
    }

    val initialAutoStartEnabled = remember(settings) {
        val storedAutoStartPreference = settings.getBoolean("autoStart", false)
        val resolvedAutoStartEnabled = if (AutoStartManager.isAutoStartSupported()) {
            AutoStartManager.isAutoStartEnabled()
        } else {
            storedAutoStartPreference
        }

        if (storedAutoStartPreference != resolvedAutoStartEnabled) {
            settings.putBoolean("autoStart", resolvedAutoStartEnabled)
        }

        resolvedAutoStartEnabled
    }

    val enabledApis = remember { MutableStateFlow(persistedApis) }

    val credentialDataSource = remember(httpClient) { LocalCredentialDataSource(httpClient) }
    val codexAuthDataSource = remember { LocalCodexAuthDataSource() }
    val remoteApiDataSource = remember(httpClient) { RemoteApiDataSource(httpClient) }
    val usageHistoryDataSource = remember { LocalUsageHistoryDataSource() }

    val anthropicRepository = remember(credentialDataSource, remoteApiDataSource) {
        AnthropicRepositoryImpl(credentialDataSource, remoteApiDataSource)
    }
    val minimaxRepository = remember(remoteApiDataSource) {
        MiniMaxRepositoryImpl(remoteApiDataSource)
    }
    val codexRepository = remember(codexAuthDataSource, remoteApiDataSource) {
        CodexRepositoryImpl(codexAuthDataSource, remoteApiDataSource)
    }
    val usageHistoryRepository = remember(usageHistoryDataSource) {
        UsageHistoryRepositoryImpl(usageHistoryDataSource)
    }

    val recordUsageSnapshot = remember(usageHistoryRepository) {
        RecordUsageSnapshotUseCase(usageHistoryRepository)
    }
    val getUsageHistory = remember(usageHistoryRepository) {
        GetUsageHistoryUseCase(usageHistoryRepository)
    }

    val viewModel = remember(anthropicRepository, minimaxRepository, codexRepository, enabledApis, recordUsageSnapshot) {
        DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepository),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepository),
            getCodexUsage = GetCodexUsageUseCase(codexRepository),
            enabledApis = enabledApis,
            recordUsageSnapshot = recordUsageSnapshot
        )
    }
    val historyViewModel = remember(getUsageHistory, enabledApis) {
        HistoryViewModel(
            getUsageHistory = getUsageHistory,
            enabledApis = enabledApis
        )
    }

    DisposableEffect(viewModel, historyViewModel, httpClient) {
        val shutdownHook = Thread {
            viewModel.onDestroy()
            historyViewModel.onDestroy()
            httpClient.close()
        }

        Runtime.getRuntime().addShutdownHook(shutdownHook)

        onDispose {
            runCatching {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            }
        }
    }

    val iconImage = remember { loadWindowIcon() }
    val mainWindowState = rememberWindowState()
    val enabledApisState by enabledApis.collectAsState()
    var isDark by remember { mutableStateOf(settings.getBoolean("isDark", true)) }
    var language by remember {
        mutableStateOf(
            settings.getStringOrNull("language")
                ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.PT
        )
    }
    var autoStartEnabled by remember { mutableStateOf(initialAutoStartEnabled) }
    var isSettingsDialogOpen by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }

    Window(
        onCloseRequest = {
            viewModel.onDestroy()
            historyViewModel.onDestroy()
            httpClient.close()
            exitProcess(0)
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
                    viewModel.onDestroy()
                    historyViewModel.onDestroy()
                    httpClient.close()
                    exitProcess(0)
                }
            ) {
                when (currentScreen) {
                    AppScreen.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        appVersion = CURRENT_APP_VERSION,
                        language = language,
                        enabledApis = enabledApis,
                        onOpenHistory = {
                            currentScreen = AppScreen.HISTORY
                            historyViewModel.refresh()
                        },
                        onOpenSettings = { isSettingsDialogOpen = true }
                    )

                    AppScreen.HISTORY -> HistoryScreen(
                        viewModel = historyViewModel,
                        language = language,
                        onBack = { currentScreen = AppScreen.DASHBOARD }
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
                            settings.putBoolean("isDark", isDark)
                        },
                        onLanguageChange = { selectedLanguage ->
                            language = selectedLanguage
                            settings.putString("language", selectedLanguage.name)
                        },
                        onAutoStartChange = { enabled ->
                            autoStartEnabled = enabled
                            settings.putBoolean("autoStart", enabled)
                            AutoStartManager.setAutoStart(enabled)
                        },
                        onApiToggle = { api, checked ->
                            val updatedApis = if (checked) {
                                enabledApis.value + api
                            } else {
                                enabledApis.value - api
                            }
                            enabledApis.value = updatedApis
                            settings.putString("enabledApis", updatedApis.joinToString(",") { it.name })
                        },
                        onClose = { isSettingsDialogOpen = false }
                    )
                }
            }
        }
    }
}
