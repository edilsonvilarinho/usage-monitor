package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.RATE_LIMIT_BACKOFF_SLACK
import com.usagemonitor.domain.entity.hasQuotaResetSince
import com.usagemonitor.domain.entity.isTargetDue
import com.usagemonitor.domain.entity.nextDueAt
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.rateLimitBackoff
import kotlin.time.Instant
import kotlin.time.Duration

/**
 * O estado de agendamento por alvo do [DashboardViewModel] (issue #269).
 *
 * Mora fora do view model pelo limite de 800 linhas, e porque é o único estado
 * dele que sobrevive ao processo: o prazo de backoff é gravado a cada mudança por
 * [onBackoffChanged]. Sem isso reiniciar o app zerava a punição, e o primeiro
 * poll voltava a bater num endpoint que ainda estava limitando — "the app was the
 * thing sustaining its own punishment", nas palavras do Codenotch.
 *
 * As regras de tempo são as funções puras de `RefreshSchedule.kt`; aqui só se
 * guarda o que elas precisam lembrar. Os métodos não suspendem e são chamados de
 * várias corrotinas de coleta, então todo acesso passa por [lock].
 */
internal class DashboardRefreshScheduler(
    initialBackoffs: Map<UsageTargetKey, Instant> = emptyMap(),
    private val onBackoffChanged: (Map<UsageTargetKey, Instant>) -> Unit = {},
    /**
     * Última tentativa assumida para alvo ainda não tentado neste processo. É o
     * prazo persistido do arranque anterior menos a cadência ociosa: reabrir o app
     * dentro dela não coleta de novo, e o alvo fica devido no prazo gravado.
     */
    private val initialAttemptAnchor: Instant? = null
) {
    private val lock = Any()
    private val backoffUntil = initialBackoffs.toMutableMap()
    private val consecutiveRateLimits = mutableMapOf<UsageTargetKey, Int>()
    private val lastSuccessAt = mutableMapOf<UsageTargetKey, Instant>()
    private val lastAttemptAt = mutableMapOf<UsageTargetKey, Instant>()

    /** Marca a tentativa no despacho, não no fim: o laço não pode escolher o mesmo alvo duas vezes. */
    fun recordAttempt(targets: Collection<UsageTargetKey>, at: Instant) {
        synchronized(lock) {
            targets.forEach { target -> lastAttemptAt[target] = at }
        }
    }

    /** Volta do sleep: nenhuma tentativa anterior vale, e todo alvo fica devido. */
    fun forgetAttempts() {
        synchronized(lock) {
            lastAttemptAt.clear()
            anchorConsumed = true
        }
    }

    @Volatile private var anchorConsumed = false

    private fun attemptOf(target: UsageTargetKey): Instant? {
        return lastAttemptAt[target] ?: initialAttemptAnchor.takeUnless { anchorConsumed }
    }

    /** Os alvos de [targets] devidos em [now] (`isTargetDue`), com os resets lidos de [statsOf]. */
    fun dueTargets(
        targets: Set<UsageTargetKey>,
        statsOf: (UsageTargetKey) -> ApiUsageStats?,
        now: Instant,
        busy: Boolean,
        config: DashboardViewModelConfig
    ): DueTargets {
        return synchronized(lock) {
            val due = linkedSetOf<UsageTargetKey>()
            val byReset = linkedSetOf<UsageTargetKey>()
            targets.forEach { target ->
                val attempt = attemptOf(target)
                val rolledOver = hasQuotaResetSince(statsOf(target), attempt, now, config.quotaResetGrace)
                val isDue = isTargetDue(
                    now = now,
                    lastAttemptAt = attempt,
                    backoffUntil = backoffUntil[target],
                    busy = busy,
                    resetRolledOver = rolledOver,
                    activeInterval = config.activePollInterval,
                    idleInterval = config.idlePollInterval
                )
                if (isDue) {
                    due += target
                    if (rolledOver) byReset += target
                }
            }
            DueTargets(due, byReset)
        }
    }

    /** A próxima coleta por cadência entre [targets] — o instante que o rodapé e a HUD contam. */
    fun nextPollAt(targets: Set<UsageTargetKey>, now: Instant, busy: Boolean, config: DashboardViewModelConfig): Instant? {
        return synchronized(lock) {
            targets.minOfOrNull { target ->
                nextDueAt(
                    now = now,
                    lastAttemptAt = attemptOf(target),
                    backoffUntil = backoffUntil[target],
                    busy = busy,
                    activeInterval = config.activePollInterval,
                    idleInterval = config.idlePollInterval
                )
            }
        }
    }

    /** O prazo de backoff de [target], se ainda estiver valendo em [now]. */
    fun backoffUntil(target: UsageTargetKey, now: Instant): Instant? {
        return synchronized(lock) {
            backoffUntil[target]?.takeIf { until -> until > now }
        }
    }

    fun isInBackoff(target: UsageTargetKey, now: Instant): Boolean = backoffUntil(target, now) != null

    /**
     * Arma o backoff depois de um 429 e devolve a decisão, para o breadcrumb
     * registrar o que foi recebido e o que foi calculado.
     */
    fun recordRateLimit(target: UsageTargetKey, retryAfter: Duration?, now: Instant): RateLimitDecision {
        val decision = synchronized(lock) {
            val attempt = consecutiveRateLimits[target] ?: 0
            consecutiveRateLimits[target] = attempt + 1
            val wait = rateLimitBackoff(attempt, retryAfter)
            val until = now + wait + RATE_LIMIT_BACKOFF_SLACK
            backoffUntil[target] = until
            RateLimitDecision(attempt = attempt, retryAfter = retryAfter, wait = wait, until = until)
        }
        publishBackoffs()
        return decision
    }

    /**
     * Quando a leitura em tela de [target] foi coletada. Guardado aqui, e não
     * carimbado no `ApiUsageStats` a cada sucesso: um carimbo novo por coleta faria
     * duas leituras iguais serem objetos diferentes, e o `StateFlow` reemitiria a
     * tela inteira a cada poll. Só a leitura mantida depois de falha e o cache em
     * disco levam o instante.
     */
    fun lastSuccessAt(target: UsageTargetKey): Instant? = synchronized(lock) { lastSuccessAt[target] }

    /** Coleta boa zera a sequência de 429 e o prazo. */
    fun recordSuccess(target: UsageTargetKey, at: Instant) {
        val changed = synchronized(lock) {
            lastSuccessAt[target] = at
            consecutiveRateLimits.remove(target)
            backoffUntil.remove(target) != null
        }
        if (changed) {
            publishBackoffs()
        }
    }

    /** Alvo desabilitado não guarda prazo: religá-lo não pode nascer punido. */
    fun retainOnly(enabled: Set<UsageTargetKey>) {
        val changed = synchronized(lock) {
            consecutiveRateLimits.keys.retainAll(enabled)
            lastSuccessAt.keys.retainAll(enabled)
            lastAttemptAt.keys.retainAll(enabled)
            backoffUntil.keys.retainAll(enabled)
        }
        if (changed) {
            publishBackoffs()
        }
    }

    private fun publishBackoffs() {
        val snapshot = synchronized(lock) { backoffUntil.toMap() }
        onBackoffChanged(snapshot)
    }
}

/** O que um 429 armou: a tentativa (a partir de zero), o `Retry-After` recebido, a espera e o prazo. */
internal data class RateLimitDecision(
    val attempt: Int,
    val retryAfter: Duration?,
    val wait: Duration,
    val until: Instant
)

/** Os alvos devidos num tique, e quais deles o são por um reset — esses não esperam a janela ficar visível. */
internal data class DueTargets(
    val due: Set<UsageTargetKey>,
    val byReset: Set<UsageTargetKey>
)
