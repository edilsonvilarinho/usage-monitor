package com.usagemonitor.data.repository

import com.usagemonitor.domain.entity.ProxyEnvironmentConfig
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.parseProxyEnvironmentValue

/**
 * Nomes das variáveis de ambiente padrão de proxy — convenção de shell (curl,
 * npm, pip), não uma API própria do app. Mesmo padrão de nome de constante de
 * `UPDATE_FEED_URL_ENV_VAR` (`AppUpdateRepositoryImpl.kt`).
 */
const val HTTPS_PROXY_ENV_VAR = "HTTPS_PROXY"
const val HTTP_PROXY_ENV_VAR = "HTTP_PROXY"

/**
 * Resolve o proxy efetivo a partir da configuração manual das Configurações ou
 * das variáveis de ambiente do sistema (issue #174).
 *
 * Precedência: manual explicitamente ligado > `HTTPS_PROXY` > `HTTP_PROXY` >
 * nenhum proxy. `NO_PROXY` fica fora do escopo — não foi pedido pela issue.
 *
 * [envVarReader] injetável em vez de `System.getenv` direto — mesmo desenho de
 * `AppUpdateRepositoryImpl.envVarReader`, para o teste não depender do
 * ambiente real do processo.
 */
fun resolveEffectiveProxy(
    settings: ProxySettings,
    envVarReader: (String) -> String? = System::getenv
): ProxyEnvironmentConfig? {
    if (settings.isManualActive) {
        return ProxyEnvironmentConfig(
            scheme = "http",
            host = settings.host,
            port = settings.port,
            username = settings.username.takeIf { it.isNotBlank() },
            password = settings.password.takeIf { it.isNotBlank() }
        )
    }

    val httpsProxy = envVarReader(HTTPS_PROXY_ENV_VAR)?.let(::parseProxyEnvironmentValue)
    if (httpsProxy != null) {
        return httpsProxy
    }

    return envVarReader(HTTP_PROXY_ENV_VAR)?.let(::parseProxyEnvironmentValue)
}
