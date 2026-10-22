package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.data.datasource.LocalProxySettingsDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.data.datasource.LocalWebAccessSettingsDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * O que as Configurações gravam, sem rede: preferências, arquivos de segredo, contas e
 * os flows que o coletor lê ao vivo (#440).
 *
 * Saiu do [AppGraph] para o terminal aplicar uma mudança sem subir `HttpClient`,
 * repositórios nem view models: sem coletor vivo, `usage-monitor sources enable …`
 * monta só isto, aplica pelo mesmo [ConfigApplier] do coletor e sai.
 */
internal class AppSettingsStore(
    breadcrumbs: BreadcrumbRecorder,
    languageOverride: AppLanguage? = null,
    // Nó da raiz de dados (#419): `com.usagemonitor` sem `--user-data-dir`.
    val preferencesNode: Preferences = Preferences.userRoot().node(AppDataDirectory.preferencesNodeName),
    /** Onde moram os arquivos de configuração; o teste passa uma pasta temporária. */
    dataFile: (String) -> File = AppDataDirectory::file,
    /** A pasta pessoal, onde as contas do Claude Code são descobertas. */
    homeDirectory: () -> File = { File(System.getProperty("user.home").orEmpty()) }
) {
    // O proxy é lido antes do `HttpClient` do grafo: o engine é montado uma vez, e
    // mudar a configuração só vale a partir do próximo arranque (issue #174).
    val proxySettingsDataSource = LocalProxySettingsDataSource(dataFile("proxy.json"))
    val proxySettingsFlow = MutableStateFlow(proxySettingsDataSource.load())

    val settings = PreferencesSettings(preferencesNode)
    val languageSelection = LanguageSelection(settings, languageOverride)

    // Chaves de API são segredos: não entram em PreferencesSettings, que grava
    // os valores em claro no registro do Windows.
    val apiKeyDataSource = LocalApiKeyDataSource(dataFile("api-keys.json"))
    val apiKeySettings = MutableStateFlow(apiKeyDataSource.load())

    val enabledApis = MutableStateFlow(persistedEnabledApis())

    val profileRegistry = AnthropicProfileRegistry(
        preferences = preferencesNode,
        defaultEnabled = ApiSource.ANTHROPIC in enabledApis.value,
        homeDirProvider = homeDirectory,
        breadcrumbs = breadcrumbs
    )
    val enabledAnthropicProfiles = MutableStateFlow(
        resolveAnthropicProfiles(profileRegistry, profileRegistry.profiles.value).enabledProfiles
    )

    // Contas Codex extras (issue #329): a padrão continua fora do registro.
    val codexProfileRegistry = CodexProfileRegistry(preferencesNode)
    val enabledCodexProfiles = MutableStateFlow(codexProfileRegistry.enabledProfiles)

    // A chave do servidor é segredo e vai para um arquivo com permissão restrita
    // ao dono, não para as preferências — estas são gravadas em claro no registro.
    // `StateFlow`: o repositório e o serviço de envio leem as credenciais de fora
    // da composição, e precisam sempre do valor corrente.
    val teamSettingsDataSource = LocalTeamSettingsDataSource(dataFile("team.json"))
    val teamSettingsFlow = MutableStateFlow(teamSettingsDataSource.load())

    // Acesso web local (#388): token em arquivo de segredo, nunca no registro.
    val webAccessSettingsDataSource = LocalWebAccessSettingsDataSource(dataFile("web-access.json"))
    val webAccessSettingsFlow = MutableStateFlow(webAccessSettingsDataSource.load())

    // Bot do Telegram (#387): token e conversas pareadas em arquivo de segredo.
    val telegramSettingsDataSource = LocalTelegramSettingsDataSource(dataFile("telegram.json"))
    val telegramSettingsFlow = MutableStateFlow(telegramSettingsDataSource.load())

    // Preferências de alerta como flow, e não como estado da composição: quem as
    // consome são os view models, que vivem fora dela — é delas que sai o fator
    // da detecção de anomalia e o limiar de sessão sem resposta.
    val alertSettingsFlow = MutableStateFlow(readPersistedAlertSettings(settings))

    /**
     * Grava as preferências no disco agora. No Linux o `java.util.prefs` só grava por
     * temporizador (30 s): o terminal que lê logo depois de o coletor aplicar uma
     * mudança veria o valor antigo (risco R2 do plano da #440).
     */
    fun flush() {
        runCatching { preferencesNode.flush() }
    }

    fun close() {
        profileRegistry.close()
        flush()
    }

    /**
     * As fontes habilitadas gravadas. Fonte que depende de chave não volta
     * habilitada sem ela: o card abriria em erro de configuração a cada arranque.
     */
    private fun persistedEnabledApis(): Set<ApiSource> {
        return readApiSourceCollection(settings, ENABLED_APIS_KEY)
            .filter { source ->
                source !in API_KEY_DEPENDENT_SOURCES || apiKeySettings.value.forSource(source) != null
            }
            .toSet()
            .ifEmpty { DEFAULT_ENABLED_APIS }
    }
}
