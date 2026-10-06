package com.usagemonitor.domain.entity

/** Validade do código de pareamento: o tempo de abrir o Telegram e mandar a mensagem. */
const val TELEGRAM_PAIRING_TTL_MILLIS = 10 * 60 * 1_000L

/** Conversa autorizada a falar com o bot (#387). Nome é só rótulo; a identidade é o [id]. */
data class TelegramChat(val id: Long, val name: String)

/**
 * Configuração do bot do Telegram (#387).
 *
 * **Não é `data class`**: carrega o token do bot, e o `toString` gerado o vazaria
 * (precedente `CursorSessionCredentials`). Igualdade escrita à mão porque o
 * `StateFlow` depende dela.
 */
class TelegramBotSettings(
    val enabled: Boolean = false,
    val botToken: String = "",
    val authorizedChats: List<TelegramChat> = emptyList(),
    /** Código que a próxima conversa manda em `/start <código>`; `null` sem pareamento aberto. */
    val pairingCode: String? = null,
    val pairingExpiresAtMillis: Long? = null
) {
    fun copy(
        enabled: Boolean = this.enabled,
        botToken: String = this.botToken,
        authorizedChats: List<TelegramChat> = this.authorizedChats,
        pairingCode: String? = this.pairingCode,
        pairingExpiresAtMillis: Long? = this.pairingExpiresAtMillis
    ): TelegramBotSettings = TelegramBotSettings(enabled, botToken, authorizedChats, pairingCode, pairingExpiresAtMillis)

    fun isAuthorized(chatId: Long): Boolean = authorizedChats.any { chat -> chat.id == chatId }

    /** O código vale para [code] dentro do prazo — comparação sem diferenciar caixa. */
    fun acceptsPairing(code: String, nowMillis: Long): Boolean {
        val expected = pairingCode ?: return false
        val expiresAt = pairingExpiresAtMillis ?: return false
        return nowMillis < expiresAt && expected.equals(code.trim(), ignoreCase = true)
    }

    override fun equals(other: Any?): Boolean = other is TelegramBotSettings &&
        other.enabled == enabled && other.botToken == botToken && other.authorizedChats == authorizedChats &&
        other.pairingCode == pairingCode && other.pairingExpiresAtMillis == pairingExpiresAtMillis

    override fun hashCode(): Int =
        listOf(enabled, botToken, authorizedChats, pairingCode, pairingExpiresAtMillis).hashCode()

    override fun toString(): String =
        "TelegramBotSettings(enabled=$enabled, botToken=${if (botToken.isEmpty()) "" else "***"}, chats=${authorizedChats.size})"
}

/** Comando recebido pelo bot (#387). Mão dupla: ler o estado e mudar os alertas. */
sealed interface BotCommand {
    data class Start(val code: String) : BotCommand
    data object Status : BotCommand
    data class Alerts(val enabled: Boolean) : BotCommand
    /** `null` desliga o silêncio. */
    data class Quiet(val hours: QuietHours?) : BotCommand
    data class Threshold(val percents: List<Int>) : BotCommand
    data object Help : BotCommand
    data class Invalid(val usage: String) : BotCommand
}

/**
 * Interpreta a mensagem. Aceita os nomes em português e em inglês e o sufixo
 * `@nome_do_bot` que o Telegram acrescenta em grupo. Texto que não é comando
 * vira `null`: o bot não responde a conversa solta.
 */
fun parseBotCommand(text: String): BotCommand? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("/")) {
        return null
    }
    val parts = trimmed.split(Regex("\\s+"))
    val name = parts.first().removePrefix("/").substringBefore('@').lowercase()
    val args = parts.drop(1)
    return when (name) {
        "start" -> args.firstOrNull()?.let { code -> BotCommand.Start(code) } ?: BotCommand.Help
        "status" -> BotCommand.Status
        "alertas", "alerts" -> when (args.firstOrNull()?.lowercase()) {
            "on", "ligar", "ligado" -> BotCommand.Alerts(true)
            "off", "desligar", "desligado" -> BotCommand.Alerts(false)
            else -> BotCommand.Invalid("/alertas on|off")
        }
        "silencio", "silêncio", "quiet" -> parseQuiet(args.firstOrNull())
        "limiar", "threshold" -> parseThreshold(args.joinToString(","))
        "ajuda", "help" -> BotCommand.Help
        else -> BotCommand.Invalid("/ajuda")
    }
}

private fun parseQuiet(argument: String?): BotCommand {
    val usage = "/silencio 22-07 | /silencio off"
    val value = argument?.lowercase() ?: return BotCommand.Invalid(usage)
    if (value == "off" || value == "desligar") {
        return BotCommand.Quiet(null)
    }
    val hours = value.split('-').mapNotNull { part -> part.toIntOrNull() }
    if (hours.size != 2 || hours.any { hour -> hour !in 0..23 }) {
        return BotCommand.Invalid(usage)
    }
    return BotCommand.Quiet(QuietHours(hours[0], hours[1]))
}

private fun parseThreshold(argument: String): BotCommand {
    val percents = argument.split(',').mapNotNull { part -> part.trim().toIntOrNull() }.filter { percent -> percent in 1..100 }
    if (percents.isEmpty()) {
        return BotCommand.Invalid("/limiar 75,90")
    }
    return BotCommand.Threshold(percents.distinct().sorted())
}

/**
 * Preferências de alerta depois do comando, ou `null` quando o comando não as
 * muda. Função pura: o bot e as Configurações gravam pelo mesmo caminho.
 */
fun applyBotCommand(settings: UsageAlertSettings, command: BotCommand): UsageAlertSettings? {
    return when (command) {
        is BotCommand.Alerts -> settings.copy(
            quotaAlertsEnabled = command.enabled,
            sessionAlertsEnabled = command.enabled,
            stalledSessionAlertsEnabled = command.enabled,
            spikeAlertsEnabled = command.enabled
        )
        is BotCommand.Quiet -> settings.copy(quietHours = command.hours)
        is BotCommand.Threshold -> settings.copy(quotaPercents = command.percents)
        else -> null
    }
}
