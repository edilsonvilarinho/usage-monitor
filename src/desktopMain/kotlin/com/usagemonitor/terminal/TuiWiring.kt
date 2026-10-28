package com.usagemonitor.terminal

import com.usagemonitor.buildHttpClient
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.datasource.LocalCollectorHealthDataSource
import com.usagemonitor.data.datasource.LocalProxySettingsDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.data.datasource.LocalWebAccessSettingsDataSource
import com.usagemonitor.AnthropicProfileRegistry
import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.domain.entity.AppLanguage
import io.ktor.client.HttpClient
import java.io.File
import java.util.prefs.Preferences
import com.usagemonitor.presentation.terminal.TuiLabels
import com.usagemonitor.presentation.terminal.TuiSettingAction
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
            val team = TuiTeamWiring()
            try {
                TuiStores().use { stores ->
                    val tuiReader = TuiReader(
                        terminalReader, stores, team.reader, languageOverride = languageOverride,
                        settings = TuiSettingsReader(tuiSettingsInputs()), glyphs = profile.glyphs
                    )
                    // O segredo digitado na aba 5 entra como a entrada do comando, e só dele.
                    val apply = { action: TuiSettingAction ->
                        TuiSettingsCommands.apply(action, { secret -> TerminalConfigRunner(secretInput = { _ -> secret }) }, languageOverride, profile)
                    }
                    return TuiRunner(io, tuiReader::load, tuiReader::exportSessions, apply, labels, profile).run()
                }
            } finally {
                team.close()
            }
        } finally {
            io.close()
            runCatching { Runtime.getRuntime().removeShutdownHook(restore) }
        }
    }
}

/** O que a aba 5 lê: as mesmas leituras dos comandos `sources`, `accounts` e `config list`. */
internal fun tuiSettingsInputs(): TuiSettingsInputs {
    val runner = TerminalConfigRunner()
    val preferences = Preferences.userRoot().node(AppDataDirectory.preferencesNodeName)
    return TuiSettingsInputs(
        sources = runner::sourceRows,
        accounts = { runner.accountsRunner().rows() },
        values = runner::readSource,
        telegram = { LocalTelegramSettingsDataSource().load() },
        team = { LocalTeamSettingsDataSource().peek() },
        teamAccounts = { settings -> tuiTeamAccountLabels(AnthropicProfileRegistry.readStored(preferences), settings) },
        web = { LocalWebAccessSettingsDataSource().load() },
        autostartAvailable = AppDataDirectory.isDefault
    )
}

/**
 * O cliente do time, montado só quando a aba 4 lê pela primeira vez: quem não usa time não
 * paga o `HttpClient`. Mesmo proxy efetivo do app (`resolveEffectiveProxy`).
 */
internal class TuiTeamWiring(
    private val settingsSource: LocalTeamSettingsDataSource = LocalTeamSettingsDataSource(),
    private val health: LocalCollectorHealthDataSource = LocalCollectorHealthDataSource()
) : AutoCloseable {
    private var httpClient: HttpClient? = null
    private val preferences = Preferences.userRoot().node(AppDataDirectory.preferencesNodeName)
    private val home = File(System.getProperty("user.home").orEmpty())

    val reader = TuiTeamReader(
        settings = { settingsSource.peek() },
        accounts = { TuiTeamReader.participating(preferences, home, settingsSource.peek()) },
        repository = { settings -> TeamUsageRepositoryImpl(RemoteTeamDataSource(client()), settingsProvider = { settings }) },
        clockOffsetMillis = { health.read()?.team?.clockOffsetMillis ?: 0L }
    )

    private fun client(): HttpClient =
        httpClient ?: buildHttpClient(resolveEffectiveProxy(LocalProxySettingsDataSource().load())).also { created -> httpClient = created }

    override fun close() {
        runCatching { httpClient?.close() }
    }
}
