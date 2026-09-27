package com.usagemonitor.domain.entity

import kotlin.time.Duration

/**
 * A fonte respondeu HTTP 429 (issue #269).
 *
 * Tipada para o agendamento armar o backoff sem depender de substring. A mensagem
 * continua trazendo `HTTP 429`, porque é por ela que `warningFor`, o toast e os
 * marcadores de `UiApiError` classificam a falha — trocar o texto os desligaria
 * em silêncio.
 *
 * [retryAfter] é o `Retry-After` recebido, já convertido em espera; nulo quando o
 * cabeçalho não veio ou não foi legível. Ele só pode **aumentar** a espera
 * calculada (`rateLimitBackoff`): o endpoint da Anthropic responde
 * `Retry-After: 0`, e obedecê-lo ao pé da letra mantinha o poll batendo no limite.
 */
class RateLimitedException(
    message: String,
    val retryAfter: Duration? = null
) : IllegalStateException(message)
