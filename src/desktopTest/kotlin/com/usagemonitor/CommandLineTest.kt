package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `the warning names the option`() {
        assertEquals(
            "Warning: '--diagnostic' is not a known option and was ignored.",
            unknownOptionWarning("--diagnostic")
        )
    }

    private fun launch(autostart: Boolean = false, unknown: List<String> = emptyList()) =
        CommandLineRequest.Launch(autostart = autostart, unknownOptions = unknown)
}
