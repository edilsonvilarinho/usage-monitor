package com.usagemonitor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.repository.resolveEffectiveProxy
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.ApiKeyCheckStatus
import com.usagemonitor.presentation.ui.components.ApiKeyCheckUiState
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiState
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiStatus
import com.usagemonitor.presentation.ui.components.SettingsField
import com.usagemonitor.presentation.ui.components.SettingsToast
import com.usagemonitor.presentation.ui.components.SettingsToastEvent
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamConnectionUiStatus
import com.usagemonitor.presentation.ui.components.apiKeyCheckResult
import com.usagemonitor.presentation.viewmodel.recordFailure
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
    private val graph: AppGraph,
    private val viewModels: AppViewModels,
    private val scope: CoroutineScope,
    private val language: () -> AppLanguage,
    /** Rótulo do perfil para a mensagem de erro do teste do time. */
    private val profileLabel: (profileId: String) -> String?
) {
    private val breadcrumbs = graph.breadcrumbs

    // Cada emissão precisa de um id próprio: dois avisos iguais em sequência —
    // salvar o mesmo campo duas vezes — seriam o mesmo valor e o diálogo não
    // reagiria ao segundo.
    var toastEvent by mutableStateOf<SettingsToastEvent?>(null)
        private set
    private var toastGeneration = 0

    var teamConnection by mutableStateOf(TeamConnectionUiState())
    var teamAdminConnection by mutableStateOf(TeamConnectionUiState())
    var proxyConnection by mutableStateOf(ProxyConnectionUiState())
    var apiKeyCheck by mutableStateOf(ApiKeyCheckUiState())

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
        val manualProxy = resolveEffectiveProxy(graph.proxySettingsFlow.value.copy(useEnvironmentProxy = false))
        proxyConnection = ProxyConnectionUiState(ProxyConnectionUiStatus.CHECKING)
        scope.launch {
            val result = Result.runCatching {
                val testClient = buildHttpClient(manualProxy)
                try {
                    testClient.get("https://api.github.com/zen")
                } finally {
                    testClient.close()
                }
            }
            proxyConnection = result.fold(
                onSuccess = { response ->
                    if (response.status.isSuccess()) {
                        ProxyConnectionUiState(
                            status = ProxyConnectionUiStatus.OK,
                            message = pick("Conexão OK.", "Connection OK.")
                        )
                    } else {
                        breadcrumbs.recordFailure("testar conexão do proxy", "HTTP ${response.status.value}")
                        ProxyConnectionUiState(
                            status = ProxyConnectionUiStatus.FAILED,
                            message = "HTTP ${response.status.value}"
                        )
                    }
                },
                onFailure = { error ->
                    breadcrumbs.recordFailure("testar conexão do proxy", error)
                    ProxyConnectionUiState(
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
            ?: graph.apiKeySettings.value.forSource(source).orEmpty()
        val proxy = resolveEffectiveProxy(graph.proxySettingsFlow.value)
        apiKeyCheck = ApiKeyCheckUiState(status = ApiKeyCheckStatus.CHECKING)
        scope.launch {
            val result = runCatching {
                val testClient = buildHttpClient(proxy)
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
        apiKeyCheck = ApiKeyCheckUiState()
    }

    /**
     * Confere o servidor e o vínculo da chave com **cada** conta marcada.
     *
     * O teste antigo consultava uma conta inventada só para exercitar a chave.
     * Isso funcionava enquanto qualquer chave lia qualquer conta; com autorização
     * por conta aquela consulta passaria a ser recusada e o botão reprovaria uma
     * configuração correta. Agora o alvo é real, e o erro aponta qual conta falhou.
     */
    fun checkTeamConnection() {
        teamConnection = TeamConnectionUiState(TeamConnectionUiStatus.CHECKING)
        scope.launch {
            val healthError = graph.teamUsageRepository.checkConnection().exceptionOrNull()
            if (healthError != null) {
                breadcrumbs.recordFailure("testar conexão do time", healthError)
                teamConnection = TeamConnectionUiState(
                    status = TeamConnectionUiStatus.FAILED,
                    message = healthError.message ?: pick("Falha desconhecida.", "Unknown failure.")
                )
                return@launch
            }

            val current = graph.teamSettingsFlow.value
            val targets = buildTeamSyncTargets(graph.profileRegistry)
                .filter { target -> current.participates(target.profileId) }
            if (targets.isEmpty()) {
                teamConnection = TeamConnectionUiState(
                    status = TeamConnectionUiStatus.OK,
                    message = pick(
                        "Servidor OK. Marque uma conta para conferir a chave.",
                        "Server OK. Select an account to check the key."
                    )
                )
                return@launch
            }

            val failures = mutableListOf<String>()
            for (target in targets) {
                val label = profileLabel(target.profileId) ?: target.profileId
                // Vincula, e não apenas confere: o vínculo antes só nascia dentro
                // de um envio de turnos, então numa máquina já sincronizada ele
                // nunca acontecia e a leitura ficava recusada indefinidamente.
                val result = viewModels.claimTeamKeyForAccount(target.accountKey, target.accountEmail)
                val error = result.exceptionOrNull()
                if (error != null) {
                    breadcrumbs.recordFailure("validar vínculo de conta com a chave do time", error)
                    failures += "$label: ${error.message.orEmpty()}"
                } else if (result.getOrNull()?.authorized != true) {
                    breadcrumbs.recordFailure(
                        "validar vínculo de conta com a chave do time",
                        "servidor recusou o vínculo da conta"
                    )
                    failures += pick("$label: a chave não cobre esta conta.", "$label: the key does not cover this account.")
                }
            }

            teamConnection = if (failures.isEmpty()) {
                TeamConnectionUiState(
                    status = TeamConnectionUiStatus.OK,
                    message = pick(
                        "Conexão OK e conta vinculada a esta chave.",
                        "Connection OK and account linked to this key."
                    )
                )
            } else {
                TeamConnectionUiState(status = TeamConnectionUiStatus.FAILED, message = failures.joinToString(" • "))
            }
        }
    }

    fun validateAdminToken() {
        teamAdminConnection = TeamConnectionUiState(TeamConnectionUiStatus.CHECKING)
        scope.launch {
            val error = viewModels.validateAdminToken().exceptionOrNull()
            teamAdminConnection = if (error == null) {
                TeamConnectionUiState(status = TeamConnectionUiStatus.OK, message = pick("Token válido.", "Token is valid."))
            } else {
                breadcrumbs.recordFailure("validar token administrativo do time", error)
                TeamConnectionUiState(
                    status = TeamConnectionUiStatus.FAILED,
                    message = error.message ?: pick("Falha desconhecida.", "Unknown failure.")
                )
            }
        }
    }

    private fun pick(portuguese: String, english: String): String {
        return if (language() == AppLanguage.PT) portuguese else english
    }
}
