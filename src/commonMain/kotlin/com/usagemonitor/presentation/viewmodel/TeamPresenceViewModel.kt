package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.usecase.DeleteTeamAccountUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamPresenceUseCase
import com.usagemonitor.domain.usecase.GetTeamPresenceUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamMemberUseCase
import com.usagemonitor.domain.usecase.TeamPresenceResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.math.absoluteValue

private const val UNKNOWN_ERROR_MESSAGE = "erro desconhecido"

private const val MILLIS_PER_MINUTE = 60L * 1_000

/**
 * Estado da janela "Conectados agora".
 *
 * Espelha [TeamUsageViewModel] de propósito — mesma anatomia, mesma promessa de
 * tempo real, mesmas restrições anti-flicker, inclusive o segundo flow:
 * [actionError] mora fora do `Success` porque o laço de 5s republica o estado e
 * apagaria a mensagem antes de ela ser lida.
 *
 * Uma diferença deliberada: **sem janela de quota**. Presença não tem `range`
 * nem âncora de reset — o recorte é fixo e vive no caso de uso, como
 * `sinceEpochMillis` cru.
 *
 * O [liveIntervalMillis] fecha o ciclo pelo lado da leitura; a latência real com
 * que alguém aparece é dominada pelo heartbeat de 30s da máquina dele.
 */
class TeamPresenceViewModel(
    private val getTeamPresence: GetTeamPresenceUseCase,
    /**
     * Leitura de todas as contas, para o administrador.
     *
     * `null` desliga a visão global — é o que acontece numa instalação sem
     * administração, onde a janela só existe por conta.
     */
    private val getAdminTeamPresence: GetAdminTeamPresenceUseCase? = null,
    /**
     * Remoção de integrante e de conta, disponíveis só em modo admin.
     *
     * `null` desliga a ação, como [getAdminTeamPresence]: numa instalação sem
     * administração a janela continua sendo só de leitura, e nenhum botão
     * destrutivo aparece.
     */
    private val removeTeamMember: RemoveAdminTeamMemberUseCase? = null,
    private val deleteTeamAccount: DeleteTeamAccountUseCase? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val liveIntervalMillis: Long = DEFAULT_LIVE_INTERVAL_MILLIS,
    private val clock: Clock = Clock.System
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

    private var loadJob: Job? = null
    private var liveJob: Job? = null

    /** Evita empilhar consultas quando a rede está mais lenta que o intervalo. */
    private val loadMutex = Mutex()

    private val _uiState = MutableStateFlow<TeamPresenceUiState>(TeamPresenceUiState.Loading)
    val uiState: StateFlow<TeamPresenceUiState> = _uiState.asStateFlow()

    /** Falha da última remoção. Fora do [uiState] para o laço de 5s não a apagar. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var accountKey: String? = null
    private var accountLabel: String? = null

    /** `true` enquanto a janela mostra todas as contas do servidor. */
    private var adminOverview: Boolean = false

    /** Aponta a janela para uma conta Anthropic e liga o tempo real. */
    fun openForAccount(accountKey: String, accountLabel: String?) {
        val scopeChanged = this.accountKey != accountKey || adminOverview
        this.accountKey = accountKey
        this.accountLabel = accountLabel
        this.adminOverview = false

        // Trocar de escopo zera a lista: manter os integrantes da conta anterior
        // mostraria pessoas de outro time como conectadas.
        if (scopeChanged) {
            _uiState.value = TeamPresenceUiState.Loading
        }

        refresh()
        startLiveLoop()
    }

    /**
     * Aponta a janela para **todas** as contas do servidor e liga o tempo real.
     *
     * É o modo de quem administra: ele não participa necessariamente de nenhuma
     * das contas que administra. Sem [getAdminTeamPresence] configurado a chamada
     * não faz nada — a instalação não tem administração.
     */
    fun openForAllAccounts() {
        if (getAdminTeamPresence == null) {
            return
        }

        val scopeChanged = !adminOverview
        this.accountKey = null
        this.accountLabel = null
        this.adminOverview = true

        if (scopeChanged) {
            _uiState.value = TeamPresenceUiState.Loading
        }

        refresh()
        startLiveLoop()
    }

    fun closeWindow() {
        liveJob?.cancel()
        liveJob = null
    }

    /**
     * Liga ou desliga o filtro "só conectados".
     *
     * Não dispara rede: a lista completa já está em memória e o filtro é uma
     * projeção dela. Uma consulta aqui faria o clique esperar a rede para mostrar
     * dados que a tela já tem.
     */
    fun setOnlyOnline(value: Boolean) {
        val current = _uiState.value
        if (current !is TeamPresenceUiState.Success || current.onlyOnline == value) {
            return
        }
        _uiState.value = current.copy(onlyOnline = value)
    }

    /** Abre ou fecha os integrantes de uma conta, por `TeamPresenceAccountGroup.groupKey`. */
    fun toggleAccount(groupKey: String) {
        val current = _uiState.value
        if (current !is TeamPresenceUiState.Success) {
            return
        }

        val expanded = current.expandedAccountKeys
        _uiState.value = current.copy(
            expandedAccountKeys = if (groupKey in expanded) {
                expanded - groupKey
            } else {
                expanded + groupKey
            }
        )
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadPresence()
        }
    }

    /**
     * Apaga um integrante no servidor e recarrega a lista.
     *
     * Recarregar em vez de tirar a linha da lista em memória: a leitura seguinte
     * é a única prova de que o servidor apagou. A falha vira aviso e a lista fica
     * intacta — mostrar como removido o que continua lá seria mentir.
     */
    fun removeMember(memberKey: String) {
        if (!adminOverview) {
            return
        }
        val useCase = removeTeamMember ?: return
        val entry = findEntry(memberKey) ?: return
        val targetAccountKey = entry.accountKey ?: return

        viewModelScope.launch {
            runAction { useCase(accountKey = targetAccountKey, deviceId = entry.deviceId) }
        }
    }

    /**
     * Apaga uma conta inteira e recarrega.
     *
     * Só faz sentido na visão global — é lá que uma conta que ninguém usa mais
     * continua aparecendo com os integrantes de antes.
     */
    fun deleteAccount(accountKey: String) {
        if (!adminOverview) {
            return
        }
        val useCase = deleteTeamAccount ?: return
        if (accountKey.isBlank()) {
            return
        }

        viewModelScope.launch {
            runAction { useCase(accountKey).map { } }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun onDestroy() {
        loadJob?.cancel()
        liveJob?.cancel()
        viewModelScope.cancel()
    }

    /**
     * Tempo real com a janela aberta.
     *
     * `loadPresence` nunca passa por `Loading` e o estado é um `data class` sobre
     * uma lista de ordem total, então um tique sem novidade produz um valor igual
     * ao anterior — o `StateFlow` não reemite e a tela não recompõe.
     */
    private fun startLiveLoop() {
        if (liveJob?.isActive == true) {
            return
        }
        liveJob = viewModelScope.launch {
            while (true) {
                delay(liveIntervalMillis)
                loadPresence()
            }
        }
    }

    /** Executa a ação destrutiva: erro vira aviso, sucesso recarrega a lista. */
    private suspend fun runAction(block: suspend () -> Result<Unit>) {
        val error = block().exceptionOrNull()
        if (error != null) {
            _actionError.value = error.message ?: UNKNOWN_ERROR_MESSAGE
            return
        }
        _actionError.value = null
        loadPresence()
    }

    private fun findEntry(memberKey: String): TeamMemberPresence? {
        val current = _uiState.value as? TeamPresenceUiState.Success ?: return null
        return current.entries.firstOrNull { entry -> entry.memberKey == memberKey }
    }

    /** Lê o escopo ativo, ou `null` quando não há nenhum apontado. */
    private suspend fun fetchScope(): Result<TeamPresenceResult>? {
        if (adminOverview) {
            val overview = getAdminTeamPresence ?: return null
            return overview()
        }

        val targetAccountKey = accountKey ?: return null
        return getTeamPresence(targetAccountKey)
    }

    private suspend fun loadPresence() {
        loadMutex.withLock {
            // Escopo capturado **antes** da chamada: alternar entre uma conta e a
            // visão global no meio de uma consulta publicaria a resposta antiga
            // sobre a nova.
            val scopeAccount = accountKey
            val scopeOverview = adminOverview
            val current = _uiState.value as? TeamPresenceUiState.Success

            (fetchScope() ?: return@withLock).fold(
                onSuccess = { result ->
                    if (scopeAccount != accountKey || scopeOverview != adminOverview) {
                        return@fold
                    }

                    val entries = result.entries
                    val contentChanged = current == null || current.entries != entries

                    _uiState.value = TeamPresenceUiState.Success(
                        entries = entries,
                        accountLabel = accountLabel,
                        isAdminOverview = scopeOverview,
                        // Estado de UI, não do servidor: sem carregá-lo daqui o
                        // filtro se desfaria a cada tique.
                        onlyOnline = current?.onlyOnline ?: false,
                        // Mesmo tratamento. Conta que sumiu da resposta sai do
                        // conjunto, senão reapareceria aberta se voltasse depois.
                        expandedAccountKeys = current?.expandedAccountKeys
                            ?.filterTo(mutableSetOf()) { key ->
                                entries.any { entry -> entry.accountKey.orEmpty() == key }
                            }
                            ?: emptySet(),
                        // Carimbo só anda quando o conteúdo muda. Marcá-lo a cada
                        // tique quebraria a igualdade do estado e recomporia a tela.
                        lastChangedAt = if (contentChanged) clock.now() else current?.lastChangedAt,
                        clockSkewSuspected = result.clockSkewSuspected,
                        clockSkewMinutes = result.clockOffsetMillis.toWholeMinutes()
                    )
                },
                onFailure = { error ->
                    // Falha intermitente com dados na tela não apaga o que o
                    // usuário está lendo.
                    if (current != null) {
                        return@fold
                    }
                    _uiState.value = TeamPresenceUiState.Error(
                        message = error.message ?: UNKNOWN_ERROR_MESSAGE,
                        accountLabel = accountLabel
                    )
                }
            )
        }
    }

    companion object {
        /** Mesma cadência das outras duas janelas ao vivo. */
        const val DEFAULT_LIVE_INTERVAL_MILLIS = 5_000L
    }
}

/**
 * Desvio em minutos inteiros, sempre positivo.
 *
 * O sinal não interessa à tela — o que ela informa é "os relógios divergem em
 * cerca de N minutos" — e arredondar é o que impede o valor de mudar a cada
 * batida e reemitir o estado.
 */
private fun Long.toWholeMinutes(): Long = absoluteValue / MILLIS_PER_MINUTE
