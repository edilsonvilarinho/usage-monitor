package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.breadcrumbReasonOf
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.DEFAULT_STALL_THRESHOLD_MILLIS
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.StalledCliSession
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.usecase.GetActiveCliSessionPulsesUseCase
import com.usagemonitor.domain.usecase.GetActiveTeamSessionPulseUseCase
import com.usagemonitor.domain.usecase.GetStalledCliSessionsUseCase
import com.usagemonitor.domain.usecase.SyncCliSessionIndexUseCase
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Conta do time que um perfil local representa, para a leitura do semáforo. */
data class TeamPulseTarget(
    val profileId: String,
    /** `accountUuid` da conta — a mesma chave que agrupa as máquinas no servidor. */
    val accountKey: String
)

/**
 * Semáforo de saúde das sessões em curso, para os botões dos cards do dashboard.
 *
 * Uma passada lê o índice local (todas as contas de uma vez) e, quando a
 * integração está ligada, o servidor de time de cada conta participante. O
 * resultado é publicado por [UsageTargetKey] porque é assim que o grid de cards
 * indexa tudo.
 *
 * Com a janela minimizada a passada continua, mas **só na parte local**: a
 * leitura do índice é do próprio disco e alimenta o alerta de sessão saturada,
 * que existe justamente para chegar a quem não está olhando a tela. O que fica
 * suspenso é a leitura do time — uma requisição por conta a cada intervalo sem
 * ninguém para ver o pisca. Os pulsos de time guardados envelhecem mesmo assim,
 * senão voltariam acesos ao restaurar a janela.
 *
 * Uma leitura que falha **não apaga** o pulso anterior: um soluço de rede
 * deixaria o botão piscando de forma intermitente sem que nada tivesse mudado nas
 * sessões. O valor antigo envelhece sozinho porque toda publicação passa por
 * [SessionPulse.prunedAt] — passados os minutos da janela de atividade ele se
 * esvazia por conta própria.
 */
class SessionPulseViewModel(
    private val getCliPulses: GetActiveCliSessionPulsesUseCase,
    private val getTeamPulse: GetActiveTeamSessionPulseUseCase,
    /**
     * Atualiza o índice antes de ler. `null` desliga — a passada passa a enxergar
     * apenas o que outro laço já indexou.
     */
    private val syncCliSessionIndex: SyncCliSessionIndexUseCase? = null,
    /**
     * Sessões sem resposta há tempo demais. `null` desliga a detecção — a
     * instalação passa a se comportar como antes dela existir.
     */
    private val getStalledSessions: GetStalledCliSessionsUseCase? = null,
    /**
     * Limiar corrente da detecção, lido a cada passada.
     *
     * Provider e não valor fixo pelo mesmo motivo de [teamTargetsProvider]: a
     * escolha vive nas preferências e muda sem recriar o view model.
     */
    private val stallThresholdProvider: () -> Long = { DEFAULT_STALL_THRESHOLD_MILLIS },
    /** Contas participantes do time; vazio zera os pulsos do botão de time. */
    private val teamTargetsProvider: () -> List<TeamPulseTarget> = { emptyList() },
    private val isAppVisible: StateFlow<Boolean> = MutableStateFlow(true),
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.System,
    autoStart: Boolean = true,
    /** Trilha do relatório de bug; ver [BreadcrumbRecorder]. */
    private val breadcrumbs: BreadcrumbRecorder = NoOpBreadcrumbRecorder
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loopJob: Job? = null

    private val _cliPulses = MutableStateFlow<Map<UsageTargetKey, SessionPulse>>(emptyMap())

    /** Sessões desta máquina, por card. Chave ausente significa botão normal. */
    val cliPulses: StateFlow<Map<UsageTargetKey, SessionPulse>> = _cliPulses.asStateFlow()

    private val _teamPulses = MutableStateFlow<Map<UsageTargetKey, SessionPulse>>(emptyMap())

    /** Sessões de todo o time na conta do card, incluindo as desta máquina. */
    val teamPulses: StateFlow<Map<UsageTargetKey, SessionPulse>> = _teamPulses.asStateFlow()

    private val _stalledSessions = MutableStateFlow<List<StalledCliSession>>(emptyList())

    /**
     * Sessões desta máquina cujo último pedido ficou sem resposta.
     *
     * Vive neste laço, e não num próprio, porque a passada local continua rodando
     * com a janela minimizada — que é exatamente o destinatário do aviso: quem
     * deixou automação rodando e não está olhando a tela.
     */
    val stalledSessions: StateFlow<List<StalledCliSession>> = _stalledSessions.asStateFlow()

    init {
        if (autoStart) {
            start()
        }
    }

    /** Idempotente: chamar com o laço rodando não abre um segundo. */
    fun start() {
        if (loopJob?.isActive == true) {
            return
        }
        loopJob = viewModelScope.launch {
            while (true) {
                refreshOnce(includeTeam = isAppVisible.value)
                delay(intervalMillis)
            }
        }
    }

    fun onDestroy() {
        loopJob?.cancel()
        loopJob = null
        viewModelScope.cancel()
    }

    /**
     * Uma passada. `internal` para o teste dispensar o laço.
     *
     * [includeTeam] falso pula a ida ao servidor e apenas envelhece o que já
     * estava publicado.
     */
    internal suspend fun refreshOnce(includeTeam: Boolean = true) {
        // Sem indexar aqui a latência do semáforo seria a do laço de background
        // (10min), não a deste laço: a leitura só enxerga turno já indexado.
        // Falha de indexação não impede a leitura do que já está no índice.
        syncCliSessionIndex?.invoke()

        val now = clock.now()
        refreshCliPulses(now)
        refreshStalledSessions()
        if (includeTeam) {
            refreshTeamPulses(now)
        } else {
            ageTeamPulses(now)
        }
    }

    /** Envelhece sem ler: o que passou da janela de atividade some sozinho. */
    private fun ageTeamPulses(now: Instant) {
        val aged = _teamPulses.value.prunedAt(now)
        if (aged != _teamPulses.value) {
            _teamPulses.value = aged
        }
    }

    private suspend fun refreshCliPulses(now: Instant) {
        val result = getCliPulses()

        // Este laço roda de 30 em 30 segundos: sem a deduplicação, uma leitura
        // quebrada escreveria 120 passos por hora e expulsaria da trilha tudo o
        // que explica o defeito. A anotação sai na PRIMEIRA falha e só volta a
        // sair se o motivo mudar ou se a leitura voltar a funcionar antes de
        // falhar de novo.
        //
        // Um `var` simples basta e não há corrida: quem chama isto é o laço
        // único de [start], sempre na mesma coroutine.
        val failure = result.exceptionOrNull()
        if (failure == null) {
            lastCliPulseFailure = null
        } else {
            val reason = breadcrumbReasonOf(failure)
            if (reason != lastCliPulseFailure) {
                lastCliPulseFailure = reason
                breadcrumbs.record(
                    BreadcrumbCategory.ERROR,
                    "semáforo de sessões não pôde ser lido: $reason"
                )
            }
        }

        // Leitura falhou: mantém o mapa anterior, que envelhece no `prunedAt`.
        val pulses = result.getOrNull()
            ?.mapKeys { (profileId, _) -> UsageTargetKey(ApiSource.ANTHROPIC, profileId) }
            ?: _cliPulses.value

        _cliPulses.value = pulses.prunedAt(now)
    }

    /** Ver [refreshCliPulses]: motivo da última falha anotada, para não repeti-la. */
    private var lastCliPulseFailure: String? = null

    /**
     * Leitura que falha **mantém** a lista anterior, como o pulso: a cauda de um
     * transcript pode estar sendo escrita neste instante, e apagar a lista faria o
     * aviso piscar sem nada ter mudado nas sessões. O valor antigo não envelhece
     * sozinho aqui — quem o descarta é o teto de idade do próprio detector.
     */
    private suspend fun refreshStalledSessions() {
        val useCase = getStalledSessions ?: return

        val result = useCase(stallThresholdProvider())

        // Mesma deduplicação da trilha do semáforo: 120 passadas por hora e uma
        // leitura quebrada expulsariam da trilha tudo o que explica o defeito.
        val failure = result.exceptionOrNull()
        if (failure == null) {
            lastStalledFailure = null
        } else {
            val reason = breadcrumbReasonOf(failure)
            if (reason != lastStalledFailure) {
                lastStalledFailure = reason
                breadcrumbs.record(
                    BreadcrumbCategory.ERROR,
                    "sessões sem resposta não puderam ser lidas: $reason"
                )
            }
        }

        val stalled = result.getOrNull() ?: return
        if (stalled != _stalledSessions.value) {
            _stalledSessions.value = stalled
        }
    }

    /** Ver [refreshStalledSessions]. */
    private var lastStalledFailure: String? = null

    private suspend fun refreshTeamPulses(now: Instant) {
        val targets = teamTargetsProvider()
        if (targets.isEmpty()) {
            if (_teamPulses.value.isNotEmpty()) {
                _teamPulses.value = emptyMap()
            }
            return
        }

        val previous = _teamPulses.value
        val updated = mutableMapOf<UsageTargetKey, SessionPulse>()
        for (target in targets) {
            val key = UsageTargetKey(ApiSource.ANTHROPIC, target.profileId)
            val pulse = getTeamPulse(target.accountKey).getOrElse { previous[key] ?: SessionPulse.EMPTY }
            updated[key] = pulse
        }
        _teamPulses.value = updated.prunedAt(now)
    }

    companion object {
        /**
         * Cadência da passada.
         *
         * A janela avaliada é de minutos, então meio minuto é precisão de sobra —
         * e mantém o tráfego para o servidor de time no mesmo patamar do envio,
         * que já roda de 30 em 30 segundos.
         */
        const val DEFAULT_INTERVAL_MILLIS = 30_000L
    }
}

/** Envelhece cada pulso e descarta os que ficaram vazios. */
private fun Map<UsageTargetKey, SessionPulse>.prunedAt(now: Instant): Map<UsageTargetKey, SessionPulse> {
    return mapValues { (_, pulse) -> pulse.prunedAt(now) }
        .filterValues { pulse -> pulse.isPulsing }
}
