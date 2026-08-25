package com.usagemonitor.presentation.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class DashboardViewModelConfig(
    val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val pollInterval: Duration = 600.seconds,
    val updateCheckIntervalWhileRunning: Duration = 10.minutes,
    val perSourceTimeout: Duration = 20.seconds,
    /**
     * Folga somada ao `periodEndAt` antes de coletar por causa de um reset.
     *
     * O reset da Anthropic não é instantâneo: bater no endpoint no milissegundo
     * exato do vencimento tende a devolver ainda a janela velha.
     */
    val quotaResetGrace: Duration = 20.seconds,
    val maxConcurrentSourceFetches: Int = 3,
    val autoStartInitialFetch: Boolean = true,
    val autoStartCountdown: Boolean = true,
    val autoStartUpdateChecks: Boolean = true,
    /**
     * Espera entre tentativas de baixar a mesma versão, por tentativa.
     *
     * O tamanho da lista é o teto de tentativas: esgotada, a versão para de ser
     * tentada até uma release nova ser anunciada. Sem isso, uma falha recorrente
     * rebaixaria ~120 MB a cada ciclo de 10 min — algo como 17 GB por dia.
     */
    val updateRetryBackoff: List<Duration> = listOf(30.minutes, 2.hours, 6.hours)
)
