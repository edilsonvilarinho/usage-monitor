package com.usagemonitor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.usagemonitor.data.datasource.LocalCredentialDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Entry point da aplicação Desktop.
 *
 * `application { }` abre o loop de eventos do Compose Desktop.
 * `Window { }` cria a janela nativa do sistema operacional.
 *
 * Este ficheiro é o único lugar onde fazemos "Dependency Injection" manual:
 * criamos as implementações concretas e as conectamos.
 *
 * Em projetos maiores, um framework como Koin ou Kodein faria isso.
 * Aqui mantemos simples e explícito para fins didáticos.
 */
fun main() = application {

    // ── Configuração do cliente HTTP ──────────────────────────────────────
    // OkHttp é a engine HTTP para JVM. A configuração fica aqui (desktopMain)
    // porque OkHttp é específico da plataforma — não é KMP.
    val httpClient = HttpClient(OkHttp) {
        // Plugin de serialização: converte automaticamente JSON ↔ data classes
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        // Log das chamadas HTTP (apenas em desenvolvimento)
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    // ── Injeção de Dependências (manual) ─────────────────────────────────
    val credentialDataSource = LocalCredentialDataSource()
    val remoteApiDataSource = RemoteApiDataSource(httpClient)

    val anthropicRepository = AnthropicRepositoryImpl(credentialDataSource, remoteApiDataSource)
    val minimaxRepository = MiniMaxRepositoryImpl(remoteApiDataSource)

    val viewModel = DashboardViewModel(
        getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepository),
        getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepository)
    )

    // ── Janela da aplicação ───────────────────────────────────────────────
    Window(
        onCloseRequest = {
            // Cancela todas as coroutines antes de fechar a janela
            viewModel.onDestroy()
            httpClient.close()
            exitApplication()
        },
        title = "Usage Monitor"
    ) {
        // Tema padrão: escuro
        AppTheme(isDark = true) {
            DashboardScreen(viewModel = viewModel)
        }
    }
}
