package com.usagemonitor

import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamConnectionStage
import com.usagemonitor.presentation.ui.TeamCredentialKind
import com.usagemonitor.presentation.ui.localized
import com.usagemonitor.presentation.viewmodel.teamConnectionFailureOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ProxyEnvironmentConfig
import com.usagemonitor.domain.entity.TeamKeyVerification
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.presentation.ui.components.ApiKeyCheckStatus
import com.usagemonitor.presentation.ui.components.ApiKeyCheckFormState
import com.usagemonitor.presentation.ui.components.ProxyConnectionFormState
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiStatus
import com.usagemonitor.presentation.ui.components.SettingsField
import com.usagemonitor.presentation.ui.components.SettingsToast
import com.usagemonitor.presentation.ui.components.SettingsToastEvent
import com.usagemonitor.presentation.ui.components.TeamConnectionFormState
import com.usagemonitor.presentation.ui.components.TeamConnectionUiStatus
import com.usagemonitor.presentation.ui.components.apiKeyCheckResult
import com.usagemonitor.presentation.viewmodel.recordFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * O retorno que as Configurações mostram: os avisos de "salvo" e o resultado dos
 * quatro testes de conexão (proxy, chave de API, servidor do time e token
 * administrativo).
 *
 * Os testes rodam em [scope], que é o da composição: fechar o app cancela o que
 * estiver em voo. O idioma é lido na hora da mensagem, não na construção, porque
 * ele muda com o app aberto.
 */
@Stable
internal class AppSettingsFeedback(
    /** O que as Configurações gravam: proxy, chaves, time e contas. */
    private val store: AppSettingsStore,
    private val breadcrumbs: BreadcrumbRecorder,
    /** Repositório de uso do time pedido (`TeamRepositoryRegistry.usageRepository`). */
    private val teamUsageRepositoryFor: (teamId: String) -> TeamUsageRepository,
    /** `ClaimTeamKeyForAccountUseCase` do time pedido: vincula a chave à conta. */
    private val claimTeamKey: suspend (teamId: String, accountKey: String, accountEmail: String?, profileId: String?) -> Result<TeamKeyVerification>,
    /** `ValidateAdminTokenUseCase` do time pedido. */
    private val validateAdminTokenWithServer: suspend (teamId: String) -> Result<Unit>,
    private val scope: CoroutineScope,
    private val language: () -> AppLanguage,
    /** Rótulo do perfil para a mensagem de erro do teste do time. */
    private val profileLabel: (profileId: String) -> String?,
    /** Cliente efêmero de cada teste de conexão; a suíte passa um `MockEngine`. */
    private val httpClientFactory: (ProxyEnvironmentConfig?) -> HttpClient = { proxy -> buildHttpClient(proxy) }
) {

    // Cada emissão precisa de um id próprio: dois avisos iguais em sequência —
    // salvar o mesmo campo duas vezes — seriam o mesmo valor e o diálogo não
    // reagiria ao segundo.
    var toastEvent by mutableStateOf<SettingsToastEvent?>(null)
        private set
    private var toastGeneration = 0

    // Resultado por time: um time nunca apaga o retorno de outro.
    private val teamConnections = mutableStateMapOf<String, TeamConnectionFormState>()
    private val teamAdminConnections = mutableStateMapOf<String, TeamConnectionFormState>()

    fun teamConnection(teamId: String): TeamConnectionFormState = teamConnections[teamId] ?: TeamConnectionFormState()

    fun teamAdminConnection(teamId: String): TeamConnectionFormState = teamAdminConnections[teamId] ?: TeamConnectionFormState()

    fun setTeamConnection(teamId: String, state: TeamConnectionFormState) {
        teamConnections[teamId] = state
    }

    fun setTeamAdminConnection(teamId: String, state: TeamConnectionFormState) {
        teamAdminConnections[teamId] = state
    }

    fun clearTeamConnection(teamId: String) {
        teamConnections.remove(teamId)
    }

    fun clearAllTeamConnections() {
        teamConnections.clear()
    }

    fun clearTeamAdminConnection(teamId: String) {
        teamAdminConnections.remove(teamId)
    }
    var proxyConnection by mutableStateOf(ProxyConnectionFormState())
    var apiKeyCheck by mutableStateOf(ApiKeyCheckFormState())

    fun showToast(toast: SettingsToast) {
        toastGeneration += 1
        toastEvent = SettingsToastEvent(id = toastGeneration, toast = toast)
    }

    /** Traduz e registra o resultado da gravação no limite que o apresenta. */
    fun reportSave(field: SettingsField, saved: Boolean, failureDetail: String? = null) {
        showToast(if (saved) SettingsToast.Saved(field) else SettingsToast.SaveFailed(field))
        if (!saved) {
            breadcrumbs.recordFailure(
                "salvar configuração ${field.name}",
                failureDetail ?: "gravação recusada sem exceção retornada"
            )
        }
    }

    /**
     * Cliente **efêmero**, montado com o valor corrente do proxy (já commitado
     * pelos campos da seção, mesmo antes de o app reiniciar) — nunca o
     * `httpClient` compartilhado, que foi montado no arranque e não pode ser
     * reconfigurado em runtime sem arriscar `ClosedException` numa requisição
     * in-flight de outro consumidor (issue #174).
     */
    fun checkProxyConnection() {
        val manualProxy = resolveEffectiveProxy(store.proxySettingsFlow.value.copy(useEnvironmentProxy = false))
        proxyConnection = ProxyConnectionFormState(ProxyConnectionUiStatus.CHECKING)
        scope.launch {
            val result = Result.runCatching {
                val testClient = httpClientFactory(manualProxy)
                try {
                    testClient.get("https://api.github.com/zen")
                } finally {
                    testClient.close()
                }
            }
            proxyConnection = result.fold(
                onSuccess = { response ->
                    if (response.status.isSuccess()) {
                        ProxyConnectionFormState(
                            status = ProxyConnectionUiStatus.OK,
                            message = pick("Conexão OK.", "Connection OK.")
                        )
                    } else {
                        breadcrumbs.recordFailure("testar conexão do proxy", "HTTP ${response.status.value}")
                        ProxyConnectionFormState(
                            status = ProxyConnectionUiStatus.FAILED,
                            message = "HTTP ${response.status.value}"
                        )
                    }
                },
                onFailure = { error ->
                    breadcrumbs.recordFailure("testar conexão do proxy", error)
                    ProxyConnectionFormState(
                        status = ProxyConnectionUiStatus.FAILED,
                        message = error.message ?: pick("Falha desconhecida.", "Unknown failure.")
                    )
                }
            )
        }
    }

    /**
     * "Testar chave" da aba APIs (issue #204).
     *
     * **Passa pelo repositório da fonte, nunca por HTTP cru.** A MiniMax responde
     * `HTTP 200` com `status_code` de erro no corpo e o OpenCode Go traduz o
     * `403 EntitlementError` em "sem assinatura"; só o repositório sabe disso, e
     * um caminho próprio de teste aprovaria chave que a coleta real recusa.
     *
     * Client efêmero com o proxy corrente, mesma razão de [checkProxyConnection].
     * Aqui o proxy sai de `resolveEffectiveProxy` sem forçar o modo manual — o
     * teste tem de usar exatamente o que a coleta usaria, e numa máquina que
     * depende de `HTTPS_PROXY` ignorá-lo daria falso negativo.
     *
     * A chave candidata não é gravada e não entra em breadcrumb.
     */
    fun checkApiKey(source: ApiSource, candidateKey: String) {
        val apiKey = candidateKey.takeIf { key -> key.isNotBlank() }
            ?: store.apiKeySettings.value.forSource(source).orEmpty()
        val proxy = resolveEffectiveProxy(store.proxySettingsFlow.value)
        apiKeyCheck = ApiKeyCheckFormState(status = ApiKeyCheckStatus.CHECKING)
        scope.launch {
            val result = runCatching {
                val testClient = httpClientFactory(proxy)
                try {
                    testApiKeyUsage(source, RemoteApiDataSource(httpClient = testClient)) { apiKey }
                } finally {
                    testClient.close()
                }
            }.fold(
                onSuccess = { probe -> probe },
                onFailure = { error -> Result.failure(error) }
            )
            val error = result.exceptionOrNull()
            if (error != null) {
                breadcrumbs.recordFailure("testar chave da API ${source.name}", error)
            }
            apiKeyCheck = apiKeyCheckResult(source, error, language())
        }
    }

    fun resetApiKeyCheck() {
        apiKeyCheck = ApiKeyCheckFormState()
    }

    /**
     * Confere o servidor e o vínculo da chave com **cada** conta marcada.
     *
     * O teste antigo consultava uma conta inventada só para exercitar a chave.
     * Isso funcionava enquanto qualquer chave lia qualquer conta; com autorização
     * por conta aquela consulta passaria a ser recusada e o botão reprovaria uma
     * configuração correta. Agora o alvo é real, e o erro aponta qual conta falhou.
     */
    fun checkTeamConnection(teamId: String) {
        setTeamConnection(teamId, TeamConnectionFormState(TeamConnectionUiStatus.CHECKING))
        scope.launch {
            val healthError = teamUsageRepositoryFor(teamId).checkConnection().exceptionOrNull()
            if (healthError != null) {
                breadcrumbs.recordFailure("testar conexão do time", healthError)
                setTeamConnection(
                    teamId,
                    TeamConnectionFormState(
                        status = TeamConnectionUiStatus.FAILED,
                        message = healthError.message ?: pick("Falha desconhecida.", "Unknown failure."),
                        failure = teamConnectionFailureOf(healthError)
                    )
                )
                return@launch
            }

            val current = store.teamIntegrationsFlow.value.team(teamId)
            if (current == null) {
                setTeamConnection(teamId, TeamConnectionFormState())
                return@launch
            }
            val targets = buildTeamSyncTargets(store.profileRegistry)
                .filter { target -> current.participates(target.profileId) }
            if (targets.isEmpty()) {
                setTeamConnection(
                    teamId,
                    TeamConnectionFormState(
                        status = TeamConnectionUiStatus.OK,
                        message = pick(
                            "Servidor OK. Marque uma conta para conferir a chave.",
                            "Server OK. Select an account to check the key."
                        )
                    )
                )
                return@launch
            }

            val failures = mutableListOf<String>()
            // Falha que parou antes da credencial (rede, certificado, servidor) é a
            // mesma para todas as contas: vira o caminho, não uma linha por conta.
            var unreachable: TeamConnectionFailure? = null
            for (target in targets) {
                val label = profileLabel(target.profileId) ?: target.profileId
                // Conta ligada sem chave própria (mapa não vazio) não herda a do time: recusa
                // antes de qualquer requisição, com o motivo na linha da conta.
                if (current.apiKeyFor(target.profileId).isBlank()) {
                    failures += localized(language(), "$label: falta a chave desta conta.", "$label: key missing for this account.")
                    continue
                }
                // Vincula, e não apenas confere: o vínculo antes só nascia dentro
                // de um envio de turnos, então numa máquina já sincronizada ele
                // nunca acontecia e a leitura ficava recusada indefinidamente.
                val result = claimTeamKey(teamId, target.accountKey, target.accountEmail, target.profileId)
                val error = result.exceptionOrNull()
                if (error != null) {
                    breadcrumbs.recordFailure("validar vínculo de conta com a chave do time", error)
                    failures += "$label: ${error.message.orEmpty()}"
                    val stage = teamConnectionFailureOf(error)
                    if (stage != null && stage.stage != TeamConnectionStage.CREDENTIAL) {
                        unreachable = stage
                    }
                } else if (result.getOrNull()?.authorized != true) {
                    breadcrumbs.recordFailure(
                        "validar vínculo de conta com a chave do time",
                        "servidor recusou o vínculo da conta"
                    )
                    failures += pick("$label: a chave não cobre esta conta.", "$label: the key does not cover this account.")
                }
            }

            setTeamConnection(
                teamId,
                if (failures.isEmpty()) {
                    TeamConnectionFormState(
                        status = TeamConnectionUiStatus.OK,
                        message = pick(
                            "Conexão OK e conta vinculada a esta chave.",
                            "Connection OK and account linked to this key."
                        )
                    )
                } else {
                    TeamConnectionFormState(
                        status = TeamConnectionUiStatus.FAILED,
                        message = failures.joinToString(" • "),
                        failure = unreachable
                    )
                }
            )
        }
    }

    fun validateAdminToken(teamId: String) {
        setTeamAdminConnection(teamId, TeamConnectionFormState(TeamConnectionUiStatus.CHECKING))
        scope.launch {
            val error = validateAdminTokenWithServer(teamId).exceptionOrNull()
            setTeamAdminConnection(
                teamId,
                if (error == null) {
                    TeamConnectionFormState(status = TeamConnectionUiStatus.OK, message = pick("Token válido.", "Token is valid."))
                } else {
                    breadcrumbs.recordFailure("validar token administrativo do time", error)
                    TeamConnectionFormState(
                        status = TeamConnectionUiStatus.FAILED,
                        message = error.message ?: pick("Falha desconhecida.", "Unknown failure."),
                        failure = teamConnectionFailureOf(error),
                        credentialKind = TeamCredentialKind.ADMIN_TOKEN
                    )
                }
            )
        }
    }

    private fun pick(portuguese: String, english: String): String {
        return if (language() == AppLanguage.PT) portuguese else english
    }
}
