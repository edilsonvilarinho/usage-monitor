package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.ENABLED_APIS_KEY
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.domain.entity.RateLimitedException
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.parseCommandLine
import com.usagemonitor.readApiSourceCollection
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `usage-monitor keys …` (#440, aba APIs; a chave só pela entrada, D5). */
class TerminalKeysCommandTest {

    private val key = "sk-test-0123456789abcdef"

    private fun TerminalConfigHarness.savedKey(source: ApiSource): String? = LocalApiKeyDataSource(file("api-keys.json")).load().forSource(source)

    @Test
    fun `keys parses the actions, only for sources with a key, and never takes the key as an argument`() {
        assertEquals(CommandLineRequest.Terminal(KeysCommand.Set(ApiSource.MINIMAX)), parseCommandLine(arrayOf("keys", "set", "minimax")))
        assertEquals(CommandLineRequest.Terminal(KeysCommand.Set(ApiSource.DEEPSEEK, skipTest = true)), parseCommandLine(arrayOf("keys", "set", "deepseek", "--skip-test")))
        assertEquals(CommandLineRequest.Terminal(KeysCommand.Remove(ApiSource.OPENROUTER)), parseCommandLine(arrayOf("keys", "remove", "openrouter")))
        for (arguments in listOf(arrayOf("keys", "set", "anthropic"), arrayOf("keys", "set", "minimax", key), arrayOf("keys", "test"), arrayOf("keys", "list"))) {
            val request = parseCommandLine(arguments)
            assertTrue(request is CommandLineRequest.Invalid, arguments.joinToString(" "))
            assertFalse(request.message.contains(key))
        }
    }

    @Test
    fun `a key that passes the test is saved and turns the source on`() = TerminalConfigHarness().use { harness ->
        val (code, output) = harness.run("keys", "set", "minimax", secret = key)

        assertEquals(0, code)
        assertEquals("✓ chave do MiniMax salva e a fonte ligada · vale quando o app ou o serviço abrir\n  Chave válida.\n", output)
        assertEquals(key, harness.savedKey(ApiSource.MINIMAX))
        assertEquals(listOf(ApiSource.MINIMAX), readApiSourceCollection(harness.store().settings, ENABLED_APIS_KEY))
        assertFalse(output.contains(key))
    }

    @Test
    fun `a key that fails the test is not saved, and the output says how to skip the test`() =
        TerminalConfigHarness(keyProbe = { _, _ -> UnknownHostException("api.minimax.io") }).use { harness ->
            val (code, output) = harness.run("keys", "set", "minimax", secret = key)

            assertEquals(1, code)
            assertTrue(output.startsWith("▲ A chave não passou no teste, e nada mudou."), output)
            assertTrue(output.contains("usage-monitor keys set minimax --skip-test"))
            assertNull(harness.savedKey(ApiSource.MINIMAX))
        }

    @Test
    fun `a test with no verdict saves with the caveat, and skip test saves without one`() =
        TerminalConfigHarness(collector = CollectorKind.SERVICE, keyProbe = { _, _ -> RateLimitedException("HTTP 429 Too Many Requests") }).use { harness ->
            val (code, output) = harness.run("keys", "set", "deepseek", secret = key)
            assertEquals(0, code)
            assertTrue(output.startsWith("✓ chave do DeepSeek salva e a fonte ligada · o serviço já está usando\n▲ Testada, com ressalva:"), output)

            val (skipped, skippedOutput) = harness.run("keys", "set", "openrouter", "--skip-test", secret = key)
            assertEquals(0, skipped)
            assertTrue(skippedOutput.endsWith("  gravada sem teste\n"), skippedOutput)
        }

    @Test
    fun `test needs a saved key, and remove turns the source off`() = TerminalConfigHarness().use { harness ->
        val (missing, missingOutput) = harness.run("keys", "test", "opencode_go")
        assertEquals(1, missing)
        assertEquals("▲ OpenCode Go não tem chave salva. Salve com:\n    usage-monitor keys set opencode_go\n", missingOutput)

        harness.run("keys", "set", "opencode_go", secret = key)
        assertEquals(0 to "✓ Chave válida. · OpenCode Go\n", harness.run("keys", "test", "opencode_go"))

        assertEquals(0 to "✓ chave do OpenCode Go removida e a fonte desligada · vale quando o app ou o serviço abrir\n", harness.run("keys", "remove", "opencode_go"))
        assertNull(harness.savedKey(ApiSource.OPENCODE_GO))
        assertEquals(emptyList(), readApiSourceCollection(harness.store().settings, ENABLED_APIS_KEY))
    }

    @Test
    fun `nothing typed changes nothing`() = TerminalConfigHarness().use { harness ->
        assertEquals(1, harness.run("keys", "set", "minimax", secret = null).first)
        assertNull(harness.savedKey(ApiSource.MINIMAX))
    }
}
