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
    fun `isMiniMaxEnvVarIssue true for PT env-var message`() {
        val error = UiApiError(
            source = ApiSource.MINIMAX,
            message = "Variável de ambiente MINIMAX_API_KEY não configurada."
        )
        assertTrue(error.isMiniMaxEnvVarIssue)
    }

    @Test
    fun `isMiniMaxEnvVarIssue true for EN env-var message`() {
        val error = UiApiError(
            source = ApiSource.MINIMAX,
            message = "Environment variable MINIMAX_API_KEY not configured"
        )
        assertTrue(error.isMiniMaxEnvVarIssue)
    }

    @Test
    fun `isMiniMaxEnvVarIssue false when message lacks env var name`() {
        val error = UiApiError(source = ApiSource.MINIMAX, message = "não configurada")
        assertFalse(error.isMiniMaxEnvVarIssue)
    }

    @Test
    fun `isMiniMaxEnvVarIssue false when source is Anthropic`() {
        val error = UiApiError(
            source = ApiSource.ANTHROPIC,
            message = "MINIMAX_API_KEY not configured"
        )
        assertFalse(error.isMiniMaxEnvVarIssue)
    }

    @Test
    fun `isConfigurationIssue true if any sub-check passes`() {
        val anthropicError = UiApiError(source = ApiSource.ANTHROPIC, message = "Credenciais não encontradas")
        val miniMaxError = UiApiError(source = ApiSource.MINIMAX, message = "MINIMAX_API_KEY not configured")
        assertTrue(anthropicError.isConfigurationIssue)
        assertTrue(miniMaxError.isConfigurationIssue)
    }

    @Test
    fun `isConfigurationIssue false for generic API error`() {
        val error = UiApiError(source = ApiSource.CODEX, message = "Codex HTTP 503: unavailable")
        assertFalse(error.isConfigurationIssue)
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
