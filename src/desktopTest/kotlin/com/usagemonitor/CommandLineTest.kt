package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.withoutAccents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandLineTest {

    @Test
    fun `no argument launches the app`() {
        assertEquals(launch(), parseCommandLine(emptyArray()))
    }

    @Test
    fun `diagnostics is recognized only as a whole word`() {
        assertEquals(CommandLineRequest.Diagnostics(), parseCommandLine(arrayOf("--diagnostics")))
        assertEquals(CommandLineRequest.Diagnostics(), parseCommandLine(arrayOf(" --diagnostics ")))
        assertEquals(launch(unknown = listOf("--diagnostics-x")), parseCommandLine(arrayOf("--diagnostics-x")))
    }

    @Test
    fun `diagnostics wins over the auto start flag`() {
        assertEquals(CommandLineRequest.Diagnostics(), parseCommandLine(arrayOf("--autostart", "--diagnostics")))
    }

    @Test
    fun `auto start is a known option, not a warning`() {
        assertEquals(launch(autostart = true), parseCommandLine(arrayOf("--autostart")))
        assertEquals(launch(autostart = true), parseCommandLine(arrayOf(" --autostart ")))
    }

    @Test
    fun `unknown options are kept in order and do not stop the launch`() {
        assertEquals(
            launch(autostart = true, unknown = listOf("--autostart-ish", "file.txt")),
            parseCommandLine(arrayOf("--autostart-ish", "--autostart", "file.txt"))
        )
    }

    @Test
    fun `blank arguments are ignored`() {
        assertEquals(launch(), parseCommandLine(arrayOf("", "  ")))
    }

    @Test
    fun `help and version have a long and a short form`() {
        assertEquals(CommandLineRequest.Help(), parseCommandLine(arrayOf("--help")))
        assertEquals(CommandLineRequest.Help(), parseCommandLine(arrayOf("-h")))
        assertEquals(CommandLineRequest.Version, parseCommandLine(arrayOf("--version")))
        assertEquals(CommandLineRequest.Version, parseCommandLine(arrayOf("-v")))
    }

    @Test
    fun `reading options win over actions, help first`() {
        assertEquals(CommandLineRequest.Help(), parseCommandLine(arrayOf("--diagnostics", "--version", "-h")))
        assertEquals(CommandLineRequest.Version, parseCommandLine(arrayOf("--diagnostics", "-v", "--bogus")))
    }

    @Test
    fun `the warning names the option and points at the help`() {
        assertEquals(
            "Warning: '--diagnostic' is not a known option and was ignored. Run with --help to list the options.",
            unknownOptionWarning("--diagnostic")
        )
    }

    @Test
    fun `the version line carries the app version`() {
        assertEquals("Usage Monitor 1.2.3-beta.4", commandLineVersion("1.2.3-beta.4"))
    }

    @Test
    fun `the help lists every public option and command in both languages and hides the auto start mark`() {
        for (language in AppLanguage.entries) {
            val help = commandLineHelp(language, "9.9.9")
            assertTrue(help.startsWith("Usage Monitor 9.9.9\n"), help)
            val expected = listOf("--help", "-h", "--version", "-v", DiagnosticsExport.ARGUMENT, "--locale <pt|en>", "--user-data-dir <dir>") +
                listOf("status [--json]", "check [--max <n>]", "wait <", "--below <n>", "statusline <claude-code|tmux>")
            for (option in expected) {
                assertTrue(help.contains(option), "help $language sem $option")
            }
            assertFalse(help.contains(StartupOrigin.AUTO_START_ARGUMENT), help)
        }
        assertTrue(commandLineHelp(AppLanguage.PT).contains("Comandos"))
        assertTrue(commandLineHelp(AppLanguage.EN).contains("Commands"))
    }

    @Test
    fun `the help fits an 80 column terminal`() {
        for (language in AppLanguage.entries) {
            for (line in commandLineHelp(language).lines()) {
                assertTrue(line.length <= 80, "help $language: '$line' tem ${line.length} colunas")
            }
        }
    }

    @Test
    fun `the console texts are ASCII once the accents are dropped`() {
        // O console do Windows decodifica com a página de código dele: acento sai corrompido.
        // Sem UTF-8, o `TerminalConsole` escreve o texto sem acento; o que sobra tem de ser ASCII.
        val texts = AppLanguage.entries.map { language -> withoutAccents(commandLineHelp(language)) } +
            listOf(commandLineVersion(), unknownOptionWarning("--x"), commandLineHelp(AppLanguage.EN))
        for (text in texts) {
            assertTrue(text.all { char -> char.code in 0x0A..0x7E }, text)
        }
    }

    @Test
    fun `locale takes its value after a space or an equals sign`() {
        assertEquals(launch(locale = AppLanguage.EN), parseCommandLine(arrayOf("--locale", "en")))
        assertEquals(launch(locale = AppLanguage.PT), parseCommandLine(arrayOf("--locale=pt")))
        assertEquals(
            launch(autostart = true, locale = AppLanguage.EN),
            parseCommandLine(arrayOf("--autostart", "--locale", "EN"))
        )
    }

    @Test
    fun `locale accepts a region and drops it`() {
        assertEquals(launch(locale = AppLanguage.PT), parseCommandLine(arrayOf("--locale", "pt-BR")))
        assertEquals(launch(locale = AppLanguage.EN), parseCommandLine(arrayOf("--locale=en_US")))
    }

    @Test
    fun `the last locale wins`() {
        assertEquals(launch(locale = AppLanguage.PT), parseCommandLine(arrayOf("--locale", "en", "--locale", "pt")))
    }

    @Test
    fun `an unsupported locale is an error, not a launch`() {
        assertEquals(
            CommandLineRequest.Invalid("Error: 'zh-TW' is not a supported --locale. Use pt or en."),
            parseCommandLine(arrayOf("--locale", "zh-TW"))
        )
    }

    @Test
    fun `a locale without value is an error and does not eat the next option`() {
        val missing = CommandLineRequest.Invalid("Error: --locale needs a value: pt or en.")
        assertEquals(missing, parseCommandLine(arrayOf("--locale")))
        assertEquals(missing, parseCommandLine(arrayOf("--locale=")))
        assertEquals(missing, parseCommandLine(arrayOf("--locale", "--diagnostics")))
    }

    @Test
    fun `help and version still win over a bad locale`() {
        assertEquals(CommandLineRequest.Help(), parseCommandLine(arrayOf("--locale", "xx", "--help")))
        assertEquals(CommandLineRequest.Version, parseCommandLine(arrayOf("--locale", "-v")))
    }

    @Test
    fun `a bad locale stops the diagnostics too`() {
        assertEquals(
            CommandLineRequest.Invalid("Error: 'xx' is not a supported --locale. Use pt or en."),
            parseCommandLine(arrayOf("--diagnostics", "--locale", "xx"))
        )
    }

    @Test
    fun `the locale error is ASCII`() {
        val error = parseCommandLine(arrayOf("--locale", "xx")) as CommandLineRequest.Invalid
        assertTrue(error.message.all { char -> char.code in 0x20..0x7E }, error.message)
    }

    @Test
    fun `user data dir takes its value after a space or an equals sign`() {
        assertEquals(launch(userDataDir = "/dados"), parseCommandLine(arrayOf("--user-data-dir", "/dados")))
        assertEquals(launch(userDataDir = "dados=a"), parseCommandLine(arrayOf("--user-data-dir=dados=a")))
        assertEquals(
            launch(locale = AppLanguage.EN, userDataDir = "teste"),
            parseCommandLine(arrayOf("--user-data-dir", "teste", "--locale", "en"))
        )
    }

    @Test
    fun `user data dir without a folder is an error`() {
        val missing = CommandLineRequest.Invalid("Error: --user-data-dir needs a folder.")
        assertEquals(missing, parseCommandLine(arrayOf("--user-data-dir")))
        assertEquals(missing, parseCommandLine(arrayOf("--user-data-dir=")))
        assertEquals(missing, parseCommandLine(arrayOf("--user-data-dir", "--locale", "en")))
    }

    @Test
    fun `diagnostics carries the user data dir`() {
        assertEquals(
            CommandLineRequest.Diagnostics(userDataDir = "teste"),
            parseCommandLine(arrayOf("--diagnostics", "--user-data-dir", "teste"))
        )
    }

    @Test
    fun `the relaunch arguments carry the absolute root in place of the typed one`() {
        assertEquals(
            listOf("--locale", "en", "--user-data-dir", "/abs/dados"),
            withUserDataDir(arrayOf("--user-data-dir", "dados", "--locale", "en"), "/abs/dados")
        )
        assertEquals(
            listOf("--bogus", "--user-data-dir", "/abs/dados"),
            withUserDataDir(arrayOf("--user-data-dir=dados", "--bogus"), "/abs/dados")
        )
    }

    @Test
    fun `the relaunch arguments drop the option for the default root`() {
        assertEquals(listOf("--locale", "pt"), withUserDataDir(arrayOf("--user-data-dir", "x", "--locale", "pt"), null))
        assertEquals(listOf("--bogus"), withUserDataDir(arrayOf(" ", "--bogus"), null))
    }

    private fun launch(
        autostart: Boolean = false,
        unknown: List<String> = emptyList(),
        locale: AppLanguage? = null,
        userDataDir: String? = null
    ) = CommandLineRequest.Launch(autostart = autostart, unknownOptions = unknown, locale = locale, userDataDir = userDataDir)
}
