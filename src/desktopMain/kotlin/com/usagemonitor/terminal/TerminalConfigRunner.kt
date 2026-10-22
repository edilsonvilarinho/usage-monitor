package com.usagemonitor.terminal

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.ConfigChange
import com.usagemonitor.ConfigOutcome
import com.usagemonitor.ConfigRefusals
import com.usagemonitor.ENABLED_APIS_KEY
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.requiresLocalApiKey
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalConfigLabels
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalLine
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TerminalSourceRow
import com.usagemonitor.readApiSourceCollection
import java.io.File
import java.util.prefs.Preferences

/**
 * Executa os comandos de configurar pelo terminal (#440). Ler é direto, dos mesmos
 * arquivos e preferências do app; mudar passa pelo [TerminalConfigClient], que entrega
 * ao coletor vivo ou aplica sem ele.
 */
internal class TerminalConfigRunner(
    private val client: TerminalConfigClient = TerminalConfigClient(),
    private val reader: TerminalReader = TerminalReader(),
    private val preferences: Preferences = Preferences.userRoot().node(AppDataDirectory.preferencesNodeName),
    private val dataFile: (String) -> File = AppDataDirectory::file
) {

    fun run(command: ConfigCommand, languageOverride: AppLanguage?, console: TerminalConsole): Int {
        val labels = TerminalConfigLabels(reader.language(languageOverride))
        val output = when (command) {
            is SourcesCommand -> sources(command, labels, console.profile)
        }
        if (output.lines.isNotEmpty()) {
            console.print(TerminalAnsi.render(output.lines, console.profile))
        }
        return output.exitCode
    }

    private fun sources(command: SourcesCommand, labels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput =
        when (command) {
            SourcesCommand.List -> TerminalConfigView.sources(sourceRows(), labels, profile)
            is SourcesCommand.Switch -> {
                val name = command.source.displayName()
                delivered(client.apply(ConfigChange.SourceEnabled(command.source.name, command.enabled)), labels, profile, labels.sourceSwitched(name, command.enabled)) { refused ->
                    if (refused.code == ConfigRefusals.KEY_REQUIRED) {
                        TerminalConfigView.warning(labels.keyRequired(name), "usage-monitor keys set ${command.source.terminalId()}", profile.glyphs)
                    } else {
                        null
                    }
                }
            }
        }

    /** O estado de cada fonte como o coletor o vê no arranque: ligada só com a chave, quando a pede. */
    private fun sourceRows(): List<TerminalSourceRow> {
        val stored = readApiSourceCollection(PreferencesSettings(preferences), ENABLED_APIS_KEY).toSet()
        val keys = LocalApiKeyDataSource(dataFile("api-keys.json")).load()
        return ApiSource.entries.map { source ->
            val needsKey = source.requiresLocalApiKey()
            val hasKey = needsKey && keys.forSource(source) != null
            TerminalSourceRow(source, source.terminalId(), source in stored && (!needsKey || hasKey), needsKey, hasKey)
        }
    }

    /**
     * A saída de uma mudança entregue: `✓` com a consequência, a recusa que o comando
     * explica ([refusal]), ou o aviso genérico.
     */
    private fun delivered(
        delivery: ConfigDelivery,
        labels: TerminalConfigLabels,
        profile: TerminalProfile,
        doneText: String,
        refusal: (ConfigOutcome.Refused) -> List<TerminalLine>?
    ): TerminalOutput =
        when (delivery) {
            is ConfigDelivery.NotAnswered ->
                TerminalOutput(TerminalConfigView.warning(labels.notAnswered(delivery.collector), null, profile.glyphs), TerminalConfigView.EXIT_REFUSED)
            is ConfigDelivery.Answered -> when (val outcome = delivery.outcome) {
                is ConfigOutcome.Applied -> {
                    val consequence = if (outcome.restartNeeded) labels.restartNeeded(delivery.collector) else labels.appliedBy(delivery.collector)
                    TerminalOutput(listOf(TerminalConfigView.done(doneText, consequence, profile.glyphs)), TerminalConfigView.EXIT_OK)
                }
                is ConfigOutcome.Refused -> TerminalOutput(
                    refusal(outcome) ?: TerminalConfigView.warning(labels.failed(outcome.detail), null, profile.glyphs),
                    TerminalConfigView.EXIT_REFUSED
                )
            }
        }
}
