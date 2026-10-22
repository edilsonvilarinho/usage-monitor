package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.parseCommandLine
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TerminalSetupLabels
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `usage-monitor setup` (#440, 4.1) e `update`. */
class TerminalSetupCommandTest {

    /** As respostas do usuário, na ordem em que o assistente pergunta. */
    private class Answers(vararg answers: String?) : TerminalPrompt {
        private val queue = ArrayDeque(answers.toList())
        val asked = mutableListOf<String>()

        override fun line(prompt: String): String? = next(prompt)

        override fun secret(prompt: String): String? = next(prompt)

        private fun next(prompt: String): String? {
            asked += prompt
            return if (queue.isEmpty()) null else queue.removeFirst()
        }
    }

    private fun console(buffer: ByteArrayOutputStream) =
        TerminalConsole(TerminalProfile(TerminalPalette.NONE, unicode = true), PrintStream(buffer, true, "UTF-8"))

    @Test
    fun `setup and update are commands of their own`() {
        assertEquals(CommandLineRequest.Terminal(SetupCommand), parseCommandLine(arrayOf("setup")))
        assertEquals(CommandLineRequest.Terminal(UpdateCommand), parseCommandLine(arrayOf("update")))
        assertTrue(parseCommandLine(arrayOf("setup", "--now")) is CommandLineRequest.Invalid)
    }

    @Test
    fun `the wizard chains the configuration commands in five steps and offers pairing with the service up`() {
        val ran = mutableListOf<Pair<TerminalCommand, String?>>()
        val key = "sk-minimax-123"
        val answers = Answers(
            "s",          // 1/5 monitorar a conta pronta
            key, "", "", "", // 2/5 MiniMax com chave; DeepSeek, OpenCode Go e OpenRouter pulados
            "70,90",      // 3/5 limiares
            "s", "123:tok", // Telegram sim, com o token
            "",           // 4/5 idioma: fica
            "s",          // 5/5 instalar o serviço
            "s"           // parear agora
        )
        val buffer = ByteArrayOutputStream()
        val code = TerminalSetupRunner(
            prompt = answers,
            config = { command, secret -> ran += command to secret; 0 to "✓ feito\n" },
            configLive = { command -> ran += command to null; 0 },
            service = { command -> ran += command to null; 0 to "✓ serviço criado\n" },
            readyAccounts = { listOf(SetupAccount(ApiSource.ANTHROPIC, "default", monitored = false)) },
            currentThresholds = { "75,90,100" },
            currentLanguage = { "pt" },
            isLinux = true,
            dataRoot = "~/.usage-monitor",
            version = "41.12.0"
        ).run(TerminalSetupLabels(AppLanguage.PT), console(buffer))

        assertEquals(0, code)
        val commands = ran.map { (command, _) -> command }
        assertEquals(
            listOf(
                AccountsCommand.Rescan, AccountsCommand.List, AccountsCommand.Switch("default", true),
                KeysCommand.Set(ApiSource.MINIMAX),
                SettingCommand.Set("alerts.quota.thresholds", "70,90"),
                TelegramCommand.Setup,
                ServiceCommand.Install,
                TelegramCommand.Pair()
            ),
            commands
        )
        assertEquals(key, ran.single { (command, _) -> command == KeysCommand.Set(ApiSource.MINIMAX) }.second)
        assertEquals("123:tok", ran.single { (command, _) -> command == TelegramCommand.Setup }.second)
        val output = buffer.toString(Charsets.UTF_8).replace("\r\n", "\n")
        assertTrue(output.startsWith("Usage Monitor 41.12.0 · configuração inicial · dados em ~/.usage-monitor\n"), output)
        assertTrue(output.contains("1/5  Contas encontradas neste computador") && output.contains("5/5  Segundo plano"), output)
        assertTrue(output.contains("– DeepSeek pulada"), output)
        assertTrue(output.contains("Pronto. Veja com usage-monitor status."), output)
        assertFalse(output.contains(key))
    }

    @Test
    fun `outside Linux the wizard does not offer the service`() {
        val ran = mutableListOf<TerminalCommand>()
        TerminalSetupRunner(
            prompt = Answers(),
            config = { command, _ -> ran += command; 0 to "" },
            configLive = { command -> ran += command; 0 },
            service = { command -> ran += command; 0 to "" },
            readyAccounts = { emptyList() },
            currentThresholds = { "75,90,100" },
            currentLanguage = { "pt" },
            isLinux = false,
            dataRoot = "C:\\Users\\dev\\.usage-monitor",
            version = "41.12.0"
        ).run(TerminalSetupLabels(AppLanguage.PT), console(ByteArrayOutputStream()))

        assertTrue(ServiceCommand.Install !in ran)
    }

    @Test
    fun `update says whether there is a new version and how to get it on this install`() {
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        val dir = Files.createTempDirectory("usage-monitor-update").toFile()
        try {
            val reader = TerminalReader(File(dir, "dashboard-cache.json"), File(dir, "app.lock"), node)
            val latest = AppUpdateInfo(version = "41.13.0", releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v41.13.0")

            val upToDate = ByteArrayOutputStream()
            assertEquals(0, TerminalUpdateCommand({ Result.success(null) }, node, reader, null).run(AppLanguage.PT, console(upToDate)))
            assertTrue(upToDate.toString(Charsets.UTF_8).replace("\r\n", "\n").startsWith("✓ "), upToDate.toString(Charsets.UTF_8).replace("\r\n", "\n"))

            val script = ByteArrayOutputStream()
            TerminalUpdateCommand({ Result.success(latest) }, node, reader, "/home/dev/.local/share/usage-monitor/versions/41.12.0/Usage Monitor/bin/Usage Monitor")
                .run(AppLanguage.PT, console(script))
            assertTrue(script.toString(Charsets.UTF_8).replace("\r\n", "\n").contains("install-usage-monitor_41.13.0_linux_x64.sh | sh"), script.toString(Charsets.UTF_8).replace("\r\n", "\n"))

            val offline = ByteArrayOutputStream()
            assertEquals(1, TerminalUpdateCommand({ Result.failure(IllegalStateException("sem rede")) }, node, reader, null).run(AppLanguage.PT, console(offline)))
        } finally {
            runCatching {
                node.removeNode()
                node.flush()
            }
            dir.deleteRecursively()
        }
    }
}
