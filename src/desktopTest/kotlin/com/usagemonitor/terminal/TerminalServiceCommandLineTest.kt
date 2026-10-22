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
}
