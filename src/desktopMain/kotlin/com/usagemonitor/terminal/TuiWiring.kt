package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TuiData
import com.usagemonitor.presentation.terminal.TuiLabels
import com.usagemonitor.presentation.terminal.TuiState
import com.usagemonitor.presentation.terminal.detectTerminalProfile

/**
 * O que o modo interativo lê a cada releitura (#441). Só leitura: o mesmo cache e as mesmas
 * preferências dos comandos da #439, sem requisição, sem trava e sem escrever nada.
 */
internal class TuiReader(
    private val reader: TerminalReader = TerminalReader(),
    private val teamSettings: LocalTeamSettingsDataSource = LocalTeamSettingsDataSource(),
    private val languageOverride: AppLanguage? = null
) {
    @Suppress("UNUSED_PARAMETER")
    fun load(state: TuiState): TuiData {
        val reading = reader.read(languageOverride)
        val team = runCatching { teamSettings.load() }.getOrNull()
        return TuiData(
            now = reading.now,
            collector = reading.collector,
            lastCollectedAt = reading.lastCollectedAt,
            teamConfigured = team != null && team.enabled && team.normalizedServerUrl.isNotEmpty() && team.apiKey.isNotBlank()
        )
    }
}

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
            val tuiReader = TuiReader(terminalReader, languageOverride = languageOverride)
            return TuiRunner(io, tuiReader::load, labels, profile).run()
        } finally {
            io.close()
            runCatching { Runtime.getRuntime().removeShutdownHook(restore) }
        }
    }
}
