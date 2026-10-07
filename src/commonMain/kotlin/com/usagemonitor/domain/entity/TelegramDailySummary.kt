package com.usagemonitor.domain.entity

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Horas oferecidas para o resumo diário (#398, Y1), em BRT. */
val TELEGRAM_SUMMARY_HOURS: List<Int> = listOf(7, 8, 9, 12, 18)

/**
 * Gasto das últimas 24 h no índice CLI, para o resumo (#398, Y1). [unpricedTurns]
 * maior que zero faz o valor sair com `+`: modelo sem tarifa não vira custo zero.
 */
data class TelegramDailySpend(
    val costMicros: Long,
    val sessionCount: Int,
    val unpricedTurns: Int
)

/**
 * O resumo de hoje já deve sair? Função pura (#398, Y1).
 *
 * Sai na primeira checagem a partir de [hour] no dia local, uma vez por dia
 * ([lastSentDate] no formato ISO). Com o app fechado na hora, sai quando ele abrir
 * naquele dia. No silêncio (horário ou "Silenciar") **adia** — volta a ser devido
 * quando o silêncio acabar —, igual aos alertas.
 */
fun isDailySummaryDue(
    hour: Int?,
    lastSentDate: String?,
    now: Instant,
    alertSettings: UsageAlertSettings,
    timeZone: TimeZone = TimeZone.of("America/Sao_Paulo")
): Boolean {
    val target = hour ?: return false
    val local = now.toLocalDateTime(timeZone)
    if (local.hour < target) return false
    if (lastSentDate == local.date.toString()) return false
    val snoozed = alertSettings.snoozedUntilEpochMillis?.let { until -> now.toEpochMilliseconds() < until } == true
    val quiet = alertSettings.quietHours?.contains(local.hour) == true
    return !snoozed && !quiet
}

/** A data local que marca o resumo como enviado — a mesma que [isDailySummaryDue] compara. */
fun dailySummaryDate(now: Instant, timeZone: TimeZone = TimeZone.of("America/Sao_Paulo")): String =
    now.toLocalDateTime(timeZone).date.toString()
