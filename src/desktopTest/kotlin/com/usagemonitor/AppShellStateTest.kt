package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class AppShellStateTest {

    private val anthropic = UsageTargetKey.forSource(ApiSource.ANTHROPIC)
    private val minimax = UsageTargetKey.forSource(ApiSource.MINIMAX)
    private val codex = UsageTargetKey.forSource(ApiSource.CODEX)
    private val deepseek = UsageTargetKey.forSource(ApiSource.DEEPSEEK)

    @Test
    fun `changing the order saves the normalized order and updates the state`() {
        withTestSettings { settings ->
            val shell = AppShellState(settings, initialTargets = listOf(anthropic, minimax, codex))

            // Duplicata some, alvo fora da lista disponível cai, conta sem posição vai para o fim.
            shell.changeCardOrder(
                order = listOf(codex, deepseek, codex, anthropic),
                availableTargets = listOf(anthropic, minimax, codex)
            )

            assertEquals(listOf(codex, anthropic, minimax), shell.cardOrder)
            assertEquals("CODEX,ANTHROPIC:default,MINIMAX", settings.getStringOrNull(CARD_ORDER_KEY))
        }
    }

    @Test
    fun `the saved order is the one a restarted shell reads back`() {
        withTestSettings { settings ->
            AppShellState(settings, initialTargets = listOf(anthropic, minimax, codex))
                .changeCardOrder(
                    order = listOf(minimax, codex, anthropic),
                    availableTargets = listOf(anthropic, minimax, codex)
                )

            val restarted = AppShellState(settings, initialTargets = listOf(anthropic, minimax, codex))

            assertEquals(listOf(minimax, codex, anthropic), restarted.cardOrder)
        }
    }

    private fun withTestSettings(block: (PreferencesSettings) -> Unit) {
        val nodeName = "com.usagemonitor.tests.${UUID.randomUUID()}"
        val preferencesNode = Preferences.userRoot().node(nodeName)
        try {
            block(PreferencesSettings(preferencesNode))
        } finally {
            runCatching {
                preferencesNode.removeNode()
                preferencesNode.flush()
            }
        }
    }
}
