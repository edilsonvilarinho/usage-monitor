package com.usagemonitor

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
        assertEquals(CommandLineRequest.Diagnostics, parseCommandLine(arrayOf("--diagnostics")))
        assertEquals(CommandLineRequest.Diagnostics, parseCommandLine(arrayOf(" --diagnostics ")))
        assertEquals(launch(unknown = listOf("--diagnostics-x")), parseCommandLine(arrayOf("--diagnostics-x")))
    }

    @Test
    fun `diagnostics wins over the auto start flag`() {
        assertEquals(CommandLineRequest.Diagnostics, parseCommandLine(arrayOf("--autostart", "--diagnostics")))
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
        assertEquals(CommandLineRequest.Help, parseCommandLine(arrayOf("--help")))
        assertEquals(CommandLineRequest.Help, parseCommandLine(arrayOf("-h")))
        assertEquals(CommandLineRequest.Version, parseCommandLine(arrayOf("--version")))
        assertEquals(CommandLineRequest.Version, parseCommandLine(arrayOf("-v")))
    }

    @Test
    fun `reading options win over actions, help first`() {
        assertEquals(CommandLineRequest.Help, parseCommandLine(arrayOf("--diagnostics", "--version", "-h")))
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
    fun `the help lists every public option and hides the auto start mark`() {
        val help = commandLineHelp("9.9.9")
        assertTrue(help.startsWith("Usage Monitor 9.9.9\n"), help)
        for (option in listOf("--help", "-h", "--version", "-v", DiagnosticsExport.ARGUMENT)) {
            assertTrue(help.contains(option), "help sem $option")
        }
        assertFalse(help.contains(StartupOrigin.AUTO_START_ARGUMENT), help)
    }

    @Test
    fun `the console texts are ASCII`() {
        // O console do Windows decodifica com a página de código dele: acento sai corrompido.
        for (text in listOf(commandLineHelp(), commandLineVersion(), unknownOptionWarning("--x"))) {
            assertTrue(text.all { char -> char.code in 0x0A..0x7E }, text)
        }
    }

    private fun launch(autostart: Boolean = false, unknown: List<String> = emptyList()) =
        CommandLineRequest.Launch(autostart = autostart, unknownOptions = unknown)
}
