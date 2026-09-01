package com.usagemonitor.domain.entity

/**
 * Configuração de proxy HTTP corporativo (issue #174).
 *
 * [useEnvironmentProxy] resolve a precedência pedida na issue com um campo só:
 * ligado (default) autodetecta `HTTPS_PROXY`/`HTTP_PROXY` do sistema; desligado
 * usa exclusivamente [host]/[port]/[username]/[password] preenchidos aqui. Não
 * existe um terceiro estado "forçar sem proxy mesmo com variável de ambiente
 * definida" — não foi pedido pela issue, e um booleano a mais aqui reabriria uma
 * combinação que ninguém pediu para resolver.
 */
data class ProxySettings(
    val useEnvironmentProxy: Boolean = true,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
) {
    val isManualConfigured: Boolean
        get() = host.isNotBlank() && port in 1..65535

    /** Ligado manualmente e com o mínimo para montar o proxy (host + porta). */
    val isManualActive: Boolean
        get() = !useEnvironmentProxy && isManualConfigured
}

/**
 * Proxy já resolvido — origem (manual ou variável de ambiente) não importa mais
 * daqui em diante, só o que o `HttpClient` precisa para montar a conexão.
 */
data class ProxyEnvironmentConfig(
    val scheme: String,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null
)

/**
 * Parser puro de `HTTP_PROXY`/`HTTPS_PROXY` no formato
 * `scheme://[usuário[:senha]@]host[:porta]` — convenção usada por curl, npm,
 * pip e a maioria das ferramentas de linha de comando, nunca documentada pela
 * Anthropic/OkHttp/Ktor porque é convenção de shell, não de uma API específica.
 *
 * Devolve `null` para entrada vazia ou sem host reconhecível: o chamador trata
 * isso como "sem proxy configurado nesta variável", nunca como erro fatal — uma
 * `HTTP_PROXY` mal formada não pode derrubar o boot do app.
 */
fun parseProxyEnvironmentValue(raw: String): ProxyEnvironmentConfig? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    val schemeSeparatorIndex = trimmed.indexOf("://")
    val scheme = if (schemeSeparatorIndex >= 0) {
        trimmed.substring(0, schemeSeparatorIndex)
    } else {
        "http"
    }
    val afterScheme = if (schemeSeparatorIndex >= 0) {
        trimmed.substring(schemeSeparatorIndex + 3)
    } else {
        trimmed
    }
    // A barra final de path (ex.: `http://proxy:8080/`) não faz sentido para um
    // proxy — descartada em vez de virar parte do host.
    val withoutPath = afterScheme.substringBefore("/")

    val userInfoSeparatorIndex = withoutPath.lastIndexOf("@")
    val userInfo = if (userInfoSeparatorIndex >= 0) {
        withoutPath.substring(0, userInfoSeparatorIndex)
    } else {
        null
    }
    val hostPort = if (userInfoSeparatorIndex >= 0) {
        withoutPath.substring(userInfoSeparatorIndex + 1)
    } else {
        withoutPath
    }

    val hostPortSeparatorIndex = hostPort.lastIndexOf(":")
    val host = if (hostPortSeparatorIndex >= 0) {
        hostPort.substring(0, hostPortSeparatorIndex)
    } else {
        hostPort
    }
    if (host.isBlank()) {
        return null
    }
    val port = if (hostPortSeparatorIndex >= 0) {
        hostPort.substring(hostPortSeparatorIndex + 1).toIntOrNull() ?: return null
    } else {
        // Sem porta explícita: 80 é o default de HTTP e o mais comum em proxy
        // corporativo sem porta customizada — 443 não se aplica aqui porque o
        // proxy em si fala HTTP mesmo quando encaminha tráfego HTTPS via CONNECT.
        80
    }

    val username = userInfo?.substringBefore(":")?.takeIf { it.isNotEmpty() }
    val password = userInfo?.let { info ->
        if (info.contains(":")) info.substringAfter(":") else null
    }

    return ProxyEnvironmentConfig(
        scheme = scheme,
        host = host,
        port = port,
        username = username,
        password = password
    )
}
