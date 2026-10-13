package com.usagemonitor.domain.entity

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Quanto tempo uma fonte falha seguido antes do aviso (#408, AA18): três coletas no ritmo sem sessão (5 min). */
const val SOURCE_BROKEN_AFTER_MILLIS = 15 * 60 * 1_000L

/**
 * Como uma conta saiu da última coleta (#408, AA18). [failure] é a mensagem que a tela já
 * mostra — saneada, nunca com token —; `null` é leitura boa. [lastReadingAt] é a hora dos
 * números que continuam na tela (`statsRetainedAfterFailure`), `null` sem leitura guardada.
 */
data class SourceHealthObservation(
    val key: String,
    val label: String,
    val failure: String?,
    val lastReadingAt: Instant? = null
)

/** Conta falhando desde [failingSince]; [announced] diz se o aviso já saiu. */
data class SourceHealthEntry(
    val label: String,
    val failingSince: Instant,
    val announced: Boolean
)

sealed interface SourceHealthEvent {
    data class Broken(val label: String, val reason: String, val failingSince: Instant, val lastReadingAt: Instant?) : SourceHealthEvent
    data class Recovered(val label: String, val at: Instant) : SourceHealthEvent
}

data class SourceHealthEvaluation(
    val events: List<SourceHealthEvent>,
    val state: Map<String, SourceHealthEntry>
)

/**
 * Fonte quebrada (#408, AA18). Função pura, como a decisão dos alertas: a conta que falha
 * há [thresholdMillis] ou mais avisa **uma vez** com o motivo; quando volta a ler, avisa
 * que voltou — só se tinha avisado que quebrou. Conta que some das observações (fonte
 * desligada) sai do estado calada. No silêncio nada sai e nada é marcado: adia, não consome.
 */
fun evaluateSourceHealth(
    observations: List<SourceHealthObservation>,
    previous: Map<String, SourceHealthEntry>,
    now: Instant,
    silenced: Boolean,
    thresholdMillis: Long = SOURCE_BROKEN_AFTER_MILLIS
): SourceHealthEvaluation {
    val events = mutableListOf<SourceHealthEvent>()
    val state = mutableMapOf<String, SourceHealthEntry>()
    for (observation in observations) {
        val stored = previous[observation.key]
        val reason = observation.failure
        if (reason == null) {
            if (stored == null || !stored.announced) continue
            if (silenced) {
                state[observation.key] = stored
                continue
            }
            events += SourceHealthEvent.Recovered(observation.label, now)
            continue
        }
        val entry = stored ?: SourceHealthEntry(observation.label, now, announced = false)
        val due = !entry.announced && (now - entry.failingSince).inWholeMilliseconds >= thresholdMillis
        if (due && !silenced) {
            events += SourceHealthEvent.Broken(observation.label, reason, entry.failingSince, observation.lastReadingAt)
            state[observation.key] = entry.copy(announced = true)
        } else {
            state[observation.key] = entry
        }
    }
    return SourceHealthEvaluation(events, state)
}

/** Silêncio em vigor agora — horário (no fuso de [timeZone]) ou "Silenciar" —, como no resumo diário. */
fun isAlertSilenced(settings: UsageAlertSettings, now: Instant, timeZone: TimeZone = TimeZone.of("America/Sao_Paulo")): Boolean {
    val snoozed = settings.snoozedUntilEpochMillis?.let { until -> now.toEpochMilliseconds() < until } == true
    return snoozed || settings.quietHours?.contains(now.toLocalDateTime(timeZone).hour) == true
}
