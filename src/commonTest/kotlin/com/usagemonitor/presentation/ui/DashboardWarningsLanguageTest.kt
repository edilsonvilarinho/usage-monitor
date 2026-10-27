package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.viewmodel.NETWORK_CONNECTIVITY_MARKER
import com.usagemonitor.presentation.viewmodel.UiApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Cada banner do dashboard nos dois idiomas. `DashboardScreenWarningsTest` cobre a
 * decisão em português; aqui a tabela garante que o inglês existe para todos e
 * diz a mesma coisa sobre o botão.
 */
class DashboardWarningsLanguageTest {

    private data class Case(
        val error: UiApiError,
        val englishTitle: String,
        val hasAction: Boolean
    )

    private val cases = listOf(
        Case(UiApiError(ApiSource.CODEX, "$NETWORK_CONNECTIVITY_MARKER timeout"), "Codex could not connect", hasAction = true),
        Case(UiApiError(ApiSource.ANTHROPIC, "Anthropic HTTP 407: Proxy Authentication Required"), "Proxy requires authentication", hasAction = false),
        Case(UiApiError(ApiSource.ANTHROPIC, "Anthropic HTTP 429: {}"), "Anthropic is temporarily limited", hasAction = true),
        Case(UiApiError(ApiSource.ANTHROPIC, "Anthropic HTTP 503: service unavailable"), "Anthropic is temporarily unavailable", hasAction = true),
        Case(UiApiError(ApiSource.ANTHROPIC, "Credentials not found"), "Anthropic needs authentication", hasAction = true),
        Case(
            UiApiError(ApiSource.ANTHROPIC, "Claude Code session is missing the expected permission or is outdated"),
            "Anthropic needs the session refreshed",
            hasAction = true
        ),
        Case(UiApiError(ApiSource.MINIMAX, "MiniMax API key not configured"), "MiniMax needs an API key", hasAction = false),
        Case(UiApiError(ApiSource.MINIMAX, "inactive token plan"), "MiniMax has no active plan/token", hasAction = false),
        Case(UiApiError(ApiSource.OPENCODE, "OpenCode local database not found"), "OpenCode Zen Free is unavailable", hasAction = false),
        Case(UiApiError(ApiSource.OPENCODE_GO, "OpenCode API key not configured"), "OpenCode Go needs an API key", hasAction = false),
        Case(UiApiError(ApiSource.OPENCODE_GO, "EntitlementError"), "OpenCode Go has no active subscription", hasAction = false),
        Case(UiApiError(ApiSource.KILO, "Kilo local database is unavailable"), "Kilo Free is unavailable", hasAction = false)
    )

    @Test
    fun `every warning has an english title and a different portuguese one`() {
        for (case in cases) {
            val english = assertNotNull(warningFor(case.error, AppLanguage.EN), case.englishTitle)
            val portuguese = assertNotNull(warningFor(case.error, AppLanguage.PT), case.englishTitle)

            assertEquals(case.englishTitle, english.title)
            assertNotEquals(english.title, portuguese.title, case.englishTitle)
            assertNotEquals(english.description, portuguese.description, case.englishTitle)
            // O botão é decisão do caso, não do idioma.
            assertEquals(case.hasAction, english.actionLabel != null, case.englishTitle)
            assertEquals(case.hasAction, portuguese.actionLabel != null, case.englishTitle)
        }
    }

    @Test
    fun `connectivity retries any source and other sources have no retry`() {
        val retried = mutableListOf<UsageTargetKey>()
        val connectivity = warningFor(cases[0].error, AppLanguage.EN)!!

        warningActionFor(connectivity) { target -> retried += target }!!.invoke()

        assertEquals(listOf(UsageTargetKey.forSource(ApiSource.CODEX)), retried)
        for (source in ApiSource.entries - ApiSource.ANTHROPIC) {
            val warning = DashboardWarning(
                target = UsageTargetKey.forSource(source),
                title = "t",
                description = "d",
                actionLabel = "Retry"
            )
            assertNull(warningActionFor(warning) { }, "$source")
        }
    }

    @Test
    fun `armed backoff names the time in english too`() {
        val armed = warningFor(
            UiApiError(
                target = UsageTargetKey.forSource(ApiSource.ANTHROPIC),
                message = "Anthropic HTTP 429: {}",
                retryAt = Instant.parse("2026-09-27T17:32:00Z")
            ),
            AppLanguage.EN
        )!!

        assertNull(armed.actionLabel)
        assertEquals(true, armed.description.contains("until 14:32 BRT"))
    }

    @Test
    fun `an ordinary failure has no banner`() {
        assertNull(warningFor(UiApiError(ApiSource.DEEPSEEK, "DeepSeek HTTP 500: boom"), AppLanguage.EN))
    }
}
