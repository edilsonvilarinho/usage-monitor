package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.parseCommandLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalServiceCommandLineTest {

    private fun parse(vararg arguments: String) = parseCommandLine(arrayOf(*arguments))

    @Test
    fun `service run is the headless collector, with the global options around it`() {
        assertEquals(CommandLineRequest.Terminal(ServiceCommand.Run), parse("service", "run"))
        val request = parse("--locale", "en", "service", "run", "--user-data-dir", "/srv/um") as CommandLineRequest.Terminal
        assertEquals(ServiceCommand.Run, request.command)
        assertEquals(AppLanguage.EN, request.locale)
        assertEquals("/srv/um", request.userDataDir)
    }

    @Test
    fun `service without an action, with an unknown action or with an extra option is a usage error`() {
        for (arguments in listOf(arrayOf("service"), arrayOf("service", "fly"), arrayOf("service", "run", "--now"))) {
            val request = parseCommandLine(arguments)
            assertTrue(request is CommandLineRequest.Invalid, arguments.joinToString(" "))
            assertTrue(request.message.all { char -> char.code < 128 })
        }
    }

    @Test
    fun `every action maps to its command`() {
        assertEquals(CommandLineRequest.Terminal(ServiceCommand.Install), parse("service", "install"))
        assertEquals(CommandLineRequest.Terminal(ServiceCommand.Uninstall), parse("service", "uninstall"))
        assertEquals(CommandLineRequest.Terminal(ServiceCommand.Status), parse("service", "status"))
        for (action in listOf("start", "stop", "restart")) {
            assertEquals(CommandLineRequest.Terminal(ServiceCommand.Control(action)), parse("service", action))
        }
    }

    @Test
    fun `logs takes follow in both spellings and a line count from 1 to 10000`() {
        assertEquals(CommandLineRequest.Terminal(ServiceCommand.Logs(follow = false, lines = 50)), parse("service", "logs"))
        assertEquals(CommandLineRequest.Terminal(ServiceCommand.Logs(follow = true, lines = 50)), parse("service", "logs", "-f"))
        assertEquals(
            CommandLineRequest.Terminal(ServiceCommand.Logs(follow = true, lines = 200)),
            parse("service", "logs", "--lines", "200", "--follow")
        )
        for (lines in listOf("0", "10001", "muitas")) {
            val request = parse("service", "logs", "--lines", lines)
            assertEquals(CommandLineRequest.Invalid("Error: --lines needs a number from 1 to 10000."), request, lines)
        }
        assertTrue(parse("service", "logs", "--lines") is CommandLineRequest.Invalid)
        assertTrue(parse("service", "logs", "--tail") is CommandLineRequest.Invalid)
    }
}
