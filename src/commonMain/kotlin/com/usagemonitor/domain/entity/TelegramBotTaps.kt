package com.usagemonitor.domain.entity

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Toque num botão com parâmetro (#398). [BotButton] continua com os três botões
 * fixos do `/status`; estes carregam um valor — qual conta, qual fonte, quanto
 * tempo — e por isso não cabem num valor de enum.
 *
 * O `callback_data` volta do Telegram como veio, até 64 bytes. Nunca carrega
 * rótulo nem e-mail: a conta vai como fonte + resumo do rótulo ([botAccountKey]).
 */
sealed interface BotTap {
    /** Mostra só a conta de [key]; [key] sai de [botAccountKey]. */
    data class Account(val key: String) : BotTap

    /** Liga ou desliga [source] (#398, Y8) — só com `allowSourceControl`. */
    data class Source(val source: ApiSource) : BotTap

    /** Silencia por [minutes] a partir do toque (#398, Y8). */
    data class SnoozeFor(val minutes: Int) : BotTap

    /** Silencia até a próxima 08:00 em BRT (#398, Y8). */
    data object SnoozeUntilMorning : BotTap
}

private const val ACCOUNT_PREFIX = "acc:"
private const val SOURCE_PREFIX = "api:"
private const val SNOOZE_PREFIX = "snz:"
private const val SNOOZE_MORNING = "am"

/** `null` para dado que não é deste formato — inclusive os de [BotButton]. */
fun parseBotTap(data: String?): BotTap? {
    val value = data ?: return null
    return when {
        value.startsWith(ACCOUNT_PREFIX) -> value.removePrefix(ACCOUNT_PREFIX).takeIf { key -> key.isNotEmpty() }?.let(BotTap::Account)
        value.startsWith(SOURCE_PREFIX) -> ApiSource.entries.firstOrNull { source -> source.name == value.removePrefix(SOURCE_PREFIX) }?.let(BotTap::Source)
        value == SNOOZE_PREFIX + SNOOZE_MORNING -> BotTap.SnoozeUntilMorning
        value.startsWith(SNOOZE_PREFIX) -> value.removePrefix(SNOOZE_PREFIX).toIntOrNull()?.takeIf { minutes -> minutes in 1..MAX_SNOOZE_MINUTES }?.let(BotTap::SnoozeFor)
        else -> null
    }
}

/** O `callback_data` de cada toque com parâmetro — o inverso de [parseBotTap]. */
fun BotTap.data(): String = when (this) {
    is BotTap.Account -> ACCOUNT_PREFIX + key
    is BotTap.Source -> SOURCE_PREFIX + source.name
    is BotTap.SnoozeFor -> SNOOZE_PREFIX + minutes
    BotTap.SnoozeUntilMorning -> SNOOZE_PREFIX + SNOOZE_MORNING
}

/** Teto do silêncio por toque: um dia. Dado maior é forjado, não do teclado do bot. */
private const val MAX_SNOOZE_MINUTES = 24 * 60

/** Hora local (BRT) em que "até de manhã" termina. */
const val TELEGRAM_MORNING_HOUR = 8

/** A próxima [TELEGRAM_MORNING_HOUR]:00 em São Paulo depois de [nowMillis]. Função pura. */
fun nextMorningMillis(nowMillis: Long, timeZone: TimeZone = TimeZone.of("America/Sao_Paulo")): Long {
    val now = Instant.fromEpochMilliseconds(nowMillis)
    val today = now.toLocalDateTime(timeZone).date
    val todayMorning = LocalDateTime(today, LocalTime(TELEGRAM_MORNING_HOUR, 0)).toInstant(timeZone)
    val target = if (todayMorning > now) todayMorning else LocalDateTime(today.plus(1, DateTimeUnit.DAY), LocalTime(TELEGRAM_MORNING_HOUR, 0)).toInstant(timeZone)
    return target.toEpochMilliseconds()
}

/** Silêncio até [untilMillis] — o mesmo campo do "Silenciar 1h" do `/status`. */
fun snoozeAlertsUntil(settings: UsageAlertSettings, untilMillis: Long): UsageAlertSettings =
    settings.copy(snoozedUntilEpochMillis = untilMillis)

/** O `callback_data` do botão da conta. */
fun botAccountTapData(account: UsageSnapshotAccount): String = BotTap.Account(botAccountKey(account)).data()

/**
 * Identidade estável da conta entre o envio do teclado e o toque: fonte e um
 * FNV-1a de 32 bits do rótulo. Índice não serve — a lista pode mudar de ordem
 * entre os dois —, e o rótulo inteiro passaria dos 64 bytes ou levaria e-mail.
 */
fun botAccountKey(account: UsageSnapshotAccount): String {
    var hash = 0x811C9DC5.toInt()
    for (char in account.label) {
        hash = hash xor char.code
        hash *= 0x01000193
    }
    return "${account.source.name}:${hash.toUInt().toString(16)}"
}

/** A conta de [key] no retrato, ou `null` se ela saiu dele desde o envio do teclado. */
fun UsageSnapshot.accountByKey(key: String): UsageSnapshotAccount? =
    accounts.firstOrNull { account -> botAccountKey(account) == key }
