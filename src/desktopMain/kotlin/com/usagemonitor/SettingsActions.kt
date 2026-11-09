package com.usagemonitor

import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import java.io.File
import java.util.UUID
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    /** Time aberto nas Configurações, lido pelo admin de chaves (A06). Sem ligação, não chega ao roteamento. */
    private val settingsTeamSelection: MutableStateFlow<String?> = MutableStateFlow(null),
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

    /** Time aberto nas Configurações; `null` até a primeira escolha. */
    var selectedTeamId by mutableStateOf<String?>(null)
        private set

    /** Cria um time vazio com id único e nome sequencial, e o deixa aberto. */
    fun addTeam(): String {
        val integrations = store.teamIntegrationsFlow.value
        val id = "team-" + UUID.randomUUID().toString()
        val name = "Time ${integrations.teams.size + 1}"
        persistTeamIntegrations(store, integrations.upsert(TeamIntegrationSettings(id = id, name = name)))
        selectTeam(id)
        return id
    }

    fun selectTeam(teamId: String) {
        selectedTeamId = teamId
        settingsTeamSelection.value = teamId
    }

    /** Remove o time; se era o aberto, abre o primeiro que sobrou. */
    fun removeTeam(teamId: String) {
        val integrations = store.teamIntegrationsFlow.value
        persistTeamIntegrations(store, integrations.remove(teamId))
        feedback.clearTeamConnection(teamId)
        feedback.clearTeamAdminConnection(teamId)
        if (selectedTeamId == teamId) {
            selectedTeamId = store.teamIntegrationsFlow.value.teams.firstOrNull()?.id
            settingsTeamSelection.value = selectedTeamId
        }
    }

    /** Time que os controles sem id tocam: o aberto, ou o primeiro. */
    fun openedTeamId(): String =
        selectedTeamId ?: store.teamIntegrationsFlow.value.teams.firstOrNull()?.id ?: TeamIntegrations.LEGACY_TEAM_ID

    /** Mudar de servidor ou religar a integração não pode deixar um resultado antigo na tela. */
    fun changeTeamEnabled(enabled: Boolean) {
        val current = store.teamIntegrationsFlow.value
        val saved = persistTeamIntegrations(store, TeamIntegrations(current.deviceId, enabled, current.teams))
        feedback.clearAllTeamConnections()
        feedback.reportSave(SettingsField.TEAM_INTEGRATION, saved)
    }

    /** Só o nome muda: o id é a chave do time e não pode sair do lugar. */
    fun changeTeamName(teamId: String, name: String) {
        val saved = updateTeam(teamId) { current -> current.copy(name = name) }
        feedback.reportSave(SettingsField.TEAM_INTEGRATION, saved)
    }

    fun changeTeamServerUrl(teamId: String, url: String) {
        val saved = updateTeam(teamId) { current -> current.copy(serverUrl = url) }
        feedback.clearTeamConnection(teamId)
        feedback.reportSave(SettingsField.TEAM_SERVER, saved)
    }

    /**
     * Vincula na hora: uma chave que não cobre a conta marcada faria o envio
     * falhar em silêncio a cada 30s. E a identidade tem de sair com a chave
     * nova, senão o servidor continua sem saber a quem ela pertence até surgir
     * turno novo no Claude Code.
     */
    fun changeTeamApiKey(teamId: String, key: String) {
        val saved = updateTeam(teamId) { current -> current.copy(apiKey = key) }
        feedback.reportSave(SettingsField.TEAM_KEY, saved)
        if (saved) {
            feedback.checkTeamConnection(teamId)
            targets.requestTeamSync()
        } else {
            feedback.clearTeamConnection(teamId)
        }
    }

    /**
     * Chave própria de uma conta no time: só o time endereçado muda. Em branco remove a entrada, e a conta
     * fica sem chave (sem herdar a do time). Antes da gravação, o time copia a chave antiga para as
     * participantes sem chave própria ([TeamIntegrationSettings.withOwnKeysMaterialized]), para não perdê-la.
     * Mesmo caminho de [changeTeamApiKey] depois de gravar.
     */
    fun changeTeamAccountKey(teamId: String, profileId: String, key: String) {
        val trimmed = key.trim()
        val saved = updateTeam(teamId) { current ->
            val base = current.withOwnKeysMaterialized()
            val keys = if (trimmed.isEmpty()) {
                base.accountApiKeys - profileId
            } else {
                base.accountApiKeys + (profileId to trimmed)
            }
            base.copy(accountApiKeys = keys)
        }
        feedback.reportSave(SettingsField.TEAM_KEY, saved)
        if (saved) {
            feedback.checkTeamConnection(teamId)
            targets.requestTeamSync()
        } else {
            feedback.clearTeamConnection(teamId)
        }
    }

    /**
     * O campo já barra apagar um apelido gravado; esta é a rede de baixo. O
     * apelido só chega ao servidor dentro de um ingest: sem antecipar a passada,
     * o nome novo esperaria o tique de 30s para aparecer ao time.
     */
    fun changeTeamAlias(teamId: String, alias: String) {
        if (alias.isBlank() && store.teamSettingsFlow.value.alias.isNotBlank()) {
            feedback.showToast(SettingsToast.TeamAliasRequired)
            return
        }
        val saved = updateTeam(teamId) { current -> current.copy(alias = alias) }
        feedback.reportSave(SettingsField.TEAM_ALIAS, saved)
        if (saved) {
            targets.requestTeamSync()
        }
    }

    /**
     * Marcar uma conta é justamente o momento em que o vínculo da chave passa a importar. Ligar materializa
     * antes a chave antiga do time nas participantes sem chave própria: a conta nova entra sem chave, e a
     * chave do time nunca migra para ela.
     */
    fun changeTeamProfileParticipation(teamId: String, profileId: String, participates: Boolean) {
        // Com time cadastrado, o agregado tira a conta dos outros times; sem ele, o time nasce com a conta.
        val integrations = store.teamIntegrationsFlow.value
        val target = integrations.team(teamId)
        val saved = if (participates && target != null) {
            val materialized = integrations.upsert(target.withOwnKeysMaterialized())
            persistTeamIntegrations(store, materialized.withParticipation(teamId, profileId, true))
        } else {
            updateTeam(teamId) { current ->
                val base = if (participates) current.withOwnKeysMaterialized() else current
                val updated = if (participates) {
                    base.participatingProfileIds + profileId
                } else {
                    base.participatingProfileIds - profileId
                }
                base.copy(participatingProfileIds = updated)
            }
        }
        feedback.reportSave(SettingsField.TEAM_ACCOUNTS, saved)
        if (saved && participates) {
            feedback.checkTeamConnection(teamId)
        }
    }

    fun changeTeamAdminToken(teamId: String, token: String) {
        val saved = updateTeam(teamId) { current -> current.copy(adminToken = token) }
        feedback.clearTeamAdminConnection(teamId)
        feedback.reportSave(SettingsField.TEAM_ADMIN_TOKEN, saved)
    }

    fun openTeamKeysManager(teamId: String) {
        breadcrumbs.recordScreenOpened("chaves das contas (admin)")
        modal.isTeamKeysOpen = true
        targets.openTeamKeys()
    }

    fun exitTeamAdminMode(teamId: String) {
        val saved = updateTeam(teamId) { current -> current.copy(adminToken = "") }
        feedback.clearTeamAdminConnection(teamId)
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

    private fun updateTeam(teamId: String, transform: (TeamIntegrationSettings) -> TeamIntegrationSettings): Boolean {
        return updateTeamSettings(store, teamId, transform)
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
 * Aplica a alteração no agregado de times e no disco.
 *
 * O agregado é a fonte; o derivado do primeiro time é publicado junto, antes da
 * gravação, para a UI refletir a digitação na hora. Uma falha de gravação não
 * derruba o diálogo: pior caso, a mudança não sobrevive ao reinício.
 *
 * Devolve se a gravação passou: o aviso de "salvo" no diálogo não pode ser
 * emitido a partir da intenção, só do resultado — antes disto a falha era
 * engolida por um `runCatching` sem tratamento.
 */
internal fun updateTeamSettings(
    store: AppSettingsStore,
    transform: (TeamIntegrationSettings) -> TeamIntegrationSettings
): Boolean {
    val first = store.teamIntegrationsFlow.value.teams.firstOrNull()?.id ?: TeamIntegrations.LEGACY_TEAM_ID
    return updateTeamSettings(store, first, transform)
}

/** Grava a alteração no time endereçado pelo id; time ausente nasce vazio com esse id. */
internal fun updateTeamSettings(
    store: AppSettingsStore,
    teamId: String,
    transform: (TeamIntegrationSettings) -> TeamIntegrationSettings
): Boolean {
    val current = store.teamIntegrationsFlow.value
    val base = current.team(teamId) ?: TeamIntegrationSettings(id = teamId)
    val updated = transform(base)
    // O `enabled` do agregado é o do time alterado: o construtor de `TeamIntegrations` o aplica a todo time.
    val aggregate = TeamIntegrations(current.deviceId, updated.enabled, current.teams)
    return persistTeamIntegrations(store, aggregate.upsert(updated))
}

/** Publica o agregado e grava; devolve se a gravação passou. */
internal fun persistTeamIntegrations(store: AppSettingsStore, updated: TeamIntegrations): Boolean {
    store.publishTeamIntegrations(updated)
    return runCatching { store.teamSettingsDataSource.saveAll(updated) }.isSuccess
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
