package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.parseCommandLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TerminalCommandLineTest {

    private fun parse(vararg arguments: String) = parseCommandLine(arrayOf(*arguments))

    @Test
    fun `status is a terminal command`() {
        assertEquals(CommandLineRequest.Terminal(TerminalCommand.Status()), parse("status"))
    }

    @Test
    fun `status accepts max age in both forms`() {
        assertEquals(CommandLineRequest.Terminal(TerminalCommand.Status(10.minutes)), parse("status", "--max-age", "10m"))
        assertEquals(CommandLineRequest.Terminal(TerminalCommand.Status(90.seconds)), parse("status", "--max-age=90s"))
    }

    @Test
    fun `global options work before and after the command`() {
        assertEquals(CommandLineRequest.Terminal(TerminalCommand.Status(), locale = AppLanguage.EN), parse("--locale", "en", "status"))
        assertEquals(
            CommandLineRequest.Terminal(TerminalCommand.Status(), userDataDir = "/tmp/um"),
            parse("status", "--user-data-dir", "/tmp/um")
        )
    }

    @Test
    fun `help and version still win over a command`() {
        assertEquals(CommandLineRequest.Help, parse("status", "--help"))
        assertEquals(CommandLineRequest.Version, parse("-v", "status"))
    }

    @Test
    fun `an unknown option with a command is an error, not a warning`() {
        assertTrue(parse("status", "--bogus") is CommandLineRequest.Invalid)
        assertTrue(parse("--bogus", "status") is CommandLineRequest.Invalid)
        assertTrue(parse("status", "extra") is CommandLineRequest.Invalid)
    }

    @Test
    fun `diagnostics cannot be combined with a command`() {
        assertTrue(parse("status", "--diagnostics") is CommandLineRequest.Invalid)
    }

    @Test
    fun `max age without a valid time is an error`() {
        assertTrue(parse("status", "--max-age") is CommandLineRequest.Invalid)
        assertTrue(parse("status", "--max-age", "soon") is CommandLineRequest.Invalid)
        assertTrue(parse("status", "--max-age=0") is CommandLineRequest.Invalid)
    }

    @Test
    fun `a loose word that is not a command still only warns and opens the app`() {
        val request = parse("statuss")
        assertTrue(request is CommandLineRequest.Launch)
        assertEquals(listOf("statuss"), request.unknownOptions)
    }

    @Test
    fun `durations accept seconds, minutes, hours and a bare number of seconds`() {
        assertEquals(90.seconds, parseTerminalDuration("90s"))
        assertEquals(10.minutes, parseTerminalDuration("10M"))
        assertEquals(2.hours, parseTerminalDuration("2h"))
        assertEquals(600.seconds, parseTerminalDuration("600"))
        assertNull(parseTerminalDuration("-5m"))
        assertNull(parseTerminalDuration("m"))
        assertNull(parseTerminalDuration("1.5h"))
    }
}
