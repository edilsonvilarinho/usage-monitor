package com.usagemonitor.terminal

import com.usagemonitor.AnthropicProfileRegistry
import com.usagemonitor.CodexProfileRegistry
import com.usagemonitor.CommandLineRequest
import com.usagemonitor.ENABLED_APIS_KEY
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.parseCommandLine
import com.usagemonitor.readApiSourceCollection
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `usage-monitor accounts …` (#440, aba Contas). */
class TerminalAccountsCommandTest {

    /** Um diretório de configuração do Claude Code já logado. */
    private fun TerminalConfigHarness.claudeDirectory(name: String): File =
        file(name).apply {
            mkdirs()
            File(this, ".credentials.json").writeText("""{"claudeAiOauth":{"accessToken":"a"}}""")
            File(this, ".claude.json").writeText("""{"oauthAccount":{"accountUuid":"u-$name","emailAddress":"$name@example.com"}}""")
        }

    /** Um `CODEX_HOME` já logado. */
    private fun TerminalConfigHarness.codexDirectory(name: String): File =
        file(name).apply {
            mkdirs()
            File(this, "auth.json").writeText("{}")
        }

    @Test
    fun `accounts parses every action and needs the directory to add`() {
        assertEquals(
            CommandLineRequest.Terminal(AccountsCommand.Add(ApiSource.CODEX, "/srv/codex-work", "trabalho")),
            parseCommandLine(arrayOf("accounts", "add", "codex", "/srv/codex-work", "--label", "trabalho"))
        )
        assertEquals(CommandLineRequest.Terminal(AccountsCommand.Rename("pessoal", "casa")), parseCommandLine(arrayOf("accounts", "rename", "pessoal", "casa")))
        assertEquals(CommandLineRequest.Terminal(AccountsCommand.Switch("casa", false)), parseCommandLine(arrayOf("accounts", "disable", "casa")))
        for (arguments in listOf(arrayOf("accounts", "add", "codex"), arrayOf("accounts", "add", "minimax", "/x"), arrayOf("accounts", "rename", "a"), arrayOf("accounts", "remove"))) {
            assertTrue(parseCommandLine(arguments) is CommandLineRequest.Invalid, arguments.joinToString(" "))
        }
    }

    @Test
    fun `adding a Claude Code account monitors it, turns the source on and lists it ready`() = TerminalConfigHarness().use { harness ->
        val work = harness.claudeDirectory("claude-work")

        val (code, output) = harness.run("accounts", "add", "anthropic", work.path, "--label", "trabalho")

        assertEquals(0, code)
        assertEquals("✓ conta Anthropic trabalho adicionada e monitorada · vale quando o app ou o serviço abrir\n", output)
        val record = AnthropicProfileRegistry.readStored(harness.node).single { stored -> stored.label == "trabalho" }
        assertTrue(record.enabled)
        assertEquals(listOf(ApiSource.ANTHROPIC), readApiSourceCollection(harness.store().settings, ENABLED_APIS_KEY))
        val listed = harness.run("accounts", "list").second.lines()
        assertTrue(listed.any { line -> line.contains("trabalho") && line.replace('\\', '/').contains("~/claude-work") && line.contains("monitorada · pronta") }, listed.joinToString("\n"))
    }

    @Test
    fun `a Codex account is renamed, switched off and removed by its label, through the service`() = TerminalConfigHarness(collector = CollectorKind.SERVICE).use { harness ->
        val home = harness.codexDirectory("codex-cliente")
        assertEquals(0, harness.run("accounts", "add", "codex", home.path).first)

        assertEquals(0 to "✓ conta renomeada para cliente · o serviço já está usando\n", harness.run("accounts", "rename", "codex-cliente", "cliente"))
        assertEquals(0 to "✓ cliente desligada · o serviço já está usando\n", harness.run("accounts", "disable", "CLIENTE"))
        assertFalse(CodexProfileRegistry.readStored(harness.node).single().enabled)
        assertEquals(0 to "✓ cliente removida da lista · o serviço já está usando\n", harness.run("accounts", "remove", "cliente"))
        assertTrue(CodexProfileRegistry.readStored(harness.node).isEmpty())
    }

    @Test
    fun `an unknown account, the default account and a directory without login are explained`() = TerminalConfigHarness().use { harness ->
        harness.claudeDirectory(".claude")

        assertEquals(1 to "▲ A conta 'nada' não existe. Veja as contas com:\n    usage-monitor accounts list\n", harness.run("accounts", "enable", "nada"))
        assertEquals(
            1 to "▲ A conta padrão do Claude Code não sai da lista. Para parar de monitorá-la:\n    usage-monitor accounts disable default\n",
            harness.run("accounts", "remove", "default")
        )
        val (code, output) = harness.run("accounts", "add", "codex", harness.file("vazio").apply { mkdirs() }.path)
        assertEquals(1, code)
        assertTrue(output.startsWith("▲ A conta não foi adicionada: Não há auth.json"), output)
    }
}
