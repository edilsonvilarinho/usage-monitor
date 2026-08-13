package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.usecase.GetActiveCliSessionPulsesUseCase
import com.usagemonitor.domain.usecase.GetActiveTeamSessionPulseUseCase
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
import kotlinx.coroutines.flow.first
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
 * O laço espera a janela ficar visível antes de cada passada: com o app
 * minimizado ninguém vê o pisca, e insistir custaria uma requisição por conta a
 * cada intervalo sem nada em troca.
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
    /** Contas participantes do time; vazio zera os pulsos do botão de time. */
    private val teamTargetsProvider: () -> List<TeamPulseTarget> = { emptyList() },
    private val isAppVisible: StateFlow<Boolean> = MutableStateFlow(true),
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.System,
    autoStart: Boolean = true
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loopJob: Job? = null

    private val _cliPulses = MutableStateFlow<Map<UsageTargetKey, SessionPulse>>(emptyMap())

    /** Sessões desta máquina, por card. Chave ausente significa botão normal. */
    val cliPulses: StateFlow<Map<UsageTargetKey, SessionPulse>> = _cliPulses.asStateFlow()

    private val _teamPulses = MutableStateFlow<Map<UsageTargetKey, SessionPulse>>(emptyMap())

    /** Sessões de todo o time na conta do card, incluindo as desta máquina. */
    val teamPulses: StateFlow<Map<UsageTargetKey, SessionPulse>> = _teamPulses.asStateFlow()

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
                if (!isAppVisible.value) {
                    isAppVisible.first { visible -> visible }
                }
                refreshOnce()
                delay(intervalMillis)
            }
        }
    }

    fun onDestroy() {
        loopJob?.cancel()
        loopJob = null
        viewModelScope.cancel()
    }

    /** Uma passada completa. `internal` para o teste dispensar o laço. */
    internal suspend fun refreshOnce() {
        // Sem indexar aqui a latência do semáforo seria a do laço de background
        // (10min), não a deste laço: a leitura só enxerga turno já indexado.
        // Falha de indexação não impede a leitura do que já está no índice.
        syncCliSessionIndex?.invoke()

        val now = clock.now()
        refreshCliPulses(now)
        refreshTeamPulses(now)
    }

    private suspend fun refreshCliPulses(now: Instant) {
        // Leitura falhou: mantém o mapa anterior, que envelhece no `prunedAt`.
        val pulses = getCliPulses().getOrNull()
            ?.mapKeys { (profileId, _) -> UsageTargetKey(ApiSource.ANTHROPIC, profileId) }
            ?: _cliPulses.value

        _cliPulses.value = pulses.prunedAt(now)
    }

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
