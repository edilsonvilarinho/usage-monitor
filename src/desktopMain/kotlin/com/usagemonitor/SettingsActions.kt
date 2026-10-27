package com.usagemonitor

import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import java.io.File
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
 * O que as Configurações pedem aos view models. Porta para as regras das
 * Configurações rodarem em teste sem o `AppViewModels`, que sobe a coleta
 * inteira; quem implementa é ele.
 */
internal interface SettingsActionTargets {
    /** Recoleta a fonte agora: ligar, desligar ou trocar a chave não espera o próximo tique. */
    fun refreshSource(api: ApiSource)
    fun requestTeamSync()
    fun openTeamKeys()
}

/**
 * O que cada controle das Configurações faz: grava, aplica e avisa.
 *
 * Eram ~400 linhas de lambdas escritas dentro da chamada a
 * `SettingsDialogContent`, no meio de `runUsageMonitor`. Como métodos, cada
 * regra tem nome e um lugar só — e a chamada da tela vira uma lista de
 * referências.
 */
internal class SettingsActions(
    /** O que as Configurações gravam, sem rede (o mesmo do terminal, #440). */
    private val store: AppSettingsStore,
    private val breadcrumbs: BreadcrumbRecorder,
    private val targets: SettingsActionTargets,
    private val shell: AppShellState,
    private val modal: AppModalState,
    private val feedback: AppSettingsFeedback,
    /** Escopo da composição, para a passagem das Configurações ao relatório de bug. */
    private val handOffScope: CoroutineScope,
    private val autoStartHost: () -> AutoStartHost = AutoStartHost::system,
    private val isDefaultDataRoot: () -> Boolean = { AppDataDirectory.isDefault },
    private val chooseAnthropicDirectory: () -> File? = ::chooseAnthropicConfigDirectory,
    private val chooseCodexDirectory: () -> File? = ::chooseCodexHomeDirectory
) {
    private val settings = store.settings

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
        // O interruptor já vem desligado com `--user-data-dir` (#419); isto é a
        // segunda trava, para nenhum caminho gravar a chave `Run` sem a pasta.
        if (!isDefaultDataRoot()) {
            return
        }
        val host = autoStartHost()
        val result = AutoStartManager.setAutoStart(enabled, host)
        val applied = result.isSuccess
        shell.recordAutoStart(if (applied) enabled else AutoStartManager.isAutoStartEnabled(host))
        feedback.reportSave(
            SettingsField.AUTO_START,
            applied,
            failureDetail = (result as? AutoStartResult.Failure)?.reason
        )
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
        store.alertSettingsFlow.value = updated
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
        val current = store.enabledApis.value
        setEnabledApis(if (checked) current + api else current - api)
        targets.refreshSource(api)
        feedback.showToast(SettingsToast.Saved(SettingsField.MONITORED_APIS))
    }

    fun saveApiKey(api: ApiSource, apiKey: String): Boolean {
        return runCatching {
            store.apiKeyDataSource.save(api, apiKey)
            store.apiKeySettings.value = store.apiKeySettings.value.withKey(api, apiKey.trim())
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
            store.apiKeyDataSource.clear(api)
            store.apiKeySettings.value = store.apiKeySettings.value.withoutKey(api)
            setEnabledApis(store.enabledApis.value - api)
            targets.refreshSource(api)
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
        store.profileRegistry.setEnabled(profileId, checked)
        refreshEnabledProfiles()
        targets.refreshSource(ApiSource.ANTHROPIC)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
    }

    fun renameAnthropicProfile(profileId: String, label: String) {
        store.profileRegistry.updateLabel(profileId, label)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILE_LABEL))
    }

    fun changeAnthropicProfileColor(profileId: String, color: AccountAccent?) {
        store.profileRegistry.setColor(profileId, color?.name)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
    }

    fun changeAnthropicProfileEmoji(profileId: String, emoji: AccountEmoji?) {
        store.profileRegistry.setEmoji(profileId, emoji?.name)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
    }

    fun addAnthropicProfile() {
        val selectedDirectory = chooseAnthropicDirectory() ?: return
        store.profileRegistry.addManual(selectedDirectory)
        refreshEnabledProfiles()
    }

    fun removeAnthropicProfile(profileId: String) {
        store.profileRegistry.removeFromMonitor(profileId)
        refreshEnabledProfiles()
        targets.refreshSource(ApiSource.ANTHROPIC)
        feedback.showToast(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES))
    }

    /** Issue #329: o diretório vira conta extra já habilitada, e a coleta dela sai na hora. */
    fun addCodexProfile() {
        val selectedDirectory = chooseCodexDirectory() ?: return
        store.codexProfileRegistry.add(selectedDirectory)
            .onSuccess { modal.codexProfileError = null }
            .onFailure { failure -> modal.codexProfileError = failure.message }
        refreshCodexProfiles()
    }

    fun toggleCodexProfile(profileId: String, checked: Boolean) {
        store.codexProfileRegistry.setEnabled(profileId, checked)
        refreshCodexProfiles()
    }

    fun removeCodexProfile(profileId: String) {
        store.codexProfileRegistry.remove(profileId)
        refreshCodexProfiles()
    }

    private fun refreshCodexProfiles() {
        store.enabledCodexProfiles.value = store.codexProfileRegistry.enabledProfiles
        targets.refreshSource(ApiSource.CODEX)
    }

    fun rescanAnthropicProfiles() {
        store.profileRegistry.rescan(restoreRemoved = true)
        refreshEnabledProfiles()
        targets.refreshSource(ApiSource.ANTHROPIC)
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
            targets.requestTeamSync()
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
        if (alias.isBlank() && store.teamSettingsFlow.value.alias.isNotBlank()) {
            feedback.showToast(SettingsToast.TeamAliasRequired)
            return
        }
        val saved = updateTeam { current -> current.copy(alias = alias) }
        feedback.reportSave(SettingsField.TEAM_ALIAS, saved)
        if (saved) {
            targets.requestTeamSync()
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
        targets.openTeamKeys()
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

    private fun setEnabledApis(updated: Set<ApiSource>) = persistEnabledApis(store, updated)

    private fun refreshEnabledProfiles() {
        store.enabledAnthropicProfiles.value = resolveAnthropicProfiles(
            store.profileRegistry,
            store.profileRegistry.profiles.value
        ).enabledProfiles
    }

    private fun updateTeam(transform: (TeamIntegrationSettings) -> TeamIntegrationSettings): Boolean {
        return updateTeamSettings(store.teamSettingsFlow, store.teamSettingsDataSource, transform)
    }

    private fun updateProxy(resetConnection: Boolean, transform: (ProxySettings) -> ProxySettings) {
        val saved = updateProxySettings(store.proxySettingsFlow, store.proxySettingsDataSource, transform)
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

/**
 * Fontes ligadas: publica e grava. Um caminho só para a aba APIs e o `/api` do bot
 * (#398) — duas cópias divergiriam na chave ou na ordem das gravações.
 */
internal fun persistEnabledApis(store: AppSettingsStore, updated: Set<ApiSource>) {
    store.enabledApis.value = updated
    writeApiSourceCollection(store.settings, ENABLED_APIS_KEY, updated)
}
