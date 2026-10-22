package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.ConfigKeys
import com.usagemonitor.parseCommandLine
import com.usagemonitor.presentation.ui.components.SettingsTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tabela 4.4 do protótipo como teste (#440, critério de aceite): cada aba das
 * Configurações do app tem caminho no terminal, e cada caminho é um comando que existe.
 * O `when` é exaustivo: aba nova não compila sem decidir o caminho dela.
 */
class TerminalSettingsCoverageTest {

    /** Os comandos de cada aba, como alguém os digitaria (o segredo nunca vai na linha). */
    private fun commandsOf(tab: SettingsTab): List<String> =
        when (tab) {
            // Tema, opacidade, escala e movimento reduzido não se aplicam ao terminal.
            SettingsTab.APPEARANCE -> listOf("config set language en")
            // Anel da bandeja e dicas de início não se aplicam; relatar um problema é o --diagnostics.
            SettingsTab.SYSTEM -> listOf("config set system.autostart true", "config set updates.auto true", "config set updates.beta false", "update", "service install")
            SettingsTab.ALERTS -> listOf(
                "config list alerts", "config set alerts.quota.thresholds 75,90", "config set budget.monthly_usd 200",
                "telegram setup", "telegram pair", "telegram test", "telegram status", "telegram readonly @ed on", "telegram remove @ed",
                "config set telegram.daily_summary_hour 8", "alerts"
            )
            SettingsTab.APIS -> listOf("sources list", "sources enable minimax", "keys set minimax", "keys test minimax", "keys remove minimax")
            // Cor e emoji por conta só aparecem no app.
            SettingsTab.ACCOUNTS -> listOf(
                "accounts list", "accounts add anthropic /srv/claude", "accounts add codex /srv/codex", "accounts rename trabalho cliente",
                "accounts enable trabalho", "accounts disable trabalho", "accounts remove trabalho", "accounts rescan"
            )
            // O modo administrador (chaves das contas) continua no app.
            SettingsTab.TEAM -> listOf("team join --server https://t.example --alias edi", "team status", "team leave")
            SettingsTab.NETWORK -> listOf("config set proxy.host proxy.local", "config set proxy.password", "web enable", "web status", "web token --show", "web disable")
        }

    @Test
    fun `every settings tab has a path in the terminal, and every path is a command`() {
        for (tab in SettingsTab.entries) {
            val commands = commandsOf(tab)
            assertTrue(commands.isNotEmpty(), "$tab sem caminho no terminal")
            for (line in commands) {
                val request = parseCommandLine(line.split(' ').toTypedArray())
                assertTrue(request is CommandLineRequest.Terminal, "$tab: '$line' não é comando: $request")
            }
        }
    }

    /** Cada ajuste do `config` mora numa aba das Configurações (e a 4.4 o cita pelo prefixo). */
    @Test
    fun `every config setting belongs to a settings tab`() {
        val tabOf = ConfigKeys.ALL.associate { key ->
            key.name to when (key.name.substringBefore('.')) {
                "alerts", "budget", "telegram" -> SettingsTab.ALERTS
                "language" -> SettingsTab.APPEARANCE
                "updates", "system" -> SettingsTab.SYSTEM
                "proxy" -> SettingsTab.NETWORK
                else -> null
            }
        }
        assertEquals(emptyList(), tabOf.filterValues { tab -> tab == null }.keys.toList())
    }
}
