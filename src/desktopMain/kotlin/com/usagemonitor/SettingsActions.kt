package com.usagemonitor

import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.presentation.ui.MODAL_CLOSE_SETTLE_MILLIS
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiState
import com.usagemonitor.presentation.ui.components.SettingsField
import com.usagemonitor.presentation.ui.components.SettingsToast
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.presentation.viewmodel.recordFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.key
import com.usagemonitor.data.datasource.LocalProxySettingsDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

/**
 * O que cada controle das Configurações faz: grava, aplica e avisa.
 *
 * Eram ~400 linhas de lambdas escritas dentro da chamada a
 * `SettingsDialogContent`, no meio de `runUsageMonitor`. Como métodos, cada
 * regra tem nome e um lugar só — e a chamada da tela vira uma lista de
 * referências.
 */
internal class SettingsActions(
    private val graph: AppGraph,
    private val viewModels: AppViewModels,
    private val shell: AppShellState,
    private val modal: AppModalState,
    private val feedback: AppSettingsFeedback,
    /** Escopo da composição, para a passagem das Configurações ao relatório de bug. */
    private val handOffScope: CoroutineScope
) {
    private val settings = graph.settings
    private val breadcrumbs = graph.breadcrumbs

    fun changeTheme(preset: AppThemePreset) {
        shell.changeTheme(preset)
        feedback.showToast(SettingsToast.Saved(SettingsField.THEME))
    }

    fun changeLanguage(language: AppLanguage) {
        shell.changeLanguage(language)
        feedback.showToast(SettingsToast.Saved(SettingsField.LANGUAGE))
    }

    /** Aviso e gravação não saem daqui: quem persiste é o coletor com debounce. */
    fun changeUiScale(percent: Int) {
        shell.changeUiScale(percent)
    }

    /** Idem: arrastar o controle dispararia um aviso por pixel. */
    fun changeWindowOpacity(percent: Int) {
        shell.changeWindowOpacity(percent)
    }

    fun changeReducedMotion(enabled: Boolean) {
        shell.changeReducedMotion(enabled)
    }

    fun changeTrayUsageRing(enabled: Boolean) {
        shell.changeTrayUsageRing(enabled)
    }

    /**
     * O registro do Windows pode recusar a escrita; nesse caso o estado volta ao
     * que o sistema realmente tem e o aviso precisa dizer que falhou.
     */
    fun changeAutoStart(enabled: Boolean) {
        val result = AutoStartManager.setAutoStart(enabled)
        val applied = result.isSuccess
        shell.recordAutoStart(if (applied) enabled else AutoStartManager.isAutoStartEnabled())
        feedback.reportSave(
            SettingsField.AUTO_START,
            applied,
            failureDetail = (result as? AutoStartResult.Failure)?.reason
        )
    }

    fun changeAlwaysOnTop(enabled: Boolean) {
        shell.changeAlwaysOnTop(enabled)
        feedback.showToast(SettingsToast.Saved(SettingsField.ALWAYS_ON_TOP))
    }

    fun changeCardsOnlyMode(enabled: Boolean) {
        shell.changeCardsOnlyMode(enabled)
        feedback.showToast(SettingsToast.Saved(SettingsField.CARDS_ONLY_MODE))
    }

    fun changeHudMode(enabled: Boolean) {
        shell.changeHudMode(enabled)
        feedback.showToast(SettingsToast.Saved(SettingsField.HUD_MODE))
    }

    /**
     * As Configurações fecham: o formulário mora na janela principal, e a janela
     * de Configurações ficaria por cima dele — e dentro da captura. A captura
     * acontece na abertura do formulário, então ela espera a janela terminar de
     * esmaecer: aberto no mesmo clique, ele fotografaria as Configurações no meio
     * da saída.
     */
    fun reportBug() {
        modal.isSettingsOpen = false
        handOffScope.launch {
            delay(MODAL_CLOSE_SETTLE_MILLIS)
            modal.isBugReportOpen = true
            breadcrumbs.recordScreenOpened("relatório de bug")
        }
    }

    fun changeAlertSettings(updated: UsageAlertSettings) {
        graph.alertSettingsFlow.value = updated
        persistAlertSettings(settings, updated)
        feedback.showToast(SettingsToast.Saved(SettingsField.ALERTS))
    }

    /**
     * Texto inválido não grava: o campo já recusa pelo `validate`, e gravar zero
     * aqui desligaria o teto em silêncio no meio de uma digitação.
     */
    fun commitMonthlyBudget(text: String) {
        val parsed = parseBudgetUsd(text) ?: return
        shell.changeMonthlyBudget(parsed)
        feedback.showToast(SettingsToast.Saved(SettingsField.ALERTS))
    }

    fun toggleApi(api: ApiSource, checked: Boolean) {
        val current = graph.enabledApis.value
        setEnabledApis(if (checked) current + api else current - api)
        viewModels.dashboard.refresh(api)
        feedback.showToast(SettingsToast.Saved(SettingsField.MONITORED_APIS))
    }

    fun saveApiKey(api: ApiSource, apiKey: String): Boolean {
        return runCatching {
            graph.apiKeyDataSource.save(api, apiKey)
            graph.apiKeySettings.value = graph.apiKeySettings.value.withKey(api, apiKey.trim())
        }.fold(
            onSuccess = {
                feedback.showToast(SettingsToast.Saved(SettingsField.API_KEY))
                true
            },
            onFailure = { error ->
                breadcrumbs.recordFailure("salvar chave de API", error)
                feedback.showToast(SettingsToast.SaveFailed(SettingsField.API_KEY))
                false
            }
        )
    }

    /**
     * Apagar a chave desliga a fonte, e as duas gravações andam juntas: deixá-la
     * ligada faria a coleta falhar com 401 a cada tique até o próximo reinício,
     * que é quando o filtro de arranque `API_KEY_DEPENDENT_SOURCES` a removeria de
     * qualquer forma. O aviso reusa `API_KEY`: é a mesma coisa sendo gravada.
     */
    fun removeApiKey(api: ApiSource): Boolean {
        return runCatching {
            graph.apiKeyDataSource.clear(api)
            graph.apiKeySettings.value = graph.apiKeySettings.value.withoutKey(api)
            setEnabledApis(graph.enabledApis.value - api)
            viewModels.dashboard.refresh(api)
        }.fold(
            onSuccess = {
                feedback.showToast(SettingsToast.Saved(SettingsField.API_KEY))
                true
            },
            onFailure = { error ->
                breadcrumbs.recordFailure("remover chave de API", error)
                feedback.showToast(SettingsToast.SaveFailed(SettingsField.API_KEY))
                false
            }
        )
    }

    fun toggleAnthropicProfile(profileId: String, checked: Boolean) {
        graph.profileRegistry.setEnabled(profileId, checked)
        refreshEnabledProfiles()
        viewModels.dashboard.refresh(ApiSource.ANTHROPIC)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
    }

    fun renameAnthropicProfile(profileId: String, label: String) {
        graph.profileRegistry.updateLabel(profileId, label)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILE_LABEL))
    }

    fun changeAnthropicProfileColor(profileId: String, color: AccountAccent?) {
        graph.profileRegistry.setColor(profileId, color?.name)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
    }

    fun changeAnthropicProfileEmoji(profileId: String, emoji: AccountEmoji?) {
        graph.profileRegistry.setEmoji(profileId, emoji?.name)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
    }

    fun addAnthropicProfile() {
        val selectedDirectory = chooseAnthropicConfigDirectory() ?: return
        graph.profileRegistry.addManual(selectedDirectory)
        refreshEnabledProfiles()
    }

    fun removeAnthropicProfile(profileId: String) {
        graph.profileRegistry.removeFromMonitor(profileId)
        refreshEnabledProfiles()
        viewModels.dashboard.refresh(ApiSource.ANTHROPIC)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
    }

    fun rescanAnthropicProfiles() {
        graph.profileRegistry.rescan(restoreRemoved = true)
        refreshEnabledProfiles()
        viewModels.dashboard.refresh(ApiSource.ANTHROPIC)
    }

    fun toggleProfileExpanded(profileId: String) {
        modal.expandedAnthropicProfileId = if (modal.expandedAnthropicProfileId == profileId) null else profileId
    }

    /** Mudar de servidor ou religar a integração não pode deixar um resultado antigo na tela. */
    fun changeTeamEnabled(enabled: Boolean) {
        val saved = updateTeam { current -> current.copy(enabled = enabled) }
        feedback.teamConnection = TeamConnectionUiState()
        feedback.reportSave(SettingsField.TEAM_INTEGRATION, saved)
    }

    fun changeTeamServerUrl(url: String) {
        val saved = updateTeam { current -> current.copy(serverUrl = url) }
        feedback.teamConnection = TeamConnectionUiState()
        feedback.reportSave(SettingsField.TEAM_SERVER, saved)
    }

    /**
     * Vincula na hora: uma chave que não cobre a conta marcada faria o envio
     * falhar em silêncio a cada 30s. E a identidade tem de sair com a chave
     * nova, senão o servidor continua sem saber a quem ela pertence até surgir
     * turno novo no Claude Code.
     */
    fun changeTeamApiKey(key: String) {
        val saved = updateTeam { current -> current.copy(apiKey = key) }
        feedback.reportSave(SettingsField.TEAM_KEY, saved)
        if (saved) {
            feedback.checkTeamConnection()
            viewModels.teamSync.requestImmediateSync()
        } else {
            feedback.teamConnection = TeamConnectionUiState()
        }
    }

    /**
     * O campo já barra apagar um apelido gravado; esta é a rede de baixo. O
     * apelido só chega ao servidor dentro de um ingest: sem antecipar a passada,
     * o nome novo esperaria o tique de 30s para aparecer ao time.
     */
    fun changeTeamAlias(alias: String) {
        if (alias.isBlank() && graph.teamSettingsFlow.value.alias.isNotBlank()) {
            feedback.showToast(SettingsToast.TeamAliasRequired)
            return
        }
        val saved = updateTeam { current -> current.copy(alias = alias) }
        feedback.reportSave(SettingsField.TEAM_ALIAS, saved)
        if (saved) {
            viewModels.teamSync.requestImmediateSync()
        }
    }

    /** Marcar uma conta é justamente o momento em que o vínculo da chave passa a importar. */
    fun changeTeamProfileParticipation(profileId: String, participates: Boolean) {
        val saved = updateTeam { current ->
            val updated = if (participates) {
                current.participatingProfileIds + profileId
            } else {
                current.participatingProfileIds - profileId
            }
            current.copy(participatingProfileIds = updated)
        }
        feedback.reportSave(SettingsField.TEAM_ACCOUNTS, saved)
        if (saved && participates) {
            feedback.checkTeamConnection()
        }
    }

    fun changeTeamAdminToken(token: String) {
        val saved = updateTeam { current -> current.copy(adminToken = token) }
        feedback.teamAdminConnection = TeamConnectionUiState()
        feedback.reportSave(SettingsField.TEAM_ADMIN_TOKEN, saved)
    }

    fun openTeamKeysManager() {
        breadcrumbs.recordScreenOpened("chaves das contas (admin)")
        modal.isTeamKeysOpen = true
        viewModels.teamKeys.open()
    }

    fun exitTeamAdminMode() {
        val saved = updateTeam { current -> current.copy(adminToken = "") }
        feedback.teamAdminConnection = TeamConnectionUiState()
        modal.isTeamKeysOpen = false
        feedback.reportSave(SettingsField.TEAM_ADMIN_TOKEN, saved)
    }

    fun changeProxyUseEnvironment(useEnvironment: Boolean) {
        updateProxy(resetConnection = true) { current -> current.copy(useEnvironmentProxy = useEnvironment) }
    }

    fun changeProxyHost(host: String) {
        updateProxy(resetConnection = true) { current -> current.copy(host = host) }
    }

    fun changeProxyPort(portText: String) {
        updateProxy(resetConnection = true) { current -> current.copy(port = portText.toIntOrNull() ?: 0) }
    }

    fun changeProxyUsername(username: String) {
        updateProxy(resetConnection = false) { current -> current.copy(username = username) }
    }

    fun changeProxyPassword(password: String) {
        updateProxy(resetConnection = false) { current -> current.copy(password = password) }
    }

    private fun setEnabledApis(updated: Set<ApiSource>) {
        graph.enabledApis.value = updated
        writeApiSourceCollection(settings, ENABLED_APIS_KEY, updated)
    }

    private fun refreshEnabledProfiles() {
        graph.enabledAnthropicProfiles.value = resolveAnthropicProfiles(
            graph.profileRegistry,
            graph.profileRegistry.profiles.value
        ).enabledProfiles
    }

    private fun updateTeam(transform: (TeamIntegrationSettings) -> TeamIntegrationSettings): Boolean {
        return updateTeamSettings(graph.teamSettingsFlow, graph.teamSettingsDataSource, transform)
    }

    private fun updateProxy(resetConnection: Boolean, transform: (ProxySettings) -> ProxySettings) {
        val saved = updateProxySettings(graph.proxySettingsFlow, graph.proxySettingsDataSource, transform)
        if (resetConnection) {
            feedback.proxyConnection = ProxyConnectionUiState()
        }
        feedback.reportSave(SettingsField.NETWORK_PROXY, saved)
    }
}

/**
 * Aplica uma mudança nas configurações de time e persiste.
 *
 * O `StateFlow` é atualizado antes da gravação: a UI reflete a digitação na hora
 * e a escrita em disco vai atrás. Uma falha de gravação não pode derrubar o
 * diálogo de configurações — pior caso, a mudança não sobrevive ao reinício.
 */
/**
 * Aplica a alteração em memória e no disco.
 *
 * Devolve se a gravação passou: o aviso de "salvo" no diálogo não pode ser
 * emitido a partir da intenção, só do resultado — antes disto a falha era
 * engolida por um `runCatching` sem tratamento.
 */
internal fun updateTeamSettings(
    settingsFlow: MutableStateFlow<TeamIntegrationSettings>,
    dataSource: LocalTeamSettingsDataSource,
    transform: (TeamIntegrationSettings) -> TeamIntegrationSettings
): Boolean {
    val updated = transform(settingsFlow.value)
    settingsFlow.value = updated
    return runCatching { dataSource.save(updated) }.isSuccess
}

internal fun updateProxySettings(
    settingsFlow: MutableStateFlow<ProxySettings>,
    dataSource: LocalProxySettingsDataSource,
    transform: (ProxySettings) -> ProxySettings
): Boolean {
    val updated = transform(settingsFlow.value)
    settingsFlow.value = updated
    return runCatching { dataSource.save(updated) }.isSuccess
}
