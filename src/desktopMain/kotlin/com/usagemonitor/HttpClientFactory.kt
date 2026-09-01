package com.usagemonitor

import com.usagemonitor.domain.entity.ProxyEnvironmentConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Credentials

/**
 * Monta o `HttpClient` compartilhado do app, com proxy HTTP opcional
 * (issue #174).
 *
 * Extraído de `Main.kt` para ser testável sem `@Composable`: a montagem do
 * proxy (host/porta/autenticador) é a parte nova e arriscada, e mora aqui
 * isolada da criação do client em si.
 */
fun buildHttpClient(effectiveProxy: ProxyEnvironmentConfig?): HttpClient {
    return HttpClient(OkHttp) {
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
        engine {
            if (effectiveProxy != null) {
                proxy = ProxyBuilder.http(Url("${effectiveProxy.scheme}://${effectiveProxy.host}:${effectiveProxy.port}"))
                val username = effectiveProxy.username
                if (username != null) {
                    config {
                        proxyAuthenticator(buildProxyAuthenticator(username, effectiveProxy.password.orEmpty()))
                    }
                }
            }
        }
    }
}

/**
 * Autenticador Basic para proxy — responde ao desafio 407 uma única vez.
 *
 * Guard contra loop: se a requisição que gerou o 407 **já** carrega
 * `Proxy-Authorization`, é porque a credencial enviada foi recusada de novo —
 * devolver `null` aqui deixa o OkHttp desistir e a resposta 407 subir como
 * está, em vez de reenviar a mesma credencial errada indefinidamente.
 */
internal fun buildProxyAuthenticator(username: String, password: String): Authenticator {
    return Authenticator { _, response ->
        if (response.request.header("Proxy-Authorization") != null) {
            return@Authenticator null
        }
        response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(username, password))
            .build()
    }
}
