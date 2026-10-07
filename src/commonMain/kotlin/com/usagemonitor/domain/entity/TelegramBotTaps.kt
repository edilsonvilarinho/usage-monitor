package com.usagemonitor.domain.entity

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
}

private const val ACCOUNT_PREFIX = "acc:"

/** `null` para dado que não é deste formato — inclusive os de [BotButton]. */
fun parseBotTap(data: String?): BotTap? {
    val value = data ?: return null
    return when {
        value.startsWith(ACCOUNT_PREFIX) -> value.removePrefix(ACCOUNT_PREFIX).takeIf { key -> key.isNotEmpty() }?.let(BotTap::Account)
        else -> null
    }
}

/** O `callback_data` do botão da conta. */
fun botAccountTapData(account: UsageSnapshotAccount): String = ACCOUNT_PREFIX + botAccountKey(account)

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
