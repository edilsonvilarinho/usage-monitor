package com.usagemonitor.presentation.viewmodel

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Falha de conectividade classificada pelo TIPO da exceção, nunca por substring
 * da mensagem: o texto de `ConnectException`/`SocketTimeoutException` varia por
 * JVM e por sistema operacional, e não dá para confiar nele.
 *
 * Mora aqui, e não dentro do [DashboardViewModel] onde nasceu, porque ganhou um
 * segundo consumidor: o "Testar chave" das Configurações (issue #204) precisa
 * separar "não chegou na API" de "a API recusou a chave" com o **mesmo** critério
 * que o dashboard usa. Dois donos da mesma decisão divergiriam justamente no caso
 * de borda, e o usuário atrás de proxy corporativo veria o teste mandá-lo revisar
 * uma credencial que está correta.
 *
 * HTTP 407 (proxy exige credencial) **não** passa por aqui: chega como resposta
 * HTTP normal e cai no mecanismo de marcador de status já usado por 429/503 (ver
 * `RemoteApiDataSource.requireSuccess`).
 */
internal fun isConnectivityFailure(error: Throwable): Boolean {
    return isConnectivityException(error) || isConnectivityException(error.cause)
}

/**
 * `error.cause` também é checado por [isConnectivityFailure] porque o Ktor às
 * vezes envelopa a exceção de socket original numa própria.
 */
private fun isConnectivityException(error: Throwable?): Boolean {
    return when (error) {
        null -> false
        // `ConnectTimeoutException` do Ktor estende `ConnectException`, então
        // este branch já cobre o timeout de conexão do próprio Ktor.
        is UnknownHostException,
        is NoRouteToHostException,
        is ConnectException,
        is SocketTimeoutException,
        is SSLHandshakeException,
        is HttpRequestTimeoutException -> true
        else -> false
    }
}
