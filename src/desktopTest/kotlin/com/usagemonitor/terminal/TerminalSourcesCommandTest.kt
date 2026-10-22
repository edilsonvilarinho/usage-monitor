package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.ENABLED_APIS_KEY
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.parseCommandLine
import com.usagemonitor.readApiSourceCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `usage-monitor sources …` (#440, aba APIs das Configurações). */
class TerminalSourcesCommandTest {

    @Test
    fun `sources parses the actions and rejects an unknown source`() {
        assertEquals(CommandLineRequest.Terminal(SourcesCommand.List), parseCommandLine(arrayOf("sources", "list")))
        assertEquals(CommandLineRequest.Terminal(SourcesCommand.Switch(ApiSource.OPENCODE_GO, true)), parseCommandLine(arrayOf("sources", "enable", "opencode_go")))
        assertEquals(CommandLineRequest.Terminal(SourcesCommand.Switch(ApiSource.CODEX, false)), parseCommandLine(arrayOf("sources", "disable", "CODEX")))
        for (arguments in listOf(arrayOf("sources"), arrayOf("sources", "enable"), arrayOf("sources", "enable", "nope"), arrayOf("sources", "list", "x"))) {
            assertTrue(parseCommandLine(arguments) is CommandLineRequest.Invalid, arguments.joinToString(" "))
        }
    }

    @Test
    fun `without a collector the change is written and applies on the next start`() = TerminalConfigHarness().use { harness ->
        val (code, output) = harness.run("sources", "enable", "codex")

        assertEquals(0, code)
        assertEquals("✓ Codex ligada · vale quando o app ou o serviço abrir\n", output)
        assertEquals(listOf(ApiSource.CODEX), readApiSourceCollection(harness.store().settings, ENABLED_APIS_KEY))
    }

    @Test
    fun `with the service collecting, the service applies it`() = TerminalConfigHarness(collector = CollectorKind.SERVICE).use { harness ->
        val (code, output) = harness.run("sources", "enable", "anthropic")

        assertEquals(0, code)
        assertEquals("✓ Anthropic ligada · o serviço já está usando\n", output)
    }

    @Test
    fun `a source that needs a key says how to save it`() = TerminalConfigHarness().use { harness ->
        val (code, output) = harness.run("sources", "enable", "minimax")

        assertEquals(1, code)
        assertEquals("▲ MiniMax precisa de uma chave antes de ligar. Salve com:\n    usage-monitor keys set minimax\n", output)
    }

    @Test
    fun `sources list shows every source in the app order, with its state and key`() = TerminalConfigHarness().use { harness ->
        harness.run("sources", "enable", "codex")

        val (code, output) = harness.run("sources", "list")

        assertEquals(0, code)
        val lines = output.lines()
        assertEquals(" FONTE", lines.first().substring(0, 6))
        assertEquals(ApiSource.entries.size + 2, lines.size)
        assertTrue(lines.any { line -> line.contains("Codex") && line.contains("codex") && line.contains("ligada") })
        assertTrue(lines.any { line -> line.contains("MiniMax") && line.contains("desligada") && line.contains("sem chave") })
    }
}
