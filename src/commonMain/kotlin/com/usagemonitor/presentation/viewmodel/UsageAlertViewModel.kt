package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.StalledCliSession
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageAlertState
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.evaluateUsageAlerts
import com.usagemonitor.domain.entity.mergeSessionPulses
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Traduz o estado do dashboard e do semáforo em alertas para fora da janela.
 *
 * Não tem laço próprio: reage às emissões que já existem — o polling de dez
 * minutos do [DashboardViewModel] e a passada de trinta segundos do
 * [SessionPulseViewModel]. Um laço adicional só reavaliaria dados idênticos.
 *
 * A deduplicação mora em [UsageAlertState], não aqui: as fontes reemitem o mesmo
 * valor com frequência e cada reemissão passa por esta avaliação.
 */
class UsageAlertViewModel(
    dashboardState: StateFlow<UiState>,
    cliPulses: StateFlow<Map<UsageTargetKey, SessionPulse>>,
    alertSettings: StateFlow<UsageAlertSettings>,
    /** Sessões sem resposta, publicadas pelo mesmo laço do semáforo. */
    stalledSessions: StateFlow<List<StalledCliSession>> = MutableStateFlow(emptyList()),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.of(ALERT_TIME_ZONE_ID),
    autoStart: Boolean = true
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var collectJob: Job? = null

    private val _alerts = MutableSharedFlow<UsageAlert>(
        replay = 0,
        extraBufferCapacity = ALERT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Alertas a entregar, um a um.
     *
     * `replay = 0` de propósito: um alerta guardado seria reentregue a cada novo
     * coletor, e notificação repetida por motivo interno é ruído.
     */
    val alerts: SharedFlow<UsageAlert> = _alerts.asSharedFlow()

    private val _worstRisk = MutableStateFlow<UsageRiskLevel?>(null)

    /** Pior risco entre as cotas visíveis; `null` quando nenhuma está em risco. */
    val worstRisk: StateFlow<UsageRiskLevel?> = _worstRisk.asStateFlow()

    private val _worstSnapshot = MutableStateFlow<WorstQuotaSnapshot?>(null)

    /**
     * A cota que produz [worstRisk], com a fonte e a projeção dela — para a
     * barra HUD (issue #164), que precisa dizer qual fonte e quando ela
     * reseta, não só o nível. Ao lado de [worstRisk], não em vez: a bandeja
     * continua lendo só o nível.
     */
    internal val worstSnapshot: StateFlow<WorstQuotaSnapshot?> = _worstSnapshot.asStateFlow()

    private var state = UsageAlertState.EMPTY

    /** `Triple` não comporta a quarta fonte, e um `Pair` de `Pair` não se lê. */
    private data class AlertInputs(
        val dashboard: UiState,
        val pulses: Map<UsageTargetKey, SessionPulse>,
        val settings: UsageAlertSettings,
        val stalled: List<StalledCliSession>
    )

    private val sources = combine(
        dashboardState,
        cliPulses,
        alertSettings,
        stalledSessions
    ) { dashboard, pulses, settings, stalled -> AlertInputs(dashboard, pulses, settings, stalled) }

    init {
        if (autoStart) {
            start()
        }
    }

    /** Idempotente: chamar com a coleta ativa não abre uma segunda. */
    fun start() {
        if (collectJob?.isActive == true) {
            return
        }
        collectJob = viewModelScope.launch {
            sources.collect { inputs ->
                evaluate(inputs.dashboard, inputs.pulses, inputs.settings, inputs.stalled)
            }
        }
    }

    fun onDestroy() {
        collectJob?.cancel()
        collectJob = null
        viewModelScope.cancel()
    }

    /** Uma avaliação. `internal` para o teste dispensar as flows. */
    internal suspend fun evaluate(
        dashboard: UiState,
        pulses: Map<UsageTargetKey, SessionPulse>,
        settings: UsageAlertSettings,
        stalledSessions: List<StalledCliSession> = emptyList()
    ) {
        val success = dashboard as? UiState.Success

        _worstRisk.value = success?.riskSummaries
            ?.values
            ?.flatMap { byQuota -> byQuota.values }
            ?.maxOfOrNull { summary -> summary.level }
            ?.takeIf { level -> level != UsageRiskLevel.ON_TRACK }

        val now = clock.now()

        _worstSnapshot.value = worstQuotaSnapshot(
            stats = success?.data.orEmpty(),
            riskSummaries = success?.riskSummaries.orEmpty(),
            now = now
        )
        val evaluation = evaluateUsageAlerts(
            stats = success?.data.orEmpty(),
            sessionPulse = pulses.values.mergeSessionPulses(),
            previous = state,
            settings = settings,
            now = now,
            currentLocalHour = now.toLocalDateTime(timeZone).hour,
            stalledSessions = stalledSessions
        )

        state = evaluation.state
        for (alert in evaluation.alerts) {
            _alerts.emit(alert)
        }
    }

    companion object {
        /** Mesmo fuso que o resto da apresentação usa para reset de quota. */
        const val ALERT_TIME_ZONE_ID = "America/Sao_Paulo"

        /**
         * Espaço para uma rajada de alertas sem bloquear a avaliação.
         *
         * Uma passada pode emitir vários limiares de várias contas de uma vez, e
         * quem consome é a bandeja — um destino que pode estar lento.
         */
        const val ALERT_BUFFER_CAPACITY = 64
    }
}
