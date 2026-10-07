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
    val pairingExpiresAtMillis: Long? = null,
    /**
     * `/api` pode ligar e desligar fontes (#398, Y8). Nasce desligado: com ele, quem
     * está numa conversa pareada muda o que o app monitora.
     */
    val allowSourceControl: Boolean = false,
    /** Hora (BRT) do resumo diário (#398, Y1); `null` desliga. */
    val dailySummaryHour: Int? = null,
    /** Dia local (ISO) do último resumo enviado: um por dia. */
    val lastSummaryDate: String? = null,
    /** Painel fixado que se atualiza sozinho (#398, Y5). */
    val livePanelEnabled: Boolean = false,
    /** A mensagem do painel em cada conversa (`chatId` → `message_id`), para editar e desafixar. */
    val panelMessages: Map<Long, Long> = emptyMap()
) {
    fun copy(
        enabled: Boolean = this.enabled,
        botToken: String = this.botToken,
        authorizedChats: List<TelegramChat> = this.authorizedChats,
        pairingCode: String? = this.pairingCode,
        pairingExpiresAtMillis: Long? = this.pairingExpiresAtMillis,
        allowSourceControl: Boolean = this.allowSourceControl,
        dailySummaryHour: Int? = this.dailySummaryHour,
        lastSummaryDate: String? = this.lastSummaryDate,
        livePanelEnabled: Boolean = this.livePanelEnabled,
        panelMessages: Map<Long, Long> = this.panelMessages
    ): TelegramBotSettings = TelegramBotSettings(
        enabled, botToken, authorizedChats, pairingCode, pairingExpiresAtMillis, allowSourceControl, dailySummaryHour, lastSummaryDate,
        livePanelEnabled, panelMessages
    )

    fun isAuthorized(chatId: Long): Boolean = authorizedChats.any { chat -> chat.id == chatId }

    /** O código vale para [code] dentro do prazo — comparação sem diferenciar caixa. */
    fun acceptsPairing(code: String, nowMillis: Long): Boolean {
        val expected = pairingCode ?: return false
        val expiresAt = pairingExpiresAtMillis ?: return false
        return nowMillis < expiresAt && expected.equals(code.trim(), ignoreCase = true)
    }

    override fun equals(other: Any?): Boolean = other is TelegramBotSettings &&
        other.enabled == enabled && other.botToken == botToken && other.authorizedChats == authorizedChats &&
        other.pairingCode == pairingCode && other.pairingExpiresAtMillis == pairingExpiresAtMillis &&
        other.allowSourceControl == allowSourceControl && other.dailySummaryHour == dailySummaryHour &&
        other.lastSummaryDate == lastSummaryDate && other.livePanelEnabled == livePanelEnabled &&
        other.panelMessages == panelMessages

    override fun hashCode(): Int = listOf(
        enabled, botToken, authorizedChats, pairingCode, pairingExpiresAtMillis, allowSourceControl, dailySummaryHour, lastSummaryDate,
        livePanelEnabled, panelMessages
    ).hashCode()

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
    /** `/conta` (#398, Y4): escolher uma conta num teclado e ver só ela. */
    data object Accounts : BotCommand
    /** `/atualizar` (#398, Y8): coleta agora e responde o `/status`. */
    data object Refresh : BotCommand
    /** `/api` (#398, Y8): lista as fontes e, se permitido, liga e desliga. */
    data object Sources : BotCommand
    /** `/silencio` sem argumento (#398, Y8): oferece 1 h, 4 h e até 08:00. */
    data object QuietMenu : BotCommand
    /** `/resumo` (#398, Y1): o resumo diário na hora. */
    data object Summary : BotCommand
    /** `/grafico [24h|7d]` (#398, Y6): imagem do uso no intervalo. */
    data class Chart(val range: HistoryRange) : BotCommand
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
        "conta", "contas", "account", "accounts" -> BotCommand.Accounts
        "atualizar", "refresh" -> BotCommand.Refresh
        "api", "apis", "fontes", "sources" -> BotCommand.Sources
        "resumo", "summary" -> BotCommand.Summary
        "grafico", "gráfico", "chart" -> when (args.firstOrNull()?.lowercase()) {
            null, "24h", "24" -> BotCommand.Chart(HistoryRange.LAST_24_HOURS)
            "7d", "7" -> BotCommand.Chart(HistoryRange.LAST_7_DAYS)
            else -> BotCommand.Invalid("/grafico 24h | /grafico 7d")
        }
        "ajuda", "help" -> BotCommand.Help
        else -> BotCommand.Invalid("/ajuda")
    }
}

private fun parseQuiet(argument: String?): BotCommand {
    val usage = "/silencio 22-07 | /silencio off"
    val value = argument?.lowercase() ?: return BotCommand.QuietMenu
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
            spikeAlertsEnabled = command.enabled,
            // Ligar de volta encerra o "Silenciar 1h" do botão; desligar não precisa dele.
            snoozedUntilEpochMillis = null
        )
        is BotCommand.Quiet -> settings.copy(quietHours = command.hours)
        is BotCommand.Threshold -> settings.copy(quotaPercents = command.percents)
        else -> null
    }
}

/** Duração do botão "Silenciar 1h" da mensagem do `/status` (#396). */
const val TELEGRAM_SNOOZE_MILLIS = 60 * 60 * 1_000L

/**
 * Botão do teclado inline que acompanha o `/status` (#396, direção W5). O
 * [data] é o `callback_data` que o Telegram devolve — até 64 bytes, nunca dado
 * de uso.
 */
enum class BotButton(val data: String) {
    REFRESH("refresh"),
    SNOOZE("snooze"),
    THRESHOLDS("thresholds");

    companion object {
        /** `null` para `callback_data` desconhecido: o bot só responde ao que ele mesmo mandou. */
        fun fromData(data: String?): BotButton? = entries.firstOrNull { button -> button.data == data }
    }
}

/** Silêncio temporário de [TELEGRAM_SNOOZE_MILLIS] a partir de [nowMillis]; função pura, como [applyBotCommand]. */
fun snoozeAlerts(settings: UsageAlertSettings, nowMillis: Long): UsageAlertSettings =
    settings.copy(snoozedUntilEpochMillis = nowMillis + TELEGRAM_SNOOZE_MILLIS)
