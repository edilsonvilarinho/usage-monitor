package com.usagemonitor.presentation

import com.usagemonitor.data.datasource.TeamServerException
import com.usagemonitor.domain.entity.TeamCertificateProblem
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import com.usagemonitor.presentation.viewmodel.teamConnectionFailureOf
import java.io.ByteArrayInputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TeamConnectionFailureClassifierTest {

    @Test
    fun `expired server certificate stops at certificate with the date read from the certificate`() {
        val error = handshakeFailure(realExpiredValidationFailure())

        assertEquals(
            TeamConnectionFailure(
                stage = TeamConnectionStage.CERTIFICATE,
                certificateProblem = TeamCertificateProblem.EXPIRED,
                certificateNotAfter = Instant.parse("2020-01-02T14:02:17Z")
            ),
            teamConnectionFailureOf(error)
        )
    }

    @Test
    fun `wrapper around the handshake failure is unwrapped`() {
        // O Ktor às vezes envelopa a exceção de rede numa própria.
        val error = IllegalStateException("wrapped", handshakeFailure(realExpiredValidationFailure()))

        assertEquals(TeamCertificateProblem.EXPIRED, teamConnectionFailureOf(error)?.certificateProblem)
    }

    @Test
    fun `unknown issuer is an untrusted certificate`() {
        val error = handshakeFailure(CertificateException(CertPathBuilderException("unable to find valid certification path")))

        assertEquals(
            TeamConnectionFailure(stage = TeamConnectionStage.CERTIFICATE, certificateProblem = TeamCertificateProblem.UNTRUSTED),
            teamConnectionFailureOf(error)
        )
    }

    @Test
    fun `hostname that the certificate does not cover is a hostname mismatch`() {
        val error = SSLPeerUnverifiedException("Hostname lab.example not verified")

        assertEquals(TeamCertificateProblem.HOSTNAME_MISMATCH, teamConnectionFailureOf(error)?.certificateProblem)
    }

    @Test
    fun `handshake failure without a known cause is a certificate problem of other kind`() {
        assertEquals(
            TeamConnectionFailure(stage = TeamConnectionStage.CERTIFICATE, certificateProblem = TeamCertificateProblem.OTHER),
            teamConnectionFailureOf(SSLHandshakeException("Received fatal alert: handshake_failure"))
        )
    }

    @Test
    fun `name that does not resolve stops at address`() {
        assertEquals(
            TeamConnectionFailure(stage = TeamConnectionStage.ADDRESS),
            teamConnectionFailureOf(UnknownHostException("lab.example"))
        )
    }

    @Test
    fun `refused connection stops at connection with the address proven`() {
        assertEquals(
            TeamConnectionFailure(stage = TeamConnectionStage.CONNECTION),
            teamConnectionFailureOf(ConnectException("Connection refused"))
        )
    }

    @Test
    fun `socket timeout stops at connection without claiming the address`() {
        assertEquals(
            TeamConnectionFailure(stage = TeamConnectionStage.CONNECTION, timedOut = true, earlierStagesProven = false),
            teamConnectionFailureOf(SocketTimeoutException("timeout"))
        )
    }

    @Test
    fun `401 and 403 from the team server stop at credential`() {
        assertEquals(
            TeamConnectionFailure(stage = TeamConnectionStage.CREDENTIAL, httpStatus = 401),
            teamConnectionFailureOf(TeamServerException(statusCode = 401, message = "HTTP 401"))
        )
        assertEquals(TeamConnectionStage.CREDENTIAL, teamConnectionFailureOf(TeamServerException(403, "HTTP 403"))?.stage)
    }

    @Test
    fun `other status from the team server stops at server`() {
        assertEquals(
            TeamConnectionFailure(stage = TeamConnectionStage.SERVER, httpStatus = 502),
            teamConnectionFailureOf(TeamServerException(statusCode = 502, message = "HTTP 502"))
        )
    }

    @Test
    fun `failure of unknown type is not classified`() {
        assertNull(teamConnectionFailureOf(IllegalArgumentException("parse error")))
    }

    @Test
    fun `cyclic cause chain does not loop`() {
        val outer = IllegalStateException("outer")
        val inner = IllegalStateException("inner", outer)
        outer.initCause(inner)

        assertNull(teamConnectionFailureOf(outer))
    }

    /** O JSSE entrega a falha do gerenciador de confiança dentro do handshake. */
    private fun handshakeFailure(cause: Throwable): SSLHandshakeException {
        return SSLHandshakeException("(certificate_expired) PKIX path validation failed").apply { initCause(cause) }
    }

    /**
     * Falha **real** do validador PKIX do JDK contra um certificado vencido em
     * 2020-01-02 — a mesma cadeia medida contra o servidor de time no #494
     * (`CertPathValidatorException` com motivo `EXPIRED`, caminho e índice).
     */
    private fun realExpiredValidationFailure(): CertificateException {
        val factory = CertificateFactory.getInstance("X.509")
        val certificate = factory.generateCertificate(ByteArrayInputStream(EXPIRED_PEM.toByteArray())) as X509Certificate
        val parameters = PKIXParameters(setOf(TrustAnchor(certificate, null))).apply { isRevocationEnabled = false }
        val path = factory.generateCertPath(listOf(certificate))
        val failure = runCatching { CertPathValidator.getInstance("PKIX").validate(path, parameters) }
            .exceptionOrNull() as CertPathValidatorException
        return CertificateException(failure)
    }

    private companion object {
        // keytool -genkeypair -keyalg EC -dname CN=expired.test -startdate 2020/01/01 -validity 1
        const val EXPIRED_PEM = """-----BEGIN CERTIFICATE-----
MIIBRDCB66ADAgECAghykqkEOTspSTAKBggqhkjOPQQDAzAXMRUwEwYDVQQDEwxl
eHBpcmVkLnRlc3QwHhcNMjAwMTAxMTQwMjE3WhcNMjAwMTAyMTQwMjE3WjAXMRUw
EwYDVQQDEwxleHBpcmVkLnRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARd
ReO0RZk/+Ka6+DAL3XlE4IuVq509OaqfxPDBKnCL168kw39CvvxBM84aCLnfMtom
XZexhBomNngy/K1JVK4FoyEwHzAdBgNVHQ4EFgQU5wFXckunQUcqmPmskoV4Toqk
/ZQwCgYIKoZIzj0EAwMDSAAwRQIhAPJgC4Dc8euOZr2WFFzEnM9y5BUpO+fMdv9/
AF9HssBQAiBfHL90LZyVCNYPybEhdXlBaGcJlZL7Z6t8XzMqGDLXtQ==
-----END CERTIFICATE-----"""
    }
}
