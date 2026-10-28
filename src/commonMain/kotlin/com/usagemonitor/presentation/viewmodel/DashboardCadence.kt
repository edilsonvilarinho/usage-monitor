package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.UsageTargetKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlin.time.Duration

/**
 * O sinal que escolhe a cadência de cada alvo do [DashboardViewModel]: 60 s ocupado, 5 min parado.
 *
 * Dois sinais deixam um alvo ocupado:
 * - **sessão CLI rodando** (issue #269), global como no Codenotch: acelera todos os alvos;
 * - **uso detectado pela cota** (#478, `QuotaActivityTracker`), só do próprio alvo. É o único sinal
 *   de OpenCode Go, MiniMax, DeepSeek, Cursor e das outras fontes sem arquivo local; sem ele ficavam
 *   em 5 min com uso acontecendo. A #385 o mantinha fora do polling para o sinal não se realimentar,
 *   mas o laço é limitado: a cota só sobe com uso real, e parado o uso o alvo volta a 5 min quando o
 *   sinal vence (`USAGE_DETECTED_WINDOW_MILLIS`). Por isso não entra no sinal global — uma fonte
 *   andando não justifica bater nas outras a cada minuto.
 *
 * Mora fora do view model pelo limite de 800 linhas. As regras de tempo continuam nas funções
 * puras de `RefreshSchedule.kt`; aqui só se decide **se** um alvo está ocupado.
 */
internal class DashboardCadence(
    private val isBusy: StateFlow<Boolean>,
    private val quotaActiveTargets: StateFlow<Set<UsageTargetKey>>,
    private val config: DashboardViewModelConfig
) {
    private val _currentPollInterval = MutableStateFlow(intervalFor(anyBusy(isBusy.value, quotaActiveTargets.value)))

    /**
     * A cadência mais curta em vigor, 60 s ou 5 min: é a volta inteira do relógio da contagem no
     * balão da engrenagem (#293), que conta até a próxima coleta de qualquer alvo.
     */
    val currentPollInterval: StateFlow<Duration> = _currentPollInterval.asStateFlow()

    fun isBusy(target: UsageTargetKey): Boolean = isBusy.value || target in quotaActiveTargets.value

    /** A cadência quando nenhum alvo habilitado dá prazo — sem alvo, o laço ainda precisa de um. */
    fun fallbackInterval(): Duration = intervalFor(anyBusy(isBusy.value, quotaActiveTargets.value))

    /**
     * Republica a cadência e chama [onChange] a cada mudança de um dos sinais: um alvo que passa a
     * ocupado pode já estar devido. Suspende enquanto o escopo viver.
     */
    suspend fun collectChanges(onChange: () -> Unit) {
        combine(isBusy, quotaActiveTargets, ::anyBusy).collect { busy ->
            _currentPollInterval.value = intervalFor(busy)
            onChange()
        }
    }

    private fun anyBusy(cliBusy: Boolean, detected: Set<UsageTargetKey>): Boolean = cliBusy || detected.isNotEmpty()

    private fun intervalFor(busy: Boolean): Duration =
        if (busy) config.activePollInterval else config.idlePollInterval
}
