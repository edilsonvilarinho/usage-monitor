package com.usagemonitor

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
import java.util.prefs.Preferences

fun main() = application {

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

    Window(
        onCloseRequest = {
            viewModel.onDestroy()
            httpClient.close()
            exitApplication()
        },
        title = "Usage Monitor"
    ) {
        DashboardScreen(viewModel = viewModel, settings = settings, enabledApis = enabledApis)
    }
}
