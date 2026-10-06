package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.USAGE_DETECTED_WINDOW_MILLIS
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.recentlyProgressed
import com.usagemonitor.domain.entity.usageProgressed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Uso detectado pela variação da cota entre leituras (#385).
 *
 * O arco de sessão ativa só acendia no Anthropic e no Codex, que têm arquivo
 * local dizendo "trabalhando agora". Para as demais fontes o único sinal é o
 * consumo avançar entre duas coletas do dashboard ([usageProgressed]); o alvo
 * fica marcado por [USAGE_DETECTED_WINDOW_MILLIS] depois do último avanço.
 *
 * **Não alimenta o polling adaptativo**: polling mais rápido produziria mais
 * leituras, mais avanços detectados e mais polling — o sinal se realimentaria.
 * Quem acelera a coleta continua sendo só o CLI ([SessionPulseViewModel]).
 *
 * Leitura que falha mantém o último `Success` publicado (regra do dashboard),
 * então não gera avanço falso: os números não mudam.
 */
class QuotaActivityTracker(
    private val dashboardState: StateFlow<UiState>,
    private val clock: Clock = Clock.System,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val pruneIntervalMillis: Long = PRUNE_INTERVAL_MILLIS,
    autoStart: Boolean = true
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lastReadings = mutableMapOf<UsageTargetKey, ApiUsageStats>()
    private val lastProgressAt = mutableMapOf<UsageTargetKey, Instant>()

    /** Coleta e envelhecimento rodam em coroutines distintas sobre os mesmos mapas. */
    private val mutex = Mutex()

    private val _detectedTargets = MutableStateFlow<Set<UsageTargetKey>>(emptySet())

    /** Alvos com consumo avançando nos últimos [USAGE_DETECTED_WINDOW_MILLIS]. */
    val detectedTargets: StateFlow<Set<UsageTargetKey>> = _detectedTargets.asStateFlow()

    init {
        if (autoStart) {
            scope.launch { dashboardState.collect { state -> onState(state) } }
            scope.launch {
                while (true) {
                    delay(pruneIntervalMillis)
                    publish(clock.now())
                }
            }
        }
    }

    /** Uma leitura do dashboard. `internal` para o teste dispensar os laços. */
    internal suspend fun onState(state: UiState) {
        val success = state as? UiState.Success ?: return
        mutex.withLock { recordReadings(success) }
    }

    private fun recordReadings(success: UiState.Success) {
        val now = clock.now()
        for (stats in success.data) {
            val previous = lastReadings[stats.targetKey]
            if (previous != null && previous != stats && usageProgressed(previous, stats)) {
                lastProgressAt[stats.targetKey] = now
            }
            lastReadings[stats.targetKey] = stats
        }
        prune(now)
    }

    internal suspend fun publish(now: Instant) {
        mutex.withLock { prune(now) }
    }

    private fun prune(now: Instant) {
        val detected = recentlyProgressed(lastProgressAt, now)
        lastProgressAt.keys.retainAll(detected)
        if (detected != _detectedTargets.value) {
            _detectedTargets.value = detected
        }
    }

    fun onDestroy() {
        scope.cancel()
    }

    private companion object {
        /** Só envelhece o sinal; meio minuto é precisão de sobra para um prazo de 10. */
        const val PRUNE_INTERVAL_MILLIS = 30_000L
    }
}
