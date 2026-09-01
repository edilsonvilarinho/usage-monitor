package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.viewmodel.UiApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardScreenWarningsTest {

    // ── Identificação do alvo ────────────────────────────────────────────
    // Com várias contas Anthropic falhando ao mesmo tempo, dois banners de
    // título idêntico não dizem qual conta precisa de atenção (issue #64).

    @Test
    fun `credential warning title names the failing profile`() {
        val warning = warningFor(
            error = anthropicError(
                message = "Token refresh falhou (HTTP 400): {\"type\":\"error\"}",
                profileId = "conta2",
                targetLabel = "Anthropic — INFORMATA2"
            ),
            language = AppLanguage.PT
        )

        assertNotNull(warning)
        assertEquals("Anthropic — INFORMATA2 precisa de autenticação", warning.title)
        assertEquals(UsageTargetKey(ApiSource.ANTHROPIC, "conta2"), warning.target)
    }

    @Test
    fun `warning title falls back to the source label without a target label`() {
        val warning = warningFor(
            error = UiApiError(
                source = ApiSource.MINIMAX,
                message = "Chave da API MiniMax não configurada"
            ),
            language = AppLanguage.PT
        )

        assertNotNull(warning)
        assertTrue(warning.title.startsWith("MiniMax"), warning.title)
    }

    // ── Classificação da falha de renovação ──────────────────────────────

    @Test
    fun `refresh http failure asks for authentication`() {
        val error = anthropicError(
            message = "Token refresh falhou (HTTP 400): invalid_request_error",
            profileId = "default",
            targetLabel = "Anthropic — Padrão"
        )

        assertTrue(error.isAnthropicCredentialIssue)
        val warning = warningFor(error = error, language = AppLanguage.EN)
        assertNotNull(warning)
        assertTrue(warning.title.endsWith("needs authentication"), warning.title)
    }

    @Test
    fun `rate limited refresh does not ask for authentication`() {
        val warning = warningFor(
            error = anthropicError(
                message = "Token refresh falhou (HTTP 429): rate_limit_error",
                profileId = "default",
                targetLabel = "Anthropic — Padrão"
            ),
            language = AppLanguage.PT
        )

        assertNotNull(warning)
        assertEquals("Anthropic — Padrão temporariamente limitado", warning.title)
    }

    @Test
    fun `unavailable refresh does not ask for authentication`() {
        val warning = warningFor(
            error = anthropicError(
                message = "Token refresh falhou (HTTP 503): service unavailable",
                profileId = "default",
                targetLabel = "Anthropic — Padrão"
            ),
            language = AppLanguage.PT
        )

        assertNotNull(warning)
        assertEquals("Anthropic — Padrão temporariamente indisponível", warning.title)
    }

    // ── Ação do banner ───────────────────────────────────────────────────

    @Test
    fun `retry action targets only the profile that failed`() {
        val target = UsageTargetKey(ApiSource.ANTHROPIC, "conta3")
        val warning = DashboardWarning(
            target = target,
            title = "irrelevante",
            description = "irrelevante",
            actionLabel = "Tentar novamente"
        )
        val retried = mutableListOf<UsageTargetKey>()

        val action = warningActionFor(warning = warning) { key -> retried.add(key) }
        assertNotNull(action)
        action()

        assertEquals(listOf(target), retried)
    }

    @Test
    fun `non anthropic warnings have no retry action`() {
        val warning = DashboardWarning(
            target = UsageTargetKey.forSource(ApiSource.MINIMAX),
            title = "irrelevante",
            description = "irrelevante",
            actionLabel = null
        )

        assertNull(warningActionFor(warning = warning) { })
    }

    // ── Conectividade / proxy corporativo (issue #174) ──────────────────

    @Test
    fun `connectivity warning offers retry even for a non anthropic source`() {
        val target = UsageTargetKey.forSource(ApiSource.MINIMAX)
        val warning = warningFor(
            error = UiApiError(
                target = target,
                message = "usage-monitor:network-connectivity-failure (Connection refused: connect)"
            ),
            language = AppLanguage.PT
        )

        assertNotNull(warning)
        assertTrue(warning.forcesUniversalRetry)

        val retried = mutableListOf<UsageTargetKey>()
        val action = warningActionFor(warning = warning) { key -> retried.add(key) }
        assertNotNull(action)
        action()
        assertEquals(listOf(target), retried)
    }

    @Test
    fun `connectivity warning is not classified as a configuration issue`() {
        val error = UiApiError(
            source = ApiSource.MINIMAX,
            message = "usage-monitor:network-connectivity-failure (Connection refused: connect)"
        )

        assertTrue(error.isConnectivityIssue)
        assertNotNull(warningFor(error = error, language = AppLanguage.PT))
    }

    @Test
    fun `proxy auth failure asks to review credentials without offering a retry`() {
        // Fonte não-Anthropic de propósito: `warningActionFor` sempre devolve
        // ação para ApiSource.ANTHROPIC, independente do `actionLabel` — mesmo
        // comportamento das demais mensagens de configuração (MiniMax, Kilo,
        // OpenCode Go), que também usam `actionLabel = null` só em fontes que
        // não sejam Anthropic.
        val warning = warningFor(
            error = UiApiError(source = ApiSource.MINIMAX, message = "MiniMax HTTP 407: Proxy Authentication Required"),
            language = AppLanguage.EN
        )

        assertNotNull(warning)
        assertNull(warning.actionLabel)
        assertNull(warningActionFor(warning = warning) { })
    }

    // ── OpenCode Go (issue #124) ─────────────────────────────────────────

    @Test
    fun `missing opencode key asks for the key without offering a retry`() {
        val warning = warningFor(
            error = UiApiError(
                source = ApiSource.OPENCODE_GO,
                message = "Chave da API OpenCode não configurada. Abra Configurações > APIs e informe a chave."
            ),
            language = AppLanguage.PT
        )

        assertNotNull(warning)
        assertEquals("OpenCode Go precisa de uma API key", warning.title)
        assertNull(warning.actionLabel)
    }

    /**
     * Chave válida sem plano Go não é defeito de credencial: o banner precisa
     * mandar assinar ou desligar, e nunca revalidar a chave.
     */
    @Test
    fun `account without the go plan gets its own warning`() {
        val error = UiApiError(
            source = ApiSource.OPENCODE_GO,
            message = "OpenCode Go sem assinatura ativa para esta chave. Assine o plano Go ou desative esta integração."
        )

        assertTrue(error.isOpenCodeGoSubscriptionIssue)
        assertTrue(error.isConfigurationIssue)

        val warning = warningFor(error = error, language = AppLanguage.PT)
        assertNotNull(warning)
        assertEquals("OpenCode Go sem assinatura ativa", warning.title)
        assertNull(warning.actionLabel)
    }

    /**
     * A ordem de `warningFor` importa: 429 é testado antes de qualquer falha de
     * configuração, então um limite de requisições continua no banner de "aguarde".
     */
    @Test
    fun `rate limited opencode go stays on the wait banner`() {
        val warning = warningFor(
            error = UiApiError(
                source = ApiSource.OPENCODE_GO,
                message = "OpenCode Go HTTP 429: Too Many Requests"
            ),
            language = AppLanguage.PT
        )

        assertNotNull(warning)
        assertTrue(warning.title.endsWith("temporariamente limitado"), warning.title)
    }

    private fun anthropicError(
        message: String,
        profileId: String,
        targetLabel: String
    ): UiApiError {
        return UiApiError(
            target = UsageTargetKey(ApiSource.ANTHROPIC, profileId),
            message = message,
            rawMessage = message,
            targetLabel = targetLabel
        )
    }
}
