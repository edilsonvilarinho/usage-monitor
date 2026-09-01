package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.presentation.viewmodel.UiApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiStateTest {

    @Test
    fun `isAnthropicCredentialIssue true for PT not-found message`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "Credenciais não encontradas: /home/user/.claude/.credentials.json"
        )
        assertTrue(error.isAnthropicCredentialIssue)
    }

    @Test
    fun `isAnthropicCredentialIssue true for EN not-found message`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "Credentials not found: /home/user/.claude/.credentials.json"
        )
        assertTrue(error.isAnthropicCredentialIssue)
    }

    @Test
    fun `isAnthropicCredentialIssue true for token refresh failure (PT)`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "Token refresh retornou sem access_token"
        )
        assertTrue(error.isAnthropicCredentialIssue)
    }

    @Test
    fun `isAnthropicCredentialIssue true for token refresh failure (EN)`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "Token refresh returned without access_token"
        )
        assertTrue(error.isAnthropicCredentialIssue)
    }

    @Test
    fun `isAnthropicCredentialIssue true for Claude Code session scope guidance`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "Sua sessão do Claude Code está sem a permissão esperada ou desatualizada. Feche o app, reautentique no Claude Code e abra o monitor novamente."
        )
        assertTrue(error.isAnthropicCredentialIssue)
    }

    @Test
    fun `isAnthropicCredentialIssue false for unrelated error`() {
        val error = UiApiError(source = ApiSource.ANTHROPIC, message = "Anthropic HTTP 500: server error")
        assertFalse(error.isAnthropicCredentialIssue)
    }

    @Test
    fun `isAnthropicCredentialIssue false when source is MiniMax`() {
        // Mensagem que parece de credencial mas vem de outra fonte: não classifica.
        val error = UiApiError(source = ApiSource.MINIMAX, message = "Credentials not found")
        assertFalse(error.isAnthropicCredentialIssue)
    }

    @Test
    fun `isMiniMaxApiKeyIssue true for PT missing-key message`() {
        val error = UiApiError(
            source = ApiSource.MINIMAX,
            message = "Chave da API MiniMax não configurada."
        )
        assertTrue(error.isMiniMaxApiKeyIssue)
    }

    @Test
    fun `isMiniMaxApiKeyIssue true for EN missing-key message`() {
        val error = UiApiError(
            source = ApiSource.MINIMAX,
            message = "MiniMax API key not configured"
        )
        assertTrue(error.isMiniMaxApiKeyIssue)
    }

    @Test
    fun `isMiniMaxApiKeyIssue false when message lacks key marker`() {
        val error = UiApiError(source = ApiSource.MINIMAX, message = "não configurada")
        assertFalse(error.isMiniMaxApiKeyIssue)
    }

    @Test
    fun `isMiniMaxApiKeyIssue false when source is Anthropic`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "MiniMax API key not configured"
        )
        assertFalse(error.isMiniMaxApiKeyIssue)
    }

    @Test
    fun `isMiniMaxInactivePlanIssue true for friendly PT message`() {
        val error = UiApiError(
            source = ApiSource.MINIMAX,
            message = "MiniMax sem plano/token ativo. Ative um plano ou gere um token com assinatura válida e tente novamente."
        )
        assertTrue(error.isMiniMaxInactivePlanIssue)
    }

    @Test
    fun `isMiniMaxInactivePlanIssue true for raw API message`() {
        val error = UiApiError(
            source = ApiSource.MINIMAX,
            message = "no active token plan subscription"
        )
        assertTrue(error.isMiniMaxInactivePlanIssue)
    }

    @Test
    fun `isMiniMaxInactivePlanIssue false when source is Anthropic`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "no active token plan subscription"
        )
        assertFalse(error.isMiniMaxInactivePlanIssue)
    }

    @Test
    fun `isConfigurationIssue true if any sub-check passes`() {
        val anthropicError = UiApiError(source = ApiSource.ANTHROPIC, message = "Credenciais não encontradas")
        val miniMaxError = UiApiError(source = ApiSource.MINIMAX, message = "MiniMax API key not configured")
        val inactivePlanError = UiApiError(
            source = ApiSource.MINIMAX,
            message = "MiniMax sem plano/token ativo"
        )
        assertTrue(anthropicError.isConfigurationIssue)
        assertTrue(miniMaxError.isConfigurationIssue)
        assertTrue(inactivePlanError.isConfigurationIssue)
    }

    @Test
    fun `isConfigurationIssue false for generic API error`() {
        val error = UiApiError(source = ApiSource.CODEX, message = "Codex HTTP 503: unavailable")
        assertFalse(error.isConfigurationIssue)
    }

    @Test
    fun `isServiceUnavailableIssue true for upstream 503 message`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "Anthropic HTTP 503: upstream connect error or disconnect/reset before headers. reset reason: remote connection failure"
        )
        assertTrue(error.isServiceUnavailableIssue)
    }

    @Test
    fun `isServiceUnavailableIssue false for unrelated client error`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "Anthropic HTTP 400: invalid request"
        )
        assertFalse(error.isServiceUnavailableIssue)
    }

    @Test
    fun `isConnectivityIssue true when the connectivity marker is present`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "usage-monitor:network-connectivity-failure (Connection refused: connect)"
        )
        assertTrue(error.isConnectivityIssue)
    }

    @Test
    fun `isConnectivityIssue false without the marker`() {
        val error = UiApiError(source = ApiSource.ANTHROPIC, message = "Connection refused: connect")
        assertFalse(error.isConnectivityIssue)
    }

    @Test
    fun `isConnectivityIssue is not a configuration issue`() {
        // Categoria própria (issue #174): a causa não é credencial errada, e
        // classificar como configuração orientaria o usuário a revisar login
        // em vez de proxy.
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "usage-monitor:network-connectivity-failure (Connection refused: connect)"
        )
        assertFalse(error.isConfigurationIssue)
    }

    @Test
    fun `isProxyAuthIssue true for HTTP 407`() {
        val error = UiApiError(source = ApiSource.ANTHROPIC, message = "Anthropic HTTP 407: Proxy Authentication Required")
        assertTrue(error.isProxyAuthIssue)
    }

    @Test
    fun `isProxyAuthIssue counts as configuration issue`() {
        val error = UiApiError(source = ApiSource.ANTHROPIC, message = "Anthropic HTTP 407: Proxy Authentication Required")
        assertTrue(error.isConfigurationIssue)
    }

    @Test
    fun `isProxyAuthIssue false for unrelated status`() {
        val error = UiApiError(source = ApiSource.ANTHROPIC, message = "Anthropic HTTP 400: invalid request")
        assertFalse(error.isProxyAuthIssue)
    }

    @Test
    fun `formattedMessage prefixes source label`() {
        assertEquals(
            "Anthropic: timeout",
            UiApiError(ApiSource.ANTHROPIC, "timeout").formattedMessage
        )
        assertEquals(
            "MiniMax: rate limited",
            UiApiError(ApiSource.MINIMAX, "rate limited").formattedMessage
        )
        assertEquals(
            "Codex: 401 unauthorized",
            UiApiError(ApiSource.CODEX, "401 unauthorized").formattedMessage
        )
        assertEquals(
            "OpenCode Zen Free: local db missing",
            UiApiError(ApiSource.OPENCODE, "local db missing").formattedMessage
        )
        assertEquals(
            "Kilo Free: local db missing",
            UiApiError(ApiSource.KILO, "local db missing").formattedMessage
        )
    }
}
