package com.usagemonitor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.data.datasource.LocalCredentialDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
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

    val credentialDataSource = LocalCredentialDataSource()
    val remoteApiDataSource = RemoteApiDataSource(httpClient)

    val anthropicRepository = AnthropicRepositoryImpl(credentialDataSource, remoteApiDataSource)
    val minimaxRepository = MiniMaxRepositoryImpl(remoteApiDataSource)

    val viewModel = DashboardViewModel(
        getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepository),
        getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepository)
    )

    Window(
        onCloseRequest = {
            viewModel.onDestroy()
            httpClient.close()
            exitApplication()
        },
        title = "Usage Monitor"
    ) {
        DashboardScreen(viewModel = viewModel, settings = settings)
    }
}
