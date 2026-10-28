package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.TeamConnectionLabels
import com.usagemonitor.presentation.ui.TeamCredentialKind
import com.usagemonitor.presentation.viewmodel.teamConnectionFailureOf
import com.usagemonitor.AnthropicProfileRegistry
import com.usagemonitor.ConfigChange
import com.usagemonitor.buildHttpClient
import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.repository.TeamAdminRepositoryImpl
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.ProxyEnvironmentConfig
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.WebAccessSettings
import com.usagemonitor.presentation.terminal.TerminalConfigLabels
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TerminalSettingRow
import com.usagemonitor.presentation.terminal.TerminalSpan
import com.usagemonitor.presentation.terminal.TerminalTeamLabels
import com.usagemonitor.presentation.terminal.terminalLine
import io.ktor.client.HttpClient
import java.io.File
import java.net.URI
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Uma conta que vai ao time: o apelido para a mensagem e a identidade para o vínculo. */
internal data class TeamCheckAccount(val profileId: String, val label: String, val accountKey: String?, val email: String?)

/**
 * `team …` e `web …` (#440, abas Time e Rede). Antes de entrar no time, o terminal faz a
 * conferência do "Testar conexão" das Configurações — servidor de pé e a chave vinculada
 * a cada conta marcada —, e só grava se ela passar.
 */
internal class TerminalTeamRunner(
    private val client: TerminalConfigClient,
    private val preferences: Preferences,
    private val home: File,
    private val teamSettings: () -> TeamIntegrationSettings,
    private val webSettings: () -> WebAccessSettings,
    private val secretInput: TerminalSecretInput,
    /** As falhas da conferência, uma por linha; vazia quando passou. */
    private val teamCheck: (TeamIntegrationSettings, List<TeamCheckAccount>) -> List<String>,
    private val addresses: () -> List<String>
) {

    fun runTeam(command: TeamCommand, configLabels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput {
        val labels = TerminalTeamLabels(configLabels.language)
        return when (command) {
            is TeamCommand.Join -> join(command, labels, configLabels, profile)
            TeamCommand.Status -> teamStatus(labels, configLabels, profile)
            TeamCommand.Leave -> delivered(client.apply(ConfigChange.TeamLeave), configLabels, profile, labels.left) { null }
        }
    }

    private fun join(command: TeamCommand.Join, labels: TerminalTeamLabels, configLabels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput {
        val glyphs = profile.glyphs
        val current = teamSettings()
        val server = command.server?.trim() ?: current.serverUrl.takeIf { url -> url.isNotBlank() }
            ?: return refused(TerminalConfigView.warning(labels.needsServer, "usage-monitor team join --server https://time.exemplo.com --alias <nome>", glyphs))
        val alias = command.alias?.trim() ?: current.alias.takeIf { name -> name.isNotBlank() }
            ?: return refused(TerminalConfigView.warning(labels.needsAlias, "usage-monitor team join --alias <nome>", glyphs))
        val all = anthropicAccounts()
        val accounts = command.accounts?.let { wanted ->
            val found = wanted.associateWith { reference -> all.firstOrNull { account -> account.profileId == reference || account.label.equals(reference, ignoreCase = true) } }
            val missing = found.filterValues { account -> account == null }.keys
            if (missing.isNotEmpty()) {
                return refused(TerminalConfigView.warning(labels.unknownAccounts(missing.joinToString(", ")), "usage-monitor accounts list", glyphs))
            }
            found.values.filterNotNull().distinct().map { account -> account.check }
        } ?: all.filter { account -> account.monitored }.map { account -> account.check }
        val apiKey = secretInput.read(labels.keyPrompt)?.trim()?.takeIf { key -> key.isNotEmpty() }
            ?: return refused(TerminalConfigView.warning(configLabels.noSecretGiven, null, glyphs))
        val candidate = current.copy(enabled = true, serverUrl = server, apiKey = apiKey, alias = alias, participatingProfileIds = accounts.map { account -> account.profileId }.toSet())
        if (!command.skipCheck) {
            val failures = teamCheck(candidate, accounts)
            if (failures.isNotEmpty()) {
                return refused(
                    TerminalConfigView.warning(labels.checkFailed(failures.joinToString(" · ")), null, glyphs) +
                        TerminalConfigView.warning(labels.skipCheckHint, "usage-monitor team join --skip-check", glyphs)
                )
            }
        }
        val joined = delivered(
            client.apply(ConfigChange.TeamJoin(server, apiKey, alias, candidate.participatingProfileIds)), configLabels, profile,
            labels.joined(hostOf(server), alias, accounts.size)
        ) { null }
        val note = TerminalConfigView.note(if (command.skipCheck) labels.notChecked else labels.checked)
        return if (joined.exitCode == TerminalConfigView.EXIT_OK) TerminalOutput(joined.lines + note, joined.exitCode) else joined
    }

    private fun teamStatus(labels: TerminalTeamLabels, configLabels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput {
        val current = teamSettings()
        val accounts = anthropicAccounts().filter { account -> account.profileId in current.participatingProfileIds }
        val connection = if (current.isActive) {
            teamCheck(current, accounts.map { account -> account.check }).joinToString(" · ").ifEmpty { labels.connectionOk }
        } else {
            null
        }
        val rows = listOf(
            TerminalSettingRow(labels.rowTeam, if (current.isActive) labels.on else labels.off, masked = false),
            TerminalSettingRow(labels.rowServer, current.normalizedServerUrl.ifEmpty { null }, masked = false),
            TerminalSettingRow(labels.rowAlias, current.alias.ifEmpty { null }, masked = false),
            TerminalSettingRow(labels.rowKey, current.apiKey.takeIf { key -> key.isNotBlank() }?.let { profile.glyphs.secretMask }, masked = true),
            TerminalSettingRow(labels.rowAccounts, accounts.joinToString(", ") { account -> account.label }.ifEmpty { labels.none }, masked = false),
            TerminalSettingRow(labels.rowConnection, connection, masked = false)
        )
        return TerminalConfigView.settings(rows, configLabels, profile.glyphs)
    }

    fun runWeb(command: WebCommand, configLabels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput {
        val labels = TerminalTeamLabels(configLabels.language)
        val glyphs = profile.glyphs
        return when (command) {
            is WebCommand.Switch -> {
                val port = command.port ?: webSettings().port
                val switched = delivered(client.apply(ConfigChange.WebEnabled(command.enabled, command.port)), configLabels, profile, labels.webSwitched(command.enabled, port)) { null }
                if (command.enabled && switched.exitCode == TerminalConfigView.EXIT_OK) {
                    TerminalOutput(switched.lines + TerminalConfigView.note(labels.webTokenHint) + TerminalConfigView.commandLine("usage-monitor web token --show"), switched.exitCode)
                } else {
                    switched
                }
            }
            WebCommand.Status -> {
                val current = webSettings()
                val urls = addresses().joinToString(", ") { address -> "http://$address:${current.port}" }.ifEmpty { labels.noAddress }
                val rows = listOf(
                    TerminalSettingRow(labels.rowAccess, if (current.enabled) labels.on else labels.off, masked = false),
                    TerminalSettingRow(labels.rowPort, current.port.toString(), masked = false),
                    TerminalSettingRow(labels.rowAddresses, urls, masked = false),
                    TerminalSettingRow(labels.rowToken, current.token.takeIf { token -> token.isNotBlank() }?.let { glyphs.secretMask }, masked = true)
                )
                TerminalConfigView.settings(rows, configLabels, glyphs)
            }
            WebCommand.ShowToken -> {
                val current = webSettings()
                if (!current.enabled || current.token.isBlank()) {
                    refused(TerminalConfigView.warning(labels.webOff, "usage-monitor web enable", glyphs))
                } else {
                    // A exceção da D5: o endereço com o token, pedido com --show, como o "copiar" do app.
                    val hosts = addresses().ifEmpty { listOf("localhost") }
                    TerminalOutput(hosts.map { host -> terminalLine(TerminalSpan("http://$host:${current.port}/?t=${current.token}")) }, TerminalConfigView.EXIT_OK)
                }
            }
            WebCommand.RegenerateToken -> delivered(client.apply(ConfigChange.WebRegenerateToken), configLabels, profile, labels.webTokenRegenerated) { null }
        }
    }

    private fun refused(lines: List<com.usagemonitor.presentation.terminal.TerminalLine>): TerminalOutput = TerminalOutput(lines, TerminalConfigView.EXIT_REFUSED)

    private fun hostOf(server: String): String = runCatching { URI(server.trim()).host }.getOrNull() ?: server.trim()

    private class StoredAccount(val profileId: String, val label: String, val monitored: Boolean, val check: TeamCheckAccount)

    /** As contas Anthropic gravadas, com a identidade lida do `.claude.json` (sem montar o registro). */
    private fun anthropicAccounts(): List<StoredAccount> =
        AnthropicProfileRegistry.readStored(preferences).filterNot { record -> record.hidden }.map { record ->
            val identity = if (record.id == DEFAULT_ANTHROPIC_PROFILE_ID) File(home, ".claude.json") else File(record.configDirectory, ".claude.json")
            val (accountKey, email) = readClaudeIdentity(identity)
            StoredAccount(record.id, record.label, record.enabled, TeamCheckAccount(record.id, record.label, accountKey, email))
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** `accountUuid` e e-mail do `oauthAccount`, os mesmos campos que o registro inspeciona. */
        fun readClaudeIdentity(file: File): Pair<String?, String?> =
            runCatching {
                val account = json.parseToJsonElement(file.readText()).jsonObject["oauthAccount"]?.jsonObject
                account?.get("accountUuid")?.jsonPrimitive?.contentOrNull to account?.get("emailAddress")?.jsonPrimitive?.contentOrNull
            }.getOrDefault(null to null)

        /**
         * A conferência do botão "Testar conexão" (`AppSettingsFeedback.checkTeamConnection`):
         * o servidor de pé e, para cada conta, o vínculo da chave — que vincula, e não só
         * confere, como lá.
         */
        fun realCheck(
            proxy: () -> ProxySettings,
            httpClientFactory: (ProxyEnvironmentConfig?) -> HttpClient = { effectiveProxy -> buildHttpClient(effectiveProxy) }
        ): (TeamIntegrationSettings, List<TeamCheckAccount>) -> List<String> = { settings, accounts ->
            runBlocking {
                val httpClient = httpClientFactory(resolveEffectiveProxy(proxy()))
                try {
                    val remote = RemoteTeamDataSource(httpClient)
                    val health = TeamUsageRepositoryImpl(remote, settingsProvider = { settings }).checkConnection().exceptionOrNull()
                    val stage = health?.let { failure -> teamConnectionFailureOf(failure) }
                    if (stage != null) {
                        // #494: a etapa e o conselho, nas frases das janelas. Em português, como as
                        // linhas vizinhas desta conferência ("sem login").
                        val kind = TeamCredentialKind.ACCOUNT_KEY
                        listOf(TeamConnectionLabels.headline(stage, kind, AppLanguage.PT) + ". " + TeamConnectionLabels.advice(stage, kind, AppLanguage.PT))
                    } else if (health != null) {
                        listOf(health.message ?: health::class.simpleName.orEmpty())
                    } else {
                        val admin = TeamAdminRepositoryImpl(remote, settingsProvider = { settings })
                        accounts.mapNotNull { account ->
                            val key = account.accountKey ?: return@mapNotNull "${account.label}: sem login"
                            val result = admin.claimKeyForAccount(key, account.email)
                            when {
                                result.isFailure -> "${account.label}: ${result.exceptionOrNull()?.message.orEmpty()}"
                                result.getOrNull()?.authorized != true -> "${account.label}: a chave não cobre esta conta"
                                else -> null
                            }
                        }
                    }
                } finally {
                    httpClient.close()
                }
            }
        }
    }
}
