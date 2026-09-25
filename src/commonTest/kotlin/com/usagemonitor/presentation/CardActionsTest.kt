package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.CardAction
import com.usagemonitor.presentation.ui.components.cardActionsFor
import kotlin.test.Test
import kotlin.test.assertEquals

/** A regra única das janelas que o card abre — a barra do card e o balão da HUD. */
class CardActionsTest {

    private val padrao = UsageTargetKey(ApiSource.ANTHROPIC, "padrao")

    @Test
    fun `toda conta tem historico`() {
        assertEquals(listOf(CardAction.HISTORY), cardActionsFor(UsageTargetKey.forSource(ApiSource.DEEPSEEK), emptySet()))
        assertEquals(listOf(CardAction.HISTORY), cardActionsFor(UsageTargetKey.forSource(ApiSource.ANTIGRAVITY), setOf("padrao")))
    }

    @Test
    fun `sessoes cli sao da anthropic e do codex, cada uma com a sua`() {
        assertEquals(listOf(CardAction.HISTORY, CardAction.CLI_SESSIONS), cardActionsFor(padrao, emptySet()))
        assertEquals(
            listOf(CardAction.HISTORY, CardAction.CODEX_CLI_SESSIONS),
            cardActionsFor(UsageTargetKey.forSource(ApiSource.CODEX), emptySet())
        )
    }

    /** Uso e presença do time só na conta marcada como parte dele. */
    @Test
    fun `time so na conta anthropic marcada`() {
        assertEquals(
            listOf(CardAction.HISTORY, CardAction.CLI_SESSIONS, CardAction.TEAM_USAGE, CardAction.TEAM_PRESENCE),
            cardActionsFor(padrao, setOf("padrao"))
        )
        assertEquals(
            listOf(CardAction.HISTORY, CardAction.CLI_SESSIONS),
            cardActionsFor(UsageTargetKey(ApiSource.ANTHROPIC, "sandbox"), setOf("padrao"))
        )
    }
}
