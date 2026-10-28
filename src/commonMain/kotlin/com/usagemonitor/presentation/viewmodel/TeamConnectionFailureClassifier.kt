package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.HttpStatusFailure
import com.usagemonitor.domain.entity.TeamCertificateProblem
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.time.Instant

private const val UNAUTHORIZED_STATUS = 401
private const val FORBIDDEN_STATUS = 403

/**
 * Etapa em que a conversa com o servidor de time parou (issue #494), pelo
 * **tipo** de cada exceção da cadeia de causas — mesma regra do
 * [isConnectivityFailure]: o texto da mensagem varia por JVM e não serve.
 *
 * Nulo quando nenhum tipo conhecido aparece; a tela então mostra a mensagem
 * como antes, em vez de inventar uma etapa.
 */
internal fun teamConnectionFailureOf(error: Throwable): TeamConnectionFailure? {
    val chain = causeChainOf(error)
    for (link in chain) {
        val failure = stageFailureOf(link, chain)
        if (failure != null) {
            return failure
        }
    }
    return null
}

private fun stageFailureOf(link: Throwable, chain: List<Throwable>): TeamConnectionFailure? {
    return when (link) {
        is HttpStatusFailure -> {
            val stage = if (link.statusCode == UNAUTHORIZED_STATUS || link.statusCode == FORBIDDEN_STATUS) {
                TeamConnectionStage.CREDENTIAL
            } else {
                TeamConnectionStage.SERVER
            }
            TeamConnectionFailure(stage = stage, httpStatus = link.statusCode)
        }
        // Verificação de nome do OkHttp: o certificado é válido, mas não cobre o host.
        is SSLPeerUnverifiedException -> TeamConnectionFailure(
            stage = TeamConnectionStage.CERTIFICATE,
            certificateProblem = TeamCertificateProblem.HOSTNAME_MISMATCH
        )
        is SSLHandshakeException -> certificateFailureOf(chain)
        is UnknownHostException -> TeamConnectionFailure(stage = TeamConnectionStage.ADDRESS)
        // Antes de `ConnectException`, que ela estende: o nome resolveu, o tempo
        // esgotou na conexão.
        is ConnectTimeoutException -> TeamConnectionFailure(stage = TeamConnectionStage.CONNECTION, timedOut = true)
        is ConnectException,
        is NoRouteToHostException -> TeamConnectionFailure(stage = TeamConnectionStage.CONNECTION)
        // Não diz se esgotou resolvendo, conectando ou lendo: nada antes ganha ✓.
        is SocketTimeoutException,
        is HttpRequestTimeoutException -> TeamConnectionFailure(
            stage = TeamConnectionStage.CONNECTION,
            timedOut = true,
            earlierStagesProven = false
        )
        else -> null
    }
}

private fun certificateFailureOf(chain: List<Throwable>): TeamConnectionFailure {
    val validation = chain.firstNotNullOfOrNull { link -> link as? CertPathValidatorException }
    val problem = when {
        validation?.reason == CertPathValidatorException.BasicReason.EXPIRED -> TeamCertificateProblem.EXPIRED
        validation?.reason == CertPathValidatorException.BasicReason.NOT_YET_VALID -> TeamCertificateProblem.NOT_YET_VALID
        chain.any { link -> link is CertificateExpiredException } -> TeamCertificateProblem.EXPIRED
        chain.any { link -> link is CertificateNotYetValidException } -> TeamCertificateProblem.NOT_YET_VALID
        chain.any { link -> link is CertPathBuilderException } -> TeamCertificateProblem.UNTRUSTED
        else -> TeamCertificateProblem.OTHER
    }
    val notAfter = if (problem == TeamCertificateProblem.EXPIRED) rejectedCertificateOf(validation)?.notAfter else null
    return TeamConnectionFailure(
        stage = TeamConnectionStage.CERTIFICATE,
        certificateProblem = problem,
        certificateNotAfter = notAfter?.let { date -> Instant.fromEpochMilliseconds(date.time) }
    )
}

/** O validador PKIX aponta o certificado recusado por caminho e índice. */
private fun rejectedCertificateOf(validation: CertPathValidatorException?): X509Certificate? {
    val certificates = validation?.certPath?.certificates ?: return null
    return certificates.getOrNull(validation.index) as? X509Certificate
}

/** Cadeia de causas sem repetição: uma causa cíclica não prende o laço. */
private fun causeChainOf(error: Throwable): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = error
    while (current != null && chain.none { seen -> seen === current }) {
        chain += current
        current = current.cause
    }
    return chain
}
