package com.usagemonitor.terminal

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.ENABLED_APIS_KEY
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.CodexCliHomeProvider
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TerminalSetupLabels
import com.usagemonitor.readApiSourceCollection
import com.usagemonitor.readPersistedAlertSettings
import com.usagemonitor.storedLanguage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.prefs.Preferences

/** A montagem do `setup` com os comandos de verdade: o assistente só encadeia. */
internal object TerminalSetupWiring {

    /** Quanto o pareamento espera o serviço recém-instalado tomar a trava. */
    private const val SERVICE_START_WAIT_MILLIS = 20_000L
    private const val SERVICE_START_POLL_MILLIS = 500L

    fun run(languageOverride: AppLanguage?, console: TerminalConsole): Int {
        val preferences = Preferences.userRoot().node(AppDataDirectory.preferencesNodeName)
        val reader = TerminalReader()
        val home = File(System.getProperty("user.home").orEmpty())
        fun captured(block: (TerminalConsole) -> Int): Pair<Int, String> {
            val buffer = ByteArrayOutputStream()
            val code = block(TerminalConsole(console.profile, PrintStream(buffer, true, Charsets.UTF_8.name())))
            return code to buffer.toString(Charsets.UTF_8)
        }
        val setup = TerminalSetupRunner(
            prompt = TerminalPrompt.SYSTEM,
            config = { command, secret ->
                captured { buffer -> TerminalConfigRunner(secretInput = { _ -> secret }).run(command, languageOverride, buffer) }
            },
            configLive = { command ->
                // O serviço acabou de subir: espera ele tomar a trava antes de pedir o código.
                var waited = 0L
                while (reader.collector() == null && waited < SERVICE_START_WAIT_MILLIS) {
                    Thread.sleep(SERVICE_START_POLL_MILLIS)
                    waited += SERVICE_START_POLL_MILLIS
                }
                TerminalConfigRunner().run(command, languageOverride, console)
            },
            service = { command -> captured { buffer -> TerminalServiceCommands().run(command, languageOverride, buffer) } },
            readyAccounts = {
                TerminalAccountsRunner(
                    TerminalConfigClient(), preferences, home, CodexCliHomeProvider.resolve(),
                    codexSourceEnabled = { ApiSource.CODEX in readApiSourceCollection(PreferencesSettings(preferences), ENABLED_APIS_KEY) },
                    credentialsInKeychain = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
                ).readyForSetup()
            },
            currentThresholds = { readPersistedAlertSettings(PreferencesSettings(preferences)).quotaPercents.joinToString(",") },
            currentLanguage = { storedLanguage(PreferencesSettings(preferences)).name.lowercase() },
            isLinux = System.getProperty("os.name").orEmpty().lowercase().contains("linux"),
            dataRoot = shown(AppDataDirectory.root.path, home),
            version = CURRENT_APP_VERSION
        )
        return setup.run(TerminalSetupLabels(reader.language(languageOverride)), console)
    }

    private fun shown(path: String, home: File): String {
        // Canônicos dos dois lados: no Windows um pode vir com o nome curto 8.3.
        val homePath = runCatching { home.canonicalPath }.getOrDefault(home.path).trimEnd(File.separatorChar)
        val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
        return if (homePath.isNotEmpty() && canonical.startsWith(homePath + File.separator)) "~" + canonical.removePrefix(homePath) else path
    }
}
