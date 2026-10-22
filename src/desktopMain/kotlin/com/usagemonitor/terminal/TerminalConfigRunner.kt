package com.usagemonitor.terminal

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.ConfigChange
import com.usagemonitor.ConfigKeys
import com.usagemonitor.ConfigReadSource
import com.usagemonitor.ConfigValues
import com.usagemonitor.ConfigRefusals
import com.usagemonitor.ENABLED_APIS_KEY
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.CodexCliHomeProvider
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.data.datasource.LocalProxySettingsDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.data.datasource.LocalWebAccessSettingsDataSource
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.siteLocalIpv4Addresses
import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.buildHttpClient
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.requiresLocalApiKey
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalConfigLabels
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TerminalSettingRow
import com.usagemonitor.presentation.terminal.TerminalSpan
import com.usagemonitor.presentation.terminal.terminalLine
import com.usagemonitor.presentation.terminal.TerminalSourceRow
import com.usagemonitor.readApiSourceCollection
import com.usagemonitor.readPersistedAlertSettings
import java.io.File
import java.util.prefs.Preferences

/**
 * Executa os comandos de configurar pelo terminal (#440). Ler é direto, dos mesmos
 * arquivos e preferências do app; mudar passa pelo [TerminalConfigClient], que entrega
 * ao coletor vivo ou aplica sem ele.
 */
/** Valor que o ajuste não aceita: uso incorreto, como no parser. */
private const val EXIT_USAGE = 2

internal class TerminalConfigRunner(
    private val client: TerminalConfigClient = TerminalConfigClient(),
    private val reader: TerminalReader = TerminalReader(),
    private val preferences: Preferences = Preferences.userRoot().node(AppDataDirectory.preferencesNodeName),
    private val dataFile: (String) -> File = AppDataDirectory::file,
    private val secretInput: TerminalSecretInput = TerminalSecretInput.SYSTEM,
    private val home: File = File(System.getProperty("user.home").orEmpty()),
    private val codexHome: File = CodexCliHomeProvider.resolve(),
    private val telegramApi: () -> TelegramBotApi = {
        TelegramBotApi(buildHttpClient(resolveEffectiveProxy(LocalProxySettingsDataSource(dataFile("proxy.json")).load())))
    },
    private val teamCheck: (TeamIntegrationSettings, List<TeamCheckAccount>) -> List<String> =
        TerminalTeamRunner.realCheck { LocalProxySettingsDataSource(dataFile("proxy.json")).load() },
    private val webAddresses: () -> List<String> = ::siteLocalIpv4Addresses,
    private val keyProbe: (ApiSource, String) -> Throwable? =
        TerminalKeysRunner.realProbe { LocalProxySettingsDataSource(dataFile("proxy.json")).load() }
) {

    fun run(command: ConfigCommand, languageOverride: AppLanguage?, console: TerminalConsole): Int {
        val labels = TerminalConfigLabels(reader.language(languageOverride))
        val output = when (command) {
            is SourcesCommand -> sources(command, labels, console.profile)
            is SettingCommand -> setting(command, labels, console)
            is KeysCommand -> TerminalKeysRunner(
                client, secretInput,
                savedKey = { source -> LocalApiKeyDataSource(dataFile("api-keys.json")).load().forSource(source) },
                probe = keyProbe
            ).run(command, labels, console.profile)
            is TelegramCommand -> return TerminalTelegramRunner(
                client,
                collector = client::currentCollector,
                settings = { LocalTelegramSettingsDataSource(dataFile("telegram.json")).load() },
                api = telegramApi,
                secretInput = secretInput
            ).run(command, labels, console)
            is TeamCommand -> teamRunner().runTeam(command, labels, console.profile)
            is WebCommand -> teamRunner().runWeb(command, labels, console.profile)
            is AccountsCommand -> TerminalAccountsRunner(
                client, preferences, home, codexHome,
                codexSourceEnabled = { ApiSource.CODEX in readApiSourceCollection(PreferencesSettings(preferences), ENABLED_APIS_KEY) },
                credentialsInKeychain = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
            ).run(command, labels, console.profile)
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

    private fun teamRunner() = TerminalTeamRunner(
        client, preferences, home,
        teamSettings = { LocalTeamSettingsDataSource(dataFile("team.json")).load() },
        webSettings = { LocalWebAccessSettingsDataSource(dataFile("web-access.json")).load() },
        secretInput = secretInput,
        teamCheck = teamCheck,
        addresses = webAddresses
    )

    private fun setting(command: SettingCommand, labels: TerminalConfigLabels, console: TerminalConsole): TerminalOutput {
        val glyphs = console.profile.glyphs
        return when (command) {
            is SettingCommand.List -> {
                val keys = ConfigKeys.ALL.filter { key -> command.prefix == null || key.name == command.prefix || key.name.startsWith(command.prefix + ".") }
                if (keys.isEmpty()) {
                    TerminalOutput(TerminalConfigView.warning(labels.noSettingIn(command.prefix.orEmpty()), "usage-monitor config list", glyphs), EXIT_USAGE)
                } else {
                    val source = readSource()
                    // O segredo não sai daqui: a linha só sabe se há um.
                    val rows = keys.map { key -> TerminalSettingRow(key.name, key.read(source)?.let { value -> if (key.secret) glyphs.secretMask else value }, key.secret) }
                    TerminalConfigView.settings(rows, labels, glyphs)
                }
            }
            // Para scripts: o valor cru, `off` quando desligado, e o segredo nunca.
            is SettingCommand.Get -> {
                val key = ConfigKeys.named(command.key) ?: return TerminalOutput(emptyList(), EXIT_USAGE)
                val value = key.read(readSource())
                val shown = if (key.secret && value != null) glyphs.secretMask else value ?: ConfigValues.OFF
                TerminalOutput(listOf(terminalLine(TerminalSpan(shown))), TerminalConfigView.EXIT_OK)
            }
            is SettingCommand.Set -> setValue(command, labels, console)
        }
    }

    private fun setValue(command: SettingCommand.Set, labels: TerminalConfigLabels, console: TerminalConsole): TerminalOutput {
        val glyphs = console.profile.glyphs
        val key = ConfigKeys.named(command.key) ?: return TerminalOutput(emptyList(), EXIT_USAGE)
        val raw = command.value ?: secretInput.read(labels.secretPrompt)?.takeIf { text -> text.isNotBlank() }
            ?: return TerminalOutput(TerminalConfigView.warning(labels.noSecretGiven, null, glyphs), TerminalConfigView.EXIT_REFUSED)
        // Conferido aqui, no idioma de quem digitou, antes de ir ao coletor.
        val normalized = ConfigValues.normalize(key.type, raw)
            ?: return TerminalOutput(TerminalConfigView.warning(labels.invalidValue(key.name, labels.expected(key.type.name)), null, glyphs), EXIT_USAGE)
        val doneText = if (key.secret) labels.secretSaved(key.name) else "${key.name} = $normalized"
        return delivered(client.apply(ConfigChange.SetValue(key.name, raw)), labels, console.profile, doneText) { refused ->
            if (refused.code == ConfigRefusals.INVALID_VALUE) {
                TerminalConfigView.warning(labels.invalidValue(key.name, labels.expected(key.type.name)), null, glyphs)
            } else {
                null
            }
        }
    }

    /** Os valores como estão gravados: preferências e os dois arquivos, sem montar nada do app. */
    private fun readSource(): ConfigReadSource {
        val settings = PreferencesSettings(preferences)
        return ConfigReadSource(
            settings = settings,
            alerts = readPersistedAlertSettings(settings),
            proxy = LocalProxySettingsDataSource(dataFile("proxy.json")).load(),
            telegram = LocalTelegramSettingsDataSource(dataFile("telegram.json")).load()
        )
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
}
