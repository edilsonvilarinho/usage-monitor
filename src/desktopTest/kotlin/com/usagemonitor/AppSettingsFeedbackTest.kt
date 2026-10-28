package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.entity.TeamCertificateProblem
import com.usagemonitor.domain.entity.TeamConnectionStage
import com.usagemonitor.presentation.ui.TeamCredentialKind
import java.net.ConnectException
import java.net.UnknownHostException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException
import com.usagemonitor.presentation.ui.components.ApiKeyCheckStatus
import com.usagemonitor.presentation.ui.components.ApiKeyCheckUiState
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiStatus
import com.usagemonitor.presentation.ui.components.SettingsField
import com.usagemonitor.presentation.ui.components.SettingsToast
import com.usagemonitor.presentation.ui.components.TeamConnectionUiStatus
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Os avisos de "salvo" e os quatro testes de conexão das Configurações, com o
 * cliente efêmero de cada teste trocado por um `MockEngine`.
 */
class AppSettingsFeedbackTest {

    private val harness = SettingsTestHarness { home -> SettingsTestHarness.writeDefaultAccount(home) }
    private val feedback get() = harness.feedback
    private val requests = mutableListOf<Pair<String, String?>>()

    @AfterTest
    fun cleanUp() {
        harness.close()
    }

    private fun activeTeam(participating: Set<String>) {
        harness.store.teamSettingsFlow.value = TeamIntegrationSettings(
            enabled = true,
            serverUrl = "https://team.example",
            apiKey = "k",
            alias = "ana",
            deviceId = "d",
            participatingProfileIds = participating
        )
    }

    @Test
    fun `two equal toasts are two events`() {
        feedback.showToast(SettingsToast.Saved(SettingsField.THEME))
        val first = feedback.toastEvent
        feedback.showToast(SettingsToast.Saved(SettingsField.THEME))

        assertNotEquals(first, feedback.toastEvent)
        assertEquals(first?.toast, feedback.toastEvent?.toast)
    }

    @Test
    fun `a refused save names the field and the reason in the trail`() {
        feedback.reportSave(SettingsField.NETWORK_PROXY, saved = false)
        feedback.reportSave(SettingsField.AUTO_START, saved = false, failureDetail = "reg.exe import retornou código 5")
        feedback.reportSave(SettingsField.THEME, saved = true)

        assertEquals(SettingsToast.Saved(SettingsField.THEME), feedback.toastEvent?.toast)
        val trail = harness.breadcrumbs.messages.joinToString("\n")
        assertTrue(trail.contains("salvar configuração NETWORK_PROXY"), trail)
        assertTrue(trail.contains("gravação recusada sem exceção retornada"), trail)
        assertTrue(trail.contains("reg.exe import retornou código 5"), trail)
        assertTrue(!trail.contains("THEME"), trail)
    }

    @Test
    fun `the proxy check reports OK, the HTTP status or the failure`() {
        harness.httpHandler = { request ->
            requests += request.url.toString() to null
            respond("zen", HttpStatusCode.OK)
        }
        feedback.checkProxyConnection()
        assertEquals(ProxyConnectionUiStatus.CHECKING, feedback.proxyConnection.status)
        harness.settle()
        assertEquals(ProxyConnectionUiStatus.OK, feedback.proxyConnection.status)
        assertEquals("Conexão OK.", feedback.proxyConnection.message)
        assertEquals("https://api.github.com/zen", requests.single().first)

        harness.httpHandler = { respond("", HttpStatusCode.ProxyAuthenticationRequired) }
        harness.language = AppLanguage.EN
        feedback.checkProxyConnection()
        harness.settle()
        assertEquals(ProxyConnectionUiStatus.FAILED, feedback.proxyConnection.status)
        assertEquals("HTTP 407", feedback.proxyConnection.message)

        harness.httpHandler = { throw IOException("connection refused") }
        feedback.checkProxyConnection()
        harness.settle()
        assertEquals(ProxyConnectionUiStatus.FAILED, feedback.proxyConnection.status)
        assertEquals("connection refused", feedback.proxyConnection.message)
        assertEquals(2, harness.breadcrumbs.messages.count { message -> message.contains("testar conexão do proxy") })
    }

    @Test
    fun `the key check uses the typed key, or the saved one when the field is empty`() {
        harness.store.apiKeySettings.value = harness.store.apiKeySettings.value.withKey(ApiSource.DEEPSEEK, "saved-key")
        harness.httpHandler = { request ->
            requests += request.url.encodedPath to request.headers[HttpHeaders.Authorization]
            respond(
                """{"is_available":true,"balance_infos":[{"currency":"USD","total_balance":"4.66","granted_balance":"0","topped_up_balance":"4.66"}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        feedback.checkApiKey(ApiSource.DEEPSEEK, "typed-key")
        assertEquals(ApiKeyCheckStatus.CHECKING, feedback.apiKeyCheck.status)
        harness.settle()
        assertEquals(ApiKeyCheckStatus.OK, feedback.apiKeyCheck.status)
        feedback.checkApiKey(ApiSource.DEEPSEEK, "  ")
        harness.settle()

        assertEquals(listOf("Bearer typed-key", "Bearer saved-key"), requests.map { request -> request.second })
        assertTrue(harness.breadcrumbs.messages.none { message -> message.contains("key") && message.contains("Bearer") })
        feedback.resetApiKeyCheck()
        assertEquals(ApiKeyCheckUiState(), feedback.apiKeyCheck)
    }

    @Test
    fun `a rejected key is a failed check without the key in the trail`() {
        harness.httpHandler = { respond("""{"error":"invalid"}""", HttpStatusCode.Unauthorized) }

        feedback.checkApiKey(ApiSource.DEEPSEEK, "bad-key")
        harness.settle()

        assertEquals(ApiKeyCheckStatus.FAILED, feedback.apiKeyCheck.status)
        val trail = harness.breadcrumbs.messages.joinToString("\n")
        assertTrue(trail.contains("testar chave da API DEEPSEEK"), trail)
        assertTrue(!trail.contains("bad-key"), trail)
    }

    @Test
    fun `the team check stops at an unreachable server`() {
        harness.health = Result.failure(IOException("servidor fora"))

        feedback.checkTeamConnection()
        assertEquals(TeamConnectionUiStatus.CHECKING, feedback.teamConnection.status)
        harness.settle()

        assertEquals(TeamConnectionUiStatus.FAILED, feedback.teamConnection.status)
        assertEquals("servidor fora", feedback.teamConnection.message)
        assertTrue(harness.claims.isEmpty())
    }

    @Test
    fun `a refused server certificate is published as the stage, with the key as the credential`() {
        val handshake = SSLHandshakeException("(certificate_expired) PKIX path validation failed")
        handshake.initCause(CertificateExpiredException("NotAfter"))
        harness.health = Result.failure(handshake)

        feedback.checkTeamConnection()
        harness.settle()

        assertEquals(TeamConnectionUiStatus.FAILED, feedback.teamConnection.status)
        assertEquals(TeamCertificateProblem.EXPIRED, feedback.teamConnection.failure?.certificateProblem)
        assertEquals(TeamCredentialKind.ACCOUNT_KEY, feedback.teamConnection.credentialKind)
    }

    @Test
    fun `a claim that cannot reach the server shows the stage instead of the account line`() {
        activeTeam(participating = setOf("default"))
        harness.claimResult = { Result.failure<TeamKeyVerification>(ConnectException("Connection refused")) }

        feedback.checkTeamConnection()
        harness.settle()

        assertEquals(TeamConnectionUiStatus.FAILED, feedback.teamConnection.status)
        assertEquals(TeamConnectionStage.CONNECTION, feedback.teamConnection.failure?.stage)
    }

    @Test
    fun `the admin token check publishes the stage with the admin token as the credential`() {
        harness.adminTokenResult = Result.failure(UnknownHostException("team.example"))

        feedback.validateAdminToken()
        harness.settle()

        assertEquals(TeamConnectionStage.ADDRESS, feedback.teamAdminConnection.failure?.stage)
        assertEquals(TeamCredentialKind.ADMIN_TOKEN, feedback.teamAdminConnection.credentialKind)
    }

    @Test
    fun `without a marked account only the server is checked`() {
        activeTeam(participating = emptySet())
        harness.language = AppLanguage.EN

        feedback.checkTeamConnection()
        harness.settle()

        assertEquals(TeamConnectionUiStatus.OK, feedback.teamConnection.status)
        assertEquals("Server OK. Select an account to check the key.", feedback.teamConnection.message)
        assertTrue(harness.claims.isEmpty())
    }

    @Test
    fun `each marked account is claimed with its uuid and email`() {
        activeTeam(participating = setOf("default"))

        feedback.checkTeamConnection()
        harness.settle()

        assertEquals(listOf<Pair<String, String?>>("uuid-1" to "me@example.com"), harness.claims.toList())
        assertEquals(TeamConnectionUiStatus.OK, feedback.teamConnection.status)
        assertEquals("Conexão OK e conta vinculada a esta chave.", feedback.teamConnection.message)
    }

    @Test
    fun `a claim error or a refused claim names the account that failed`() {
        activeTeam(participating = setOf("default"))
        harness.claimResult = { Result.failure<TeamKeyVerification>(IllegalStateException("HTTP 403")) }

        feedback.checkTeamConnection()
        harness.settle()
        assertEquals(TeamConnectionUiStatus.FAILED, feedback.teamConnection.status)
        assertEquals("Pessoal: HTTP 403", feedback.teamConnection.message)

        harness.claimResult = { Result.success(TeamKeyVerification(authorized = false, claimed = false)) }
        feedback.checkTeamConnection()
        harness.settle()
        assertEquals("Pessoal: a chave não cobre esta conta.", feedback.teamConnection.message)
        assertEquals(2, harness.breadcrumbs.messages.count { message -> message.contains("validar vínculo de conta") })
        assertTrue(harness.breadcrumbs.messages.none { message -> message.contains("me@example.com") })
    }

    @Test
    fun `the admin token check reports valid or the server message`() {
        feedback.validateAdminToken()
        assertEquals(TeamConnectionUiStatus.CHECKING, feedback.teamAdminConnection.status)
        harness.settle()
        assertEquals(TeamConnectionUiStatus.OK, feedback.teamAdminConnection.status)
        assertEquals("Token válido.", feedback.teamAdminConnection.message)

        harness.adminTokenResult = Result.failure(IllegalStateException())
        harness.language = AppLanguage.EN
        feedback.validateAdminToken()
        harness.settle()
        assertEquals(TeamConnectionUiStatus.FAILED, feedback.teamAdminConnection.status)
        assertEquals("Unknown failure.", feedback.teamAdminConnection.message)
        assertTrue(harness.breadcrumbs.messages.any { message -> message.contains("validar token administrativo do time") })
    }
}
