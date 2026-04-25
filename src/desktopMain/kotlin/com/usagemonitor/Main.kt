package com.usagemonitor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.data.datasource.LocalCredentialDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import kotlin.system.exitProcess

fun main() = application {

    val singleInstance = java.util.concurrent.Semaphore(1)
    if (!singleInstance.tryAcquire()) {
        exitApplication()
    }

    val httpClient = HttpClient(OkHttp) {
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

    val settings = PreferencesSettings(Preferences.userRoot().node("com.usagemonitor"))

    val persistedApis = settings.getStringOrNull("enabledApis")
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { runCatching { ApiSource.valueOf(it) }.getOrNull() }
        ?.toSet()
        ?.ifEmpty { setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX) }
        ?: setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX)

    val enabledApis = MutableStateFlow(persistedApis)

    val credentialDataSource = LocalCredentialDataSource(httpClient)
    val remoteApiDataSource = RemoteApiDataSource(httpClient)

    val anthropicRepository = AnthropicRepositoryImpl(credentialDataSource, remoteApiDataSource)
    val minimaxRepository = MiniMaxRepositoryImpl(remoteApiDataSource)

    val viewModel = DashboardViewModel(
        getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepository),
        getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepository),
        enabledApis = enabledApis
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        viewModel.onDestroy()
        httpClient.close()
    })

    val iconFile = File("src/desktopMain/resources/icons/app_icon.png")
    val iconImage = if (iconFile.exists()) {
        runCatching { ImageIO.read(iconFile).toPainter() }.getOrNull()
    } else {
        null
    }

    Window(
        onCloseRequest = {
            viewModel.onDestroy()
            httpClient.close()
            exitProcess(0)
        },
        title = "Usage Monitor",
        icon = iconImage
    ) {
        DashboardScreen(
            viewModel = viewModel,
            settings = settings,
            enabledApis = enabledApis,
            onAutoStartChange = { enabled ->
                AutoStartManager.setAutoStart(enabled)
            }
        )
    }
}