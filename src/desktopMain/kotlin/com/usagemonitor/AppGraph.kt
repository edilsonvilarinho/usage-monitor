package com.usagemonitor

import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.data.datasource.LocalWebAccessSettingsDataSource
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.data.CodexCliHomeProvider
import com.usagemonitor.data.datasource.LocalAnthropicCreditsDiagnosticsRecorder
import com.usagemonitor.data.datasource.LocalAntigravityUsageDataSource
import com.usagemonitor.data.datasource.LocalApiKeyDataSource
import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.data.datasource.LocalCodexActivityDataSource
import com.usagemonitor.data.datasource.LocalCodexAuthDataSource
import com.usagemonitor.data.datasource.LocalCodexRolloutRateLimitDataSource
import com.usagemonitor.data.datasource.LocalCodexCliSessionDataSource
import com.usagemonitor.data.datasource.LocalCodexDiagnosticsRecorder
import com.usagemonitor.data.datasource.LocalCredentialDataSource
import com.usagemonitor.data.datasource.LocalCursorSessionDataSource
import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.data.datasource.LocalGeminiUsageDataSource
import com.usagemonitor.data.datasource.LocalKiloUsageDataSource
import com.usagemonitor.data.datasource.LocalOpenCodeUsageDataSource
import com.usagemonitor.data.datasource.LocalProxySettingsDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.data.datasource.LocalTeamSyncStateDataSource
import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.datasource.RemoteCursorUsageApiDataSource
import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import com.usagemonitor.data.repository.AntigravityRepositoryImpl
import com.usagemonitor.data.repository.AppUpdateRepositoryImpl
import com.usagemonitor.data.repository.CliSessionRepositoryImpl
import com.usagemonitor.data.repository.CodexCliSessionRepositoryImpl
import com.usagemonitor.data.repository.CodexProfileSources
import com.usagemonitor.data.repository.CodexRepositoryImpl
import com.usagemonitor.data.repository.CursorRepositoryImpl
import com.usagemonitor.data.repository.DashboardCacheRepositoryImpl
import com.usagemonitor.data.repository.DeepSeekRepositoryImpl
import com.usagemonitor.data.repository.GeminiRepositoryImpl
import com.usagemonitor.data.repository.KiloRepositoryImpl
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
import com.usagemonitor.data.repository.OpenCodeGoRepositoryImpl
import com.usagemonitor.data.repository.OpenCodeRepositoryImpl
import com.usagemonitor.data.repository.OpenRouterRepositoryImpl
import com.usagemonitor.data.repository.TeamAdminRepositoryImpl
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.data.repository.UsageHistoryRepositoryImpl
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.InMemoryTeamServerClockOffset
import com.usagemonitor.domain.usecase.GetCachedDashboardStatsUseCase
import com.usagemonitor.domain.usecase.GetStalledCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.domain.usecase.SaveDashboardCacheUseCase
import com.usagemonitor.domain.usecase.SyncCliSessionIndexUseCase
import com.usagemonitor.update.DesktopAppUpdateReleaseOpener
import java.io.File
import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

/**
 * O grafo de dependências do app: preferências, clientes HTTP, data sources,
 * repositórios e os casos de uso que mais de um consumidor partilha.
 *
 * Classe comum, e não `remember` dentro de `application {}`: todos estes objetos
 * são criados uma vez por processo e nenhum depende de estado de composição. Eles
 * viviam em ~180 linhas de `remember` sem chave dentro de `runUsageMonitor`, e era
 * isso que fazia daquele lambda um método que o backend JVM não conseguia
 * transformar (issue #298).
 *
 * Criado **depois** do `SingleInstanceGuard`: a segunda instância sai antes de
 * abrir banco ou arquivo de ninguém.
 */
internal class AppGraph(val breadcrumbs: BreadcrumbRecorder) {

    // Lido ANTES do `httpClient`: o proxy precisa estar resolvido no momento em
    // que o engine é montado, e o client é criado uma única vez — mudar a
    // configuração nas Configurações só vale a partir do próximo arranque
    // (issue #174).
    val proxySettingsDataSource = LocalProxySettingsDataSource()
    val proxySettingsFlow = MutableStateFlow(proxySettingsDataSource.load())
    private val effectiveProxy = resolveEffectiveProxy(proxySettingsFlow.value)

    val httpClient = buildHttpClient(effectiveProxy)
    // A requisição do Cursor carrega uma sessão em cookie. Redirects ficam
    // desativados neste cliente para nunca encaminhar a sessão a outro host.
    val cursorHttpClient = buildHttpClient(effectiveProxy, followRedirects = false)

    val preferencesNode: Preferences = Preferences.userRoot().node("com.usagemonitor")
    val settings = PreferencesSettings(preferencesNode)

    // Chaves de API são segredos: não entram em PreferencesSettings, que grava
    // os valores em claro no registro do Windows.
    val apiKeyDataSource = LocalApiKeyDataSource()
    val apiKeySettings = MutableStateFlow(apiKeyDataSource.load())

    val enabledApis = MutableStateFlow(persistedEnabledApis())

    val persistedNextRefreshAt: Instant? = settings.getLong(NEXT_REFRESH_AT_KEY, -1L)
        .takeIf { millis -> millis > 0 }
        ?.let { millis -> Instant.fromEpochMilliseconds(millis) }

    val profileRegistry = AnthropicProfileRegistry(
        preferences = preferencesNode,
        defaultEnabled = ApiSource.ANTHROPIC in enabledApis.value,
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
    val teamSettingsDataSource = LocalTeamSettingsDataSource()
    val teamSettingsFlow = MutableStateFlow(teamSettingsDataSource.load())

    // Acesso web local (#388): token em arquivo de segredo, nunca no registro.
    val webAccessSettingsDataSource = LocalWebAccessSettingsDataSource()
    val webAccessSettingsFlow = MutableStateFlow(webAccessSettingsDataSource.load())

    // Bot do Telegram (#387): token e conversas pareadas em arquivo de segredo.
    val telegramSettingsDataSource = LocalTelegramSettingsDataSource()
    val telegramSettingsFlow = MutableStateFlow(telegramSettingsDataSource.load())

    // Preferências de alerta como flow, e não como estado da composição: quem as
    // consome são os view models, que vivem fora dela — é delas que sai o fator
    // da detecção de anomalia e o limiar de sessão sem resposta.
    val alertSettingsFlow = MutableStateFlow(readPersistedAlertSettings(settings))

    /**
     * Referência da janela principal para quem vive fora do `Window`: a bandeja,
     * o diálogo de exportação e o relatório de bug. Só é lida dentro de lambdas,
     * nunca para decidir o que compor.
     */
    @Volatile
    var mainWindow: java.awt.Window? = null

    private val credentialDataSource = LocalCredentialDataSource(
        httpClient = httpClient,
        profileLocationProvider = { profile -> profileRegistry.locationFor(profile.id) }
    )
    private val remoteApiDataSource = RemoteApiDataSource(
        httpClient = httpClient,
        codexDiagnosticsRecorder = LocalCodexDiagnosticsRecorder(),
        anthropicCreditsDiagnosticsRecorder = LocalAnthropicCreditsDiagnosticsRecorder()
    )
    val usageHistoryDataSource = LocalUsageHistoryDataSource()

    // Cada conta Anthropic tem seu próprio config dir do Claude Code, e cada um
    // tem seu `projects/`. É daí que sai a atribuição de sessão para conta — os
    // transcripts em si não carregam identidade nenhuma.
    val cliSessionDataSource = LocalCliSessionDataSource(
        projectRootsProvider = {
            profileRegistry.profiles.value.map { record ->
                CliProjectRoot(
                    profileId = record.id,
                    directoryPath = File(record.configDirectory, "projects").absolutePath
                )
            }
        }
    )
    // Índice separado: rollouts do Codex não entram nas tabelas `cli_*` do Claude.
    val codexCliSessionDataSource = LocalCodexCliSessionDataSource()
    // Mesma conexão do índice de sessões, não uma segunda para o mesmo arquivo:
    // `useConnection` é sincronizado, então o envio espera a indexação terminar
    // em vez de disputar a escrita e receber `SQLITE_BUSY`.
    val teamSyncStateDataSource = LocalTeamSyncStateDataSource(cliSessionDataSource.sharedConnectionManager)
    val openCodeUsageDataSource = LocalOpenCodeUsageDataSource()
    val kiloUsageDataSource = LocalKiloUsageDataSource()
    val codexActivityDataSource = LocalCodexActivityDataSource(CodexCliHomeProvider.resolve())

    val anthropicRepository = AnthropicRepositoryImpl(credentialDataSource, remoteApiDataSource)
    val minimaxRepository = MiniMaxRepositoryImpl(
        apiDataSource = remoteApiDataSource,
        apiKeyReader = { apiKeySettings.value.forSource(ApiSource.MINIMAX) }
    )
    val codexRepository = CodexRepositoryImpl(
        authDataSource = LocalCodexAuthDataSource(),
        apiDataSource = remoteApiDataSource,
        rolloutRateLimits = LocalCodexRolloutRateLimitDataSource(),
        profileSources = { profile -> codexProfileRegistry.directoryOf(profile.id)?.let(::codexProfileSources) }
    )
    val deepSeekRepository = DeepSeekRepositoryImpl(
        apiDataSource = remoteApiDataSource,
        apiKeyReader = { apiKeySettings.value.forSource(ApiSource.DEEPSEEK) }
    )
    val openCodeRepository = OpenCodeRepositoryImpl(openCodeUsageDataSource)
    // A assinatura Go é outra fonte: HTTP com chave, cotas em percentual. O plano
    // gratuito do Zen acima continua vindo do SQLite local, sem credencial.
    val openCodeGoRepository = OpenCodeGoRepositoryImpl(
        apiDataSource = remoteApiDataSource,
        apiKeyReader = { apiKeySettings.value.forSource(ApiSource.OPENCODE_GO) }
    )
    // Saldo pré-pago do OpenRouter — HTTP com chave, mesmo desenho de DeepSeek.
    val openRouterRepository = OpenRouterRepositoryImpl(
        apiDataSource = remoteApiDataSource,
        apiKeyReader = { apiKeySettings.value.forSource(ApiSource.OPENROUTER) }
    )
    val kiloRepository = KiloRepositoryImpl(kiloUsageDataSource)
    val geminiRepository = GeminiRepositoryImpl(LocalGeminiUsageDataSource())
    val cursorRepository = CursorRepositoryImpl(
        LocalCursorSessionDataSource(),
        RemoteCursorUsageApiDataSource(RemoteApiDataSource(httpClient = cursorHttpClient))
    )
    val antigravityRepository = AntigravityRepositoryImpl(LocalAntigravityUsageDataSource())
    val cliSessionRepository = CliSessionRepositoryImpl(cliSessionDataSource)
    val codexCliSessionRepository = CodexCliSessionRepositoryImpl(codexCliSessionDataSource)

    // Uma instância só: o desvio é entre o relógio desta máquina e o do servidor,
    // não de cada consumidor. Quem o mede é a batida de presença; quem o lê é a
    // classificação de quem está online.
    val teamServerClockOffset = InMemoryTeamServerClockOffset()
    private val remoteTeamDataSource = RemoteTeamDataSource(httpClient)
    val teamUsageRepository = TeamUsageRepositoryImpl(
        remoteDataSource = remoteTeamDataSource,
        settingsProvider = { teamSettingsFlow.value },
        serverClockOffset = teamServerClockOffset
    )
    val teamAdminRepository = TeamAdminRepositoryImpl(
        remoteDataSource = remoteTeamDataSource,
        settingsProvider = { teamSettingsFlow.value }
    )
    val appUpdateRepository = AppUpdateRepositoryImpl(remoteApiDataSource)
    val appUpdateReleaseOpener = DesktopAppUpdateReleaseOpener()

    private val usageHistoryRepository = UsageHistoryRepositoryImpl(usageHistoryDataSource)
    private val dashboardCacheRepository = DashboardCacheRepositoryImpl(LocalDashboardCacheDataSource())
    val recordUsageSnapshot = RecordUsageSnapshotUseCase(usageHistoryRepository)
    val getUsageHistory = GetUsageHistoryUseCase(usageHistoryRepository)
    val saveDashboardCache = SaveDashboardCacheUseCase(dashboardCacheRepository)
    val getCachedDashboardStats = GetCachedDashboardStatsUseCase(dashboardCacheRepository)

    // Uma instância só: a tela de Sessões CLI, o semáforo e o envio para o time
    // indexam o mesmo banco, e duas cópias do caso de uso não trariam nada.
    val syncCliSessionIndex = SyncCliSessionIndexUseCase(cliSessionRepository)
    val getStalledCliSessions = GetStalledCliSessionsUseCase(cliSessionRepository)

    // A janela principal só existe depois da composição; por isso o writer
    // recebe uma função e não a referência. O idioma sai das preferências na hora
    // de gerar: o relatório precisa do idioma corrente, não do que valia no arranque.
    val usageExportWriter = DesktopUsageExportWriter(
        parentWindow = { mainWindow },
        language = { storedLanguage(settings) }
    )

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

/**
 * As leituras de uma conta Codex extra pelo diretório dela (issue #329): o
 * diretório **é** o `CODEX_HOME`, então `auth.json`, `cap_sid` e `sessions/`
 * ficam direto nele, e não num `.codex` abaixo, como na conta padrão.
 */
internal fun codexProfileSources(home: File): CodexProfileSources {
    return CodexProfileSources(
        auth = LocalCodexAuthDataSource(
            homeDirProvider = { home.path },
            authFileProvider = { dir -> File(dir, "auth.json") },
            capSidFileProvider = { dir -> File(dir, "cap_sid") }
        ),
        rolloutRateLimits = LocalCodexRolloutRateLimitDataSource(codexHome = { home })
    )
}
