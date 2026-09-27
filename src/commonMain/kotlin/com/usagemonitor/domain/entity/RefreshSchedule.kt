package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Regras puras do agendamento da coleta (issue #269). Nenhuma lê relógio nem
// estado: quem as chama passa o instante, e por isso são testáveis sem coroutine.

/** Primeira espera depois de um 429. */
val RATE_LIMIT_BACKOFF_BASE: Duration = 60.seconds

/** Teto da espera, com ou sem `Retry-After` maior. */
val RATE_LIMIT_BACKOFF_CAP: Duration = 15.minutes

private const val RATE_LIMIT_BACKOFF_MAX_EXPONENT = 4

/**
 * Folga somada ao prazo de backoff. Sem ela, 60 s de espera e 60 s de tique
 * entram em ressonância: o laço acorda no milissegundo anterior ao prazo, pula
 * o alvo e só volta a tentar um tique inteiro depois.
 */
val RATE_LIMIT_BACKOFF_SLACK: Duration = 1.seconds

/**
 * Idade máxima de uma leitura mantida depois de falhas. Passado o prazo o card
 * mostra o erro real: semanas sem rede ou sem credencial não podem continuar
 * exibindo números históricos como se fossem atuais.
 */
val MAX_STALE_READING_AGE: Duration = 7.days

/**
 * A espera depois do [attempt]-ésimo 429 seguido, contado a partir de zero:
 * 60 → 120 → 240 → 480 → 900 s.
 *
 * O `Retry-After` só aumenta a espera, e o teto vale para os dois. É a fórmula do
 * Codenotch (`ClaudeOAuthProvider.backoff`).
 */
fun rateLimitBackoff(attempt: Int, retryAfter: Duration?): Duration {
    val exponent = attempt.coerceIn(0, RATE_LIMIT_BACKOFF_MAX_EXPONENT)
    val exponential = RATE_LIMIT_BACKOFF_BASE * (1 shl exponent)
    val requested = retryAfter?.takeIf { it > Duration.ZERO } ?: Duration.ZERO
    return maxOf(exponential, requested).coerceAtMost(RATE_LIMIT_BACKOFF_CAP)
}

/** Se a leitura coletada em [fetchedAt] ainda pode ser mostrada em [now]. */
fun isReadingFreshEnough(fetchedAt: Instant?, now: Instant): Boolean {
    if (fetchedAt == null) {
        return true
    }
    return now - fetchedAt <= MAX_STALE_READING_AGE
}

/**
 * Serializa os prazos de backoff para as preferências: uma linha por alvo,
 * `chave=epochMillis`. A chave é `UsageTargetKey.storageKey`, que não contém
 * quebra de linha; a divisão usa o **último** `=` para não depender do id do perfil.
 */
fun encodeRateLimitBackoffs(backoffs: Map<UsageTargetKey, Instant>): String {
    return backoffs.entries
        .sortedBy { entry -> entry.key.storageKey }
        .joinToString(separator = "\n") { entry -> "${entry.key.storageKey}=${entry.value.toEpochMilliseconds()}" }
}

/** Inverso de [encodeRateLimitBackoffs]. Linha ilegível é descartada, e prazo vencido também. */
fun decodeRateLimitBackoffs(encoded: String?, now: Instant): Map<UsageTargetKey, Instant> {
    if (encoded.isNullOrBlank()) {
        return emptyMap()
    }
    val result = mutableMapOf<UsageTargetKey, Instant>()
    encoded.lineSequence().forEach { line ->
        val separator = line.lastIndexOf('=')
        if (separator <= 0) {
            return@forEach
        }
        val target = UsageTargetKey.fromStorageKey(line.substring(0, separator)) ?: return@forEach
        val millis = line.substring(separator + 1).toLongOrNull() ?: return@forEach
        val until = Instant.fromEpochMilliseconds(millis)
        if (until > now) {
            result[target] = until
        }
    }
    return result
}

/**
 * Se um alvo deve ser coletado em [now] — o `shouldRefresh` do
 * Codenotch, por alvo (issue #269):
 * - em backoff, nunca;
 * - nunca tentado, sempre;
 * - com um reset vencido desde a última tentativa, sempre;
 * - senão, quando a espera da cadência corrente passou: [activeInterval] com
 *   sessão CLI rodando, [idleInterval] sem nenhuma. Uso não anda enquanto nada o
 *   usa, e bater no endpoint numa tarde parada só gasta orçamento de rate limit.
 */
fun isTargetDue(
    now: Instant,
    lastAttemptAt: Instant?,
    backoffUntil: Instant?,
    busy: Boolean,
    resetRolledOver: Boolean,
    activeInterval: Duration,
    idleInterval: Duration
): Boolean {
    if (backoffUntil != null && backoffUntil > now) {
        return false
    }
    if (lastAttemptAt == null || resetRolledOver) {
        return true
    }
    val interval = if (busy) activeInterval else idleInterval
    return now - lastAttemptAt >= interval
}

/** Quando o alvo fica devido pela cadência, sem contar reset. O prazo de backoff empurra para depois. */
fun nextDueAt(
    now: Instant,
    lastAttemptAt: Instant?,
    backoffUntil: Instant?,
    busy: Boolean,
    activeInterval: Duration,
    idleInterval: Duration
): Instant {
    val interval = if (busy) activeInterval else idleInterval
    val byCadence = lastAttemptAt?.plus(interval) ?: now
    return if (backoffUntil != null && backoffUntil > byCadence) backoffUntil else byCadence
}

/**
 * Se alguma cota de [stats] reiniciou depois da última tentativa — o
 * `hasWindowRolledOver` do Codenotch. O reset só conta com [grace] somada: o da
 * Anthropic não é instantâneo, e bater no milissegundo do vencimento devolve a
 * janela velha. Reset sem data conhecida é sentinela e nunca conta.
 */
fun hasQuotaResetSince(stats: ApiUsageStats?, lastAttemptAt: Instant?, now: Instant, grace: Duration): Boolean {
    if (stats == null || lastAttemptAt == null) {
        return false
    }
    return stats.quotas.any { quota ->
        val due = quota.periodEndAt + grace
        quota.hasKnownResetAt && due <= now && due > lastAttemptAt
    }
}

/**
 * Se a espera terminou muito depois do pedido: o processo não rodou nesse
 * intervalo, o que na prática é o PC voltando do sleep — o substituto na JVM do
 * `didWakeNotification` do macOS. Nesse caso tudo fica devido.
 */
fun looksLikeWakeFromSleep(expectedWakeAt: Instant, actualWakeAt: Instant, threshold: Duration): Boolean {
    return actualWakeAt - expectedWakeAt > threshold
}
