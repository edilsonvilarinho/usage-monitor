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
