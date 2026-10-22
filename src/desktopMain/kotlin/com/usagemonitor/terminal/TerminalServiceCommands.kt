package com.usagemonitor.terminal

import com.usagemonitor.HeadlessService
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TerminalServiceLabels

/** Executa `usage-monitor service …` (#440) e devolve o código de saída. */
internal object TerminalServiceCommands {

    fun run(command: ServiceCommand, languageOverride: AppLanguage?, console: TerminalConsole): Int {
        val labels = TerminalServiceLabels(TerminalReader().language(languageOverride))
        return when (command) {
            ServiceCommand.Run -> HeadlessService.run(languageOverride, labels, console::printText)
        }
    }
}
