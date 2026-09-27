package com.usagemonitor.presentation.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class DashboardViewModelConfig(
    val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Cadência com sessão CLI ativa (issue #269). O Codenotch coleta a 60 s; o
     * ai-usagebar diz que os endpoints da Anthropic e do Codex limitam abaixo de
     * ~300 s. Nenhum dos dois mediu: se a trilha mostrar 429, o degrau seguinte é
     * 300 s, e o ajuste é esta linha.
     */
    val activePollInterval: Duration = 60.seconds,
    /** Cadência sem nenhuma sessão rodando: uso não anda enquanto nada o usa. */
    val idlePollInterval: Duration = 5.minutes,
    /**
     * Intervalo entre contas Anthropic devidas no mesmo tique. O ai-usagebar
     * registrou 429 com várias contas batendo juntas nos endpoints de uso e de
     * token; espaçá-las custa menos de um segundo por conta.
     */
    val anthropicStagger: Duration = 800.milliseconds,
    /** Espera que terminou esse tanto além do pedido é volta do sleep: tudo fica devido. */
    val sleepJumpThreshold: Duration = 2.minutes,
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
