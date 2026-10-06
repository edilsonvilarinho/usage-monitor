package com.usagemonitor

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.usagemonitor.domain.entity.WebAccessSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Estado do servidor, para a seção de Rede das Configurações. */
internal sealed interface WebAccessStatus {
    data object Stopped : WebAccessStatus
    data class Running(val port: Int, val addresses: List<String>) : WebAccessStatus
    data class Failed(val message: String) : WebAccessStatus
}

/**
 * Servidor HTTP local da HUD (#388): a página da grade de anéis e o JSON do
 * retrato, para o celular ou outro computador da mesma rede.
 *
 * **Escolha do usuário: rede local**, então escuta em todas as interfaces — e
 * por isso o token é obrigatório em toda rota, comparado em tempo constante. O
 * Windows pergunta no firewall ao ativar (medido no `FocusRequestChannel`, que
 * evitou socket por esse motivo); o serviço nasce desligado e só sobe quando o
 * usuário liga a opção.
 *
 * Só leitura: `GET`/`HEAD`, nenhuma rota escreve. O conteúdo é o mesmo retrato
 * que a HUD mostra — nunca prompt, resposta, caminho de projeto ou credencial.
 * `com.sun.net.httpserver` (módulo `jdk.httpserver`, declarado no jlink) evita
 * dependência nova para três rotas.
 */
internal class LocalWebAccessService(
    private val snapshotJson: () -> String?,
    private val pageHtml: () -> String = ::loadWebPage,
    /** `null` = todas as interfaces (padrão do app); os testes usam loopback. */
    private val bindAddress: InetAddress? = null,
    private val addressesProvider: () -> List<String> = ::siteLocalIpv4Addresses
) {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    private var appliedSettings: WebAccessSettings? = null

    private val _status = MutableStateFlow<WebAccessStatus>(WebAccessStatus.Stopped)
    val status: StateFlow<WebAccessStatus> = _status.asStateFlow()

    /** Sobe, derruba ou reinicia conforme [settings]. Idempotente para o mesmo valor. */
    @Synchronized
    fun apply(settings: WebAccessSettings) {
        if (settings == appliedSettings) {
            return
        }
        stopServer()
        appliedSettings = settings
        if (!settings.enabled || settings.token.isBlank()) {
            _status.value = WebAccessStatus.Stopped
            return
        }
        _status.value = runCatching { startServer(settings) }.getOrElse { error ->
            stopServer()
            WebAccessStatus.Failed(error.message ?: error::class.simpleName.orEmpty())
        }
    }

    @Synchronized
    fun stop() {
        stopServer()
        appliedSettings = null
        _status.value = WebAccessStatus.Stopped
    }

    private fun startServer(settings: WebAccessSettings): WebAccessStatus {
        val address = bindAddress?.let { host -> InetSocketAddress(host, settings.port) } ?: InetSocketAddress(settings.port)
        val created = HttpServer.create(address, 0)
        val pool = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "web-access").apply { isDaemon = true } }
        val tokenBytes = settings.token.toByteArray(StandardCharsets.UTF_8)
        created.createContext("/") { exchange -> handle(exchange, tokenBytes) }
        created.executor = pool
        created.start()
        server = created
        executor = pool
        return WebAccessStatus.Running(created.address.port, addressesProvider())
    }

    private fun stopServer() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
    }

    private fun handle(exchange: HttpExchange, token: ByteArray) {
        exchange.use { call ->
            val method = call.requestMethod
            if (method != "GET" && method != "HEAD") {
                respond(call, 405, "text/plain", "Method not allowed")
                return
            }
            if (!isAuthorized(call, token)) {
                respond(call, 401, "text/plain", "Token ausente ou inválido.")
                return
            }
            when (call.requestURI.path) {
                "/", "/index.html" -> respond(call, 200, "text/html", pageHtml())
                "/api/snapshot" -> {
                    val json = snapshotJson()
                    if (json == null) {
                        respond(call, 503, "application/json", """{"error":"sem leitura ainda"}""")
                    } else {
                        respond(call, 200, "application/json", json)
                    }
                }
                else -> respond(call, 404, "text/plain", "Not found")
            }
        }
    }

    /** Token na query (`?t=`) ou em `Authorization: Bearer`; comparação em tempo constante. */
    private fun isAuthorized(exchange: HttpExchange, token: ByteArray): Boolean {
        val header = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")?.trim()
        val query = exchange.requestURI.rawQuery.orEmpty().split('&')
            .firstOrNull { part -> part.startsWith("t=") }
            ?.removePrefix("t=")
            ?.let { value -> URLDecoder.decode(value, StandardCharsets.UTF_8) }
        val candidate = (header ?: query ?: return false).toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(candidate, token)
    }

    private fun respond(exchange: HttpExchange, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.apply {
            set("Content-Type", "$contentType; charset=utf-8")
            // O token anda na URL: nada de cache, nem de Referer para fora.
            set("Cache-Control", "no-store")
            set("Referrer-Policy", "no-referrer")
            set("X-Content-Type-Options", "nosniff")
            set(
                "Content-Security-Policy",
                "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src data:"
            )
        }
        if (exchange.requestMethod == "HEAD") {
            exchange.sendResponseHeaders(code, -1)
            return
        }
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }
}

/** A página da grade de anéis, empacotada como recurso. */
private fun loadWebPage(): String {
    val stream = LocalWebAccessService::class.java.getResourceAsStream("/web/hud.html")
        ?: return "<!doctype html><title>Usage Monitor</title><p>Página indisponível neste pacote.</p>"
    return stream.use { input -> input.readBytes().toString(StandardCharsets.UTF_8) }
}

/** Endereços IPv4 privados das interfaces ativas: o que o celular da mesma rede alcança. */
internal fun siteLocalIpv4Addresses(): List<String> {
    return runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { network -> network.isUp && !network.isLoopback && !network.isVirtual }
            .flatMap { network -> network.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { address -> address.isSiteLocalAddress }
            .map { address -> address.hostAddress }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())
}
