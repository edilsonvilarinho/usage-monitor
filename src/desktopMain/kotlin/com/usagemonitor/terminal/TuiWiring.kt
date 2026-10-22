package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TuiLabels
import com.usagemonitor.presentation.terminal.detectTerminalProfile

/** A montagem do `tui` com o terminal de verdade. */
internal object TuiWiring {

    fun run(languageOverride: AppLanguage?, console: TerminalConsole): Int {
        val terminalReader = TerminalReader()
        val labels = TuiLabels(terminalReader.language(languageOverride))
        val io = JLineTuiIo.open()
        if (io == null) {
            console.printText(labels.needsTerminal)
            return TuiRunner.EXIT_NO_TERMINAL
        }
        // A tela alternativa e o modo cru voltam mesmo num SIGTERM: o terminal nunca fica preso.
        val restore = Thread { io.close() }
        Runtime.getRuntime().addShutdownHook(restore)
        try {
            val profile = detectTerminalProfile(System.getenv(), outputIsTerminal = true, outputEncoding = io.encoding)
            TuiStores().use { stores ->
                val tuiReader = TuiReader(terminalReader, stores, languageOverride = languageOverride)
                return TuiRunner(io, tuiReader::load, tuiReader::exportSessions, labels, profile).run()
            }
        } finally {
            io.close()
            runCatching { Runtime.getRuntime().removeShutdownHook(restore) }
        }
    }
}
