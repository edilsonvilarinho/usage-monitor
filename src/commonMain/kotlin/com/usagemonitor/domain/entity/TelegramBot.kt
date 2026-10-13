package com.usagemonitor.domain.entity

/** Validade do código de pareamento: o tempo de abrir o Telegram e mandar a mensagem. */
const val TELEGRAM_PAIRING_TTL_MILLIS = 10 * 60 * 1_000L

/**
 * Conversa autorizada a falar com o bot (#387). Nome é só rótulo; a identidade é o [id].
 * [readOnly] (#408, AA21): a conversa lê tudo mas não muda nada — um grupo da equipe
 * vê o `/status`, só quem tem controle mexe em alertas, fontes e silêncio. Nasce
 * `false`, o comportamento de antes do papel existir.
 */
data class TelegramChat(
    val id: Long,
    val name: String,
    val readOnly: Boolean = false,
    /** Idioma desta conversa (#408, AA24); `null` segue o idioma do app. */
    val language: AppLanguage? = null
)

/**
 * Opções do bot sem segredo (#408): cada função nova com interruptor entra aqui, e
 * não como mais um campo do [TelegramBotSettings] — aquele tem `copy`, `equals` e
 * `hashCode` escritos à mão por causa do token, e crescer um a um os três a cada
 * função seria a falha esperando para acontecer. Toda opção que age no PC ou expõe
 * dado de terceiros nasce desligada.
 */
data class TelegramBotPreferences(
    /** `/hud` traz a HUD à frente no PC (#408, AA29). */
    val allowPcActions: Boolean = false,
    /** Mensagens dizem de que PC saíram (#408, AA20): útil com o bot em mais de uma máquina. */
    val showMachineName: Boolean = false,
    /** `/time` mostra quem do time está online (#408, AA16): dado de outras pessoas, nasce desligado. */
    val showTeam: Boolean = false,
    /**
     * Avisa a versão nova já baixada e oferece "Atualizar e reiniciar" (#408, AA17): reinicia
     * o app no PC a pedido de uma conversa, por isso nasce desligado.
     */
    val allowRemoteUpdate: Boolean = false,
    /**
     * Só o alerta crítico ([isCriticalAlert]) toca; os outros chegam sem som (#408, AA26).
     * Nasce ligado, como no mockup aprovado: não age no PC nem expõe dado — só tira o som.
     */
    val soundOnlyOnCritical: Boolean = true,
    /**
     * Repetição do mesmo alerta no mesmo dia edita a mensagem anterior com o contador em vez
     * de abrir outra (#408, AA1). Nasce ligado, como no mockup: só muda a forma da entrega.
     */
    val groupRepeatedAlerts: Boolean = true,
    /**
     * Junta os alertas de 15 min numa mensagem só (#408, AA25); o crítico sai na hora, fora
     * do lote. Nasce ligado, como no mockup: atrasa o aviso não crítico, nunca o descarta.
     */
    val batchAlerts: Boolean = true,
    /**
     * Painel fixado em uma linha (#408, AA15): cabe no banner do topo da conversa sem abrir.
     * Nasce ligado, como no mockup; o painel em si continua nascendo desligado.
     */
    val compactPanel: Boolean = true,
    /**
     * Avisa a fonte que falha há 15 min e de novo quando ela volta (#408, AA18). Nasce
     * ligado, como no mockup: o texto é a mensagem saneada da tela, nunca o token.
     */
    val sourceHealthAlerts: Boolean = true,
    /** Última versão avisada pelo bot — estado, não opção: o aviso sai uma vez por versão, mesmo entre reinícios. */
    val announcedUpdateVersion: String? = null
)

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
    val panelMessages: Map<Long, Long> = emptyMap(),
    /** As opções da #408 ([TelegramBotPreferences]). */
    val preferences: TelegramBotPreferences = TelegramBotPreferences()
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
        panelMessages: Map<Long, Long> = this.panelMessages,
        preferences: TelegramBotPreferences = this.preferences
    ): TelegramBotSettings = TelegramBotSettings(
        enabled, botToken, authorizedChats, pairingCode, pairingExpiresAtMillis, allowSourceControl, dailySummaryHour, lastSummaryDate,
        livePanelEnabled, panelMessages, preferences
    )

    fun isAuthorized(chatId: Long): Boolean = authorizedChats.any { chat -> chat.id == chatId }

    /** O idioma das respostas e alertas desta conversa (#408, AA24): o dela ou, sem escolha, o do app. */
    fun languageOf(chatId: Long, appLanguage: AppLanguage): AppLanguage =
        authorizedChats.firstOrNull { chat -> chat.id == chatId }?.language ?: appLanguage

    /** Conversa pareada só de leitura (#408, AA21); conversa fora da lista não é "só leitura", é estranha. */
    fun isReadOnly(chatId: Long): Boolean = authorizedChats.any { chat -> chat.id == chatId && chat.readOnly }

    /** Sem [chatId] na lista nem painel guardado dela (#408, `/sair`). */
    fun withoutChat(chatId: Long): TelegramBotSettings =
        copy(authorizedChats = authorizedChats.filterNot { chat -> chat.id == chatId }, panelMessages = panelMessages - chatId)

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
        other.panelMessages == panelMessages && other.preferences == preferences

    override fun hashCode(): Int = listOf(
        enabled, botToken, authorizedChats, pairingCode, pairingExpiresAtMillis, allowSourceControl, dailySummaryHour, lastSummaryDate,
        livePanelEnabled, panelMessages, preferences
    ).hashCode()

    override fun toString(): String =
        "TelegramBotSettings(enabled=$enabled, botToken=${if (botToken.isEmpty()) "" else "***"}, chats=${authorizedChats.size})"
}

/** Comando recebido pelo bot (#387). Mão dupla: ler o estado e mudar os alertas. */
sealed interface BotCommand {
    data class Start(val code: String) : BotCommand
    data object Status : BotCommand
    /** `/status img` (#408, AA14): os anéis das contas como imagem. */
    data object StatusImage : BotCommand
    data class Alerts(val enabled: Boolean) : BotCommand
    /** `/alertas` sem argumento (#408, AA9): um interruptor por tipo de alerta. */
    data object AlertMenu : BotCommand
    /** `null` desliga o silêncio. */
    data class Quiet(val hours: QuietHours?) : BotCommand
    data class Threshold(val percents: List<Int>) : BotCommand
    /** `/limiar` sem números (#408, AA10): pergunta a conta antes. */
    data object ThresholdMenu : BotCommand
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
    /** `/reinicios` (#408, AA3): os próximos reinícios de todas as contas, em ordem. */
    data object Resets : BotCommand
    /** `/quando` (#408, AA2): quando cada cota esgota no ritmo atual, contra o reinício. */
    data object Exhaustion : BotCommand
    /** `/qual` (#408, AA28): a conta com mais folga de cada fonte, agora. */
    data object BestAccount : BotCommand
    /** `/ritmo` (#408, AA27): quanto das janelas longas cabe por dia até o reinício (estimativa). */
    data object Pace : BotCommand
    /** `/ping` (#408, AA19): o app está aberto e coletando? */
    data object Ping : BotCommand
    /** `/pico` (#408, AA11): o consumo de hoje contra o hábito, sob demanda. */
    data object Spike : BotCommand
    /** `/vazao` (#408, AA5): tokens/s de saída por modelo nas últimas 24 h. */
    data object Throughput : BotCommand
    /** `/semana` (#408, AA7): últimos 7 dias contra os 7 anteriores. */
    data object Week : BotCommand
    /** `/janelas [5h|7d]` (#408, AA12): as últimas janelas com pico e faixa ativa. */
    data class Windows(val weekly: Boolean) : BotCommand
    /** `/comparar [24h|7d|30d]` (#408, AA6): o mapa de calor da comparação entre modelos, em PNG. */
    data class Compare(val days: Int) : BotCommand
    /** `/conversas` (#408, AA22): quem está pareado. */
    data object Chats : BotCommand
    /** `/sair` (#408, AA22): desfaz o pareamento desta conversa. */
    data object Leave : BotCommand
    /** `/idioma pt|en|app` (#408, AA24): o idioma desta conversa; `null` volta ao do app. */
    data class ChatLanguage(val language: AppLanguage?) : BotCommand
    /** `/teclado on|off` (#408, AA30): o teclado fixo de atalhos desta conversa. */
    data class Keyboard(val on: Boolean) : BotCommand
    /** `/hud` (#408, AA29): traz a HUD à frente no PC — só com "Permitir ações no PC". */
    data object FocusHud : BotCommand
    /** `/exportar [24h|7d|30d] [csv|json]` (#408, AA23): o consumo do Claude CLI como arquivo. */
    data class Export(val days: Int, val format: UsageExportFormat) : BotCommand
    /** `/time` (#408, AA16): quem do time está online e trabalhando — só com "Mostrar o time no bot". */
    data object Team : BotCommand
    /** `/foco 2h|off` (#408, AA8): por [hours] horas só passa alerta crítico; `null` encerra. */
    data class Focus(val hours: Int?) : BotCommand
    /** `/avise 15|off` (#408, AA4): minutos de antecedência do lembrete de reinício; `null` desliga. */
    data class Remind(val minutes: Int?) : BotCommand
    data object Help : BotCommand
    data class Invalid(val usage: String) : BotCommand
}

/**
 * O comando muda o app — alertas, silêncio, limiares (#408, AA21)? Conversa só de
 * leitura não pode. `when` exaustivo de propósito: comando novo não compila sem
 * dizer se muda alguma coisa. Sair (`/sair`) não conta: é a conversa decidindo por si.
 */
fun BotCommand.changesApp(): Boolean = when (this) {
    is BotCommand.Alerts, is BotCommand.Quiet, is BotCommand.Threshold, BotCommand.FocusHud, is BotCommand.Focus,
    is BotCommand.Remind, BotCommand.ThresholdMenu -> true
    is BotCommand.Start, BotCommand.AlertMenu, BotCommand.Status, BotCommand.StatusImage, is BotCommand.Export, BotCommand.Team, BotCommand.Accounts, BotCommand.Refresh,
    BotCommand.Sources, BotCommand.QuietMenu, BotCommand.Summary, is BotCommand.Chart, BotCommand.Resets,
    BotCommand.Exhaustion, BotCommand.BestAccount, BotCommand.Pace, BotCommand.Ping, BotCommand.Spike,
    BotCommand.Throughput, BotCommand.Week, is BotCommand.Windows, is BotCommand.Compare, BotCommand.Chats,
    BotCommand.Leave, is BotCommand.ChatLanguage, is BotCommand.Keyboard, BotCommand.Help, is BotCommand.Invalid -> false
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
        "status" -> when (args.firstOrNull()?.lowercase()) {
            "img", "imagem", "image", "foto", "photo" -> BotCommand.StatusImage
            else -> BotCommand.Status
        }
        "alertas", "alerts" -> when (args.firstOrNull()?.lowercase()) {
            "on", "ligar", "ligado" -> BotCommand.Alerts(true)
            "off", "desligar", "desligado" -> BotCommand.Alerts(false)
            null -> BotCommand.AlertMenu
            else -> BotCommand.Invalid("/alertas | /alertas on | /alertas off")
        }
        "silencio", "silêncio", "quiet" -> parseQuiet(args.firstOrNull())
        "limiar", "threshold" -> if (args.isEmpty()) BotCommand.ThresholdMenu else parseThreshold(args.joinToString(","))
        "conta", "contas", "account", "accounts" -> BotCommand.Accounts
        "atualizar", "refresh" -> BotCommand.Refresh
        "api", "apis", "fontes", "sources" -> BotCommand.Sources
        "resumo", "summary" -> BotCommand.Summary
        "grafico", "gráfico", "chart" -> when (args.firstOrNull()?.lowercase()) {
            null, "24h", "24" -> BotCommand.Chart(HistoryRange.LAST_24_HOURS)
            "7d", "7" -> BotCommand.Chart(HistoryRange.LAST_7_DAYS)
            else -> BotCommand.Invalid("/grafico 24h | /grafico 7d")
        }
        "reinicios", "reinícios", "resets" -> BotCommand.Resets
        "quando", "when" -> BotCommand.Exhaustion
        "qual", "which" -> BotCommand.BestAccount
        "ritmo", "pace" -> BotCommand.Pace
        "ping" -> BotCommand.Ping
        "pico", "spike" -> BotCommand.Spike
        "vazao", "vazão", "throughput", "speed" -> BotCommand.Throughput
        "semana", "week" -> BotCommand.Week
        "comparar", "compare" -> when (args.firstOrNull()?.lowercase()) {
            "24h", "1d", "1" -> BotCommand.Compare(1)
            null, "7d", "7" -> BotCommand.Compare(7)
            "30d", "30" -> BotCommand.Compare(30)
            else -> BotCommand.Invalid("/comparar 24h | /comparar 7d | /comparar 30d")
        }
        "janelas", "windows" -> when (args.firstOrNull()?.lowercase()) {
            null, "5h", "5" -> BotCommand.Windows(weekly = false)
            "7d", "7", "semana", "week" -> BotCommand.Windows(weekly = true)
            else -> BotCommand.Invalid("/janelas 5h | /janelas 7d")
        }
        "conversas", "chats" -> BotCommand.Chats
        "sair", "leave", "unpair" -> BotCommand.Leave
        "hud" -> BotCommand.FocusHud
        "exportar", "export" -> parseExport(args)
        "time", "team", "equipe" -> BotCommand.Team
        "foco", "focus" -> parseFocus(args.firstOrNull())
        "avise", "aviso", "lembrete", "remind" -> parseRemind(args.firstOrNull())
        "teclado", "keyboard" -> when (args.firstOrNull()?.lowercase()) {
            null, "on", "ligar" -> BotCommand.Keyboard(true)
            "off", "desligar" -> BotCommand.Keyboard(false)
            else -> BotCommand.Invalid("/teclado on | /teclado off")
        }
        "idioma", "language", "lang" -> when (args.firstOrNull()?.lowercase()) {
            "pt", "português", "portugues" -> BotCommand.ChatLanguage(AppLanguage.PT)
            "en", "english", "inglês", "ingles" -> BotCommand.ChatLanguage(AppLanguage.EN)
            "app", "padrão", "padrao", "default" -> BotCommand.ChatLanguage(null)
            else -> BotCommand.Invalid("/idioma pt | /idioma en | /idioma app")
        }
        "ajuda", "help" -> BotCommand.Help
        else -> BotCommand.Invalid("/ajuda")
    }
}

/**
 * Os botões do teclado fixo (#408, AA30), duas linhas de dois, no idioma da conversa.
 * O toque chega como texto com o rótulo; [parseKeyboardLabel] o traduz no comando.
 */
fun telegramKeyboardRows(language: AppLanguage): List<List<String>> =
    TELEGRAM_KEYBOARD.map { row -> row.map { key -> if (language == AppLanguage.PT) key.pt else key.en } }

/** O comando de um rótulo do teclado fixo, nos dois idiomas; outro texto é `null`, como conversa solta. */
fun parseKeyboardLabel(text: String): BotCommand? {
    val trimmed = text.trim()
    return TELEGRAM_KEYBOARD.flatten().firstOrNull { key -> key.pt == trimmed || key.en == trimmed }?.command
}

private class KeyboardKey(val pt: String, val en: String, val command: BotCommand)

private val TELEGRAM_KEYBOARD = listOf(
    listOf(KeyboardKey("📊 Status", "📊 Status", BotCommand.Status), KeyboardKey("👤 Conta", "👤 Account", BotCommand.Accounts)),
    listOf(KeyboardKey("📈 Gráfico", "📈 Chart", BotCommand.Chart(HistoryRange.LAST_24_HOURS)), KeyboardKey("⏳ Quando", "⏳ When", BotCommand.Exhaustion))
)

/** Período e formato em qualquer ordem; sem eles, 7 dias em CSV. */
private fun parseExport(args: List<String>): BotCommand {
    var days = 7
    var format = UsageExportFormat.CSV
    for (arg in args.map { value -> value.lowercase() }) {
        when (arg) {
            "24h", "1d", "1" -> days = 1
            "7d", "7" -> days = 7
            "30d", "30" -> days = 30
            "csv" -> format = UsageExportFormat.CSV
            "json" -> format = UsageExportFormat.JSON
            else -> return BotCommand.Invalid("/exportar 7d csv | /exportar 30d json")
        }
    }
    return BotCommand.Export(days, format)
}

/** Minutos dentro de [RESET_REMINDER_MINUTES_RANGE] (com ou sem `min`); `off` desliga. Sem argumento, o uso. */
private fun parseRemind(argument: String?): BotCommand {
    val usage = "/avise 15 | /avise off"
    val value = argument?.lowercase() ?: return BotCommand.Invalid(usage)
    if (value == "off" || value == "desligar") return BotCommand.Remind(null)
    val minutes = value.removeSuffix("min").removeSuffix("m").toIntOrNull()?.takeIf { count -> count in RESET_REMINDER_MINUTES_RANGE }
        ?: return BotCommand.Invalid(usage)
    return BotCommand.Remind(minutes)
}

/** `2h` ou `2` (até [MAX_FOCUS_HOURS]) liga; `off` encerra. Sem argumento, o uso. */
private fun parseFocus(argument: String?): BotCommand {
    val value = argument?.lowercase() ?: return BotCommand.Invalid("/foco 2h | /foco off")
    if (value == "off" || value == "desligar") return BotCommand.Focus(null)
    val hours = value.removeSuffix("h").toIntOrNull()?.takeIf { count -> count in 1..MAX_FOCUS_HOURS }
        ?: return BotCommand.Invalid("/foco 2h | /foco off")
    return BotCommand.Focus(hours)
}

/** Teto do `/foco`: mais que meio dia de foco é silêncio com outro nome. */
const val MAX_FOCUS_HOURS = 12

/** Preferências com o foco do `/foco` aplicado a partir de [nowMillis]. Função pura. */
fun focusAlerts(settings: UsageAlertSettings, command: BotCommand.Focus, nowMillis: Long): UsageAlertSettings =
    settings.copy(focusUntilEpochMillis = command.hours?.let { hours -> nowMillis + hours * 60 * 60 * 1_000L })

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
    val percents = parseThresholdPercents(argument) ?: return BotCommand.Invalid("/limiar 75,90")
    return BotCommand.Threshold(percents)
}

/** `75,90` (ou com espaços) em limiares normalizados; `null` quando não sobra nenhum. */
fun parseThresholdPercents(text: String): List<Int>? =
    normalizeQuotaPercents(text.split(',', ' ').mapNotNull { part -> part.trim().toIntOrNull() }).ifEmpty { null }

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
        is BotCommand.Remind -> settings.copy(resetReminderMinutes = command.minutes)
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
