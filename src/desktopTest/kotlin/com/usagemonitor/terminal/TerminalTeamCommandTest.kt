package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.data.datasource.LocalWebAccessSettingsDataSource
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.parseCommandLine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `usage-monitor team …` e `web …` (#440, abas Time e Rede). */
class TerminalTeamCommandTest {

    private val teamKey = "tk_segredo_do_time"

    /** Uma conta padrão do Claude Code logada na pasta pessoal do teste. */
    private fun TerminalConfigHarness.loggedIn() {
        File(dir, ".claude").mkdirs()
        File(dir, ".claude/.credentials.json").writeText("""{"claudeAiOauth":{"accessToken":"a"}}""")
        File(dir, ".claude.json").writeText("""{"oauthAccount":{"accountUuid":"u-1","emailAddress":"dev@example.com"}}""")
        // Montar o store descobre a conta, como o app faz no arranque.
        store().close()
        run("accounts", "enable", "default")
    }

    @Test
    fun `team and web parse their actions, and the key never comes as an argument`() {
        assertEquals(
            CommandLineRequest.Terminal(TeamCommand.Join("https://time.exemplo.com", "edi", listOf("pessoal", "trabalho"))),
            parseCommandLine(arrayOf("team", "join", "--server", "https://time.exemplo.com", "--alias=edi", "--accounts", "pessoal,trabalho"))
        )
        assertEquals(CommandLineRequest.Terminal(WebCommand.Switch(true, 9000)), parseCommandLine(arrayOf("web", "enable", "--port", "9000")))
        assertEquals(CommandLineRequest.Terminal(WebCommand.ShowToken), parseCommandLine(arrayOf("web", "token", "--show")))
        for (arguments in listOf(arrayOf("team", "join", teamKey), arrayOf("web", "token"), arrayOf("web", "enable", "--port", "80"))) {
            val request = parseCommandLine(arguments)
            assertTrue(request is CommandLineRequest.Invalid, arguments.joinToString(" "))
            assertFalse(request.message.contains(teamKey))
        }
    }

    @Test
    fun `joining checks the server and the account link first, then saves through the service`() {
        val checked = mutableListOf<String>()
        TerminalConfigHarness(collector = CollectorKind.SERVICE, teamCheck = { settings, accounts ->
            checked += settings.normalizedServerUrl
            accounts.map { account -> account.email.orEmpty() }.filter { email -> email != "dev@example.com" }
        }).use { harness ->
            harness.loggedIn()
            val (code, output) = harness.run("team", "join", "--server", "https://time.exemplo.com/", "--alias", "edi", secret = teamKey)

            assertEquals(0, code, output)
            assertEquals(
                "✓ no time em time.exemplo.com como edi, com 1 conta · o serviço já está usando\n  servidor e vínculo das contas conferidos\n",
                output
            )
            assertEquals(listOf("https://time.exemplo.com"), checked)
            val saved = LocalTeamSettingsDataSource(harness.file("team.json")).load()
            assertTrue(saved.isActive)
            assertEquals(setOf("default"), saved.participatingProfileIds)
            assertFalse(output.contains(teamKey))

            val status = harness.run("team", "status").second
            assertTrue(status.contains("time.exemplo.com") && status.contains("••••") && status.lines().any { line -> line.startsWith("conexão") && line.endsWith("ok") }, status)

            assertEquals(0, harness.run("team", "leave").first)
            assertFalse(LocalTeamSettingsDataSource(harness.file("team.json")).load().isActive)
            assertEquals("", LocalTeamSettingsDataSource(harness.file("team.json")).load().apiKey)
        }
    }

    @Test
    fun `a failed check saves nothing, and an unknown account is named`() =
        TerminalConfigHarness(teamCheck = { _, _ -> listOf("HTTP 401: chave recusada") }).use { harness ->
            harness.loggedIn()
            val (code, output) = harness.run("team", "join", "--server", "https://t.example", "--alias", "edi", secret = teamKey)
            assertEquals(1, code)
            assertTrue(output.startsWith("▲ A conferência com o servidor falhou, e nada mudou. HTTP 401: chave recusada"), output)
            assertTrue(output.contains("usage-monitor team join --skip-check"))
            assertEquals("", LocalTeamSettingsDataSource(harness.file("team.json")).load().apiKey)

            val (unknown, unknownOutput) = harness.run("team", "join", "--server", "https://t.example", "--alias", "edi", "--accounts", "nada", secret = teamKey)
            assertEquals(1, unknown)
            assertTrue(unknownOutput.startsWith("▲ Estas contas não existem: nada."), unknownOutput)
        }

    @Test
    fun `web enable creates the token, status hides it and token --show prints the address`() = TerminalConfigHarness().use { harness ->
        val (code, output) = harness.run("web", "enable", "--port", "8787")
        assertEquals(0, code)
        assertEquals(
            "✓ acesso pela rede ligado na porta 8787 · vale quando o app ou o serviço abrir\n  O endereço com o token sai com:\n    usage-monitor web token --show\n",
            output
        )
        val token = LocalWebAccessSettingsDataSource(harness.file("web-access.json")).load().token
        assertEquals(64, token.length)

        val status = harness.run("web", "status").second
        assertTrue(status.contains("http://192.168.0.10:8787") && status.contains("••••") && !status.contains(token), status)

        assertEquals(0 to "http://192.168.0.10:8787/?t=$token\n", harness.run("web", "token", "--show"))
        assertEquals(0, harness.run("web", "token", "--regenerate").first)
        assertFalse(LocalWebAccessSettingsDataSource(harness.file("web-access.json")).load().token == token)

        harness.run("web", "disable")
        assertEquals(1, harness.run("web", "token", "--show").first)
    }

    @Test
    fun `join asks for the server, then the alias, then the key, and saves nothing on the way`() = TerminalConfigHarness().use { harness ->
        harness.loggedIn()

        assertEquals(
            1 to "▲ Falta o endereço do servidor do time. Informe com:\n    usage-monitor team join --server https://time.exemplo.com --alias <nome>\n",
            harness.run("team", "join", secret = teamKey)
        )
        assertEquals(
            1 to "▲ Falta o seu apelido no time. Informe com:\n    usage-monitor team join --alias <nome>\n",
            harness.run("team", "join", "--server", "https://t.example", secret = teamKey)
        )
        assertEquals(
            1 to "▲ Nada foi digitado, e nada mudou.\n",
            harness.run("team", "join", "--server", "https://t.example", "--alias", "edi", secret = " ")
        )
        assertEquals("", LocalTeamSettingsDataSource(harness.file("team.json")).load().apiKey)
    }

    @Test
    fun `join without the check says it was not checked, and a second join reuses the saved server and alias`() =
        TerminalConfigHarness(teamCheck = { _, _ -> error("--skip-check não confere") }).use { harness ->
            harness.loggedIn()
            val (code, output) = harness.run("team", "join", "--server", "https://t.example", "--alias", "edi", "--skip-check", secret = teamKey)
            assertEquals(0, code, output)
            assertTrue(output.trimEnd().endsWith("entrou sem conferência"), output)

            val (again, againOutput) = harness.run("team", "join", "--skip-check", secret = "outra-chave")
            assertEquals(0, again, againOutput)
            val saved = LocalTeamSettingsDataSource(harness.file("team.json")).load()
            assertEquals("https://t.example", saved.normalizedServerUrl)
            assertEquals("edi", saved.alias)
            assertEquals("outra-chave", saved.apiKey)
        }
}
