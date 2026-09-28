package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.RateLimitedException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fromHttpToGmtDate
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * O `Retry-After` como espera (RFC 9110 §10.2.3): segundos inteiros ou data HTTP.
 * Nulo quando ausente ou ilegível — quem decide a espera é `rateLimitBackoff`, e
 * valor que não se consegue ler não pode virar "zero, tente já".
 */
internal fun parseRetryAfter(value: String?, now: Instant): Duration? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        return null
    }
    trimmed.toLongOrNull()?.let { seconds ->
        return if (seconds >= 0) seconds.seconds else null
    }
    val date = runCatching { trimmed.fromHttpToGmtDate() }.getOrNull() ?: return null
    val wait = (date.timestamp - now.toEpochMilliseconds()).milliseconds
    return wait.coerceAtLeast(Duration.ZERO)
}

/**
 * Lança [RateLimitedException] quando [response] é um 429, com a mesma mensagem
 * `"<fonte> HTTP 429: <corpo>"` que os demais status já produzem.
 */
internal suspend fun throwIfRateLimited(response: HttpResponse, sourceName: String, now: Instant = Clock.System.now()) {
    if (response.status != HttpStatusCode.TooManyRequests) {
        return
    }
    val body = response.bodyAsText()
    throw RateLimitedException(
        message = "$sourceName HTTP ${response.status.value}: $body",
        retryAfter = parseRetryAfter(response.headers[HttpHeaders.RetryAfter], now)
    )
}
