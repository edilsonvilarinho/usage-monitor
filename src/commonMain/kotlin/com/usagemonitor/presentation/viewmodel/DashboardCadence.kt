package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.UsageTargetKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/**
 * O sinal que escolhe a cadência de cada alvo do [DashboardViewModel] (issue #269): 60 s com
 * sessão CLI rodando, 5 min sem.
 *
 * Mora fora do view model pelo limite de 800 linhas. As regras de tempo continuam nas funções
 * puras de `RefreshSchedule.kt`; aqui só se decide **se** um alvo está ocupado.
 */
internal class DashboardCadence(
    /** Há sessão CLI rodando. Global, como no Codenotch. */
    private val isBusy: StateFlow<Boolean>,
    private val config: DashboardViewModelConfig
) {
    private val _currentPollInterval = MutableStateFlow(intervalFor(isBusy.value))

    /**
     * A cadência em vigor, 60 s ou 5 min: é a volta inteira do relógio da contagem no
     * balão da engrenagem (#293).
     */
    val currentPollInterval: StateFlow<Duration> = _currentPollInterval.asStateFlow()

    fun isBusy(@Suppress("UNUSED_PARAMETER") target: UsageTargetKey): Boolean = isBusy.value

    /** A cadência quando nenhum alvo habilitado dá prazo — sem alvo, o laço ainda precisa de um. */
    fun fallbackInterval(): Duration = intervalFor(isBusy.value)

    /** Republica a cadência e chama [onChange] a cada mudança do sinal. Suspende enquanto o escopo viver. */
    suspend fun collectChanges(onChange: () -> Unit) {
        isBusy.collect { busy ->
            _currentPollInterval.value = intervalFor(busy)
            onChange()
        }
    }

    private fun intervalFor(busy: Boolean): Duration =
        if (busy) config.activePollInterval else config.idlePollInterval
}
