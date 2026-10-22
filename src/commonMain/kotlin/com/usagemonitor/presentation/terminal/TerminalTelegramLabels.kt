package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized

/** Os textos do `telegram` no terminal (#440, 5.2 do protótipo), nos dois idiomas do app. */
internal class TerminalTelegramLabels(val language: AppLanguage) {

    val tokenPrompt: String
        get() = localized(language, "Cole o token do bot (o que o @BotFather deu) e tecle enter (não aparece na tela):", "Paste the bot token (the one @BotFather gave you) and press enter (it is not shown):")

    val tokenRejected: String get() = localized(language, "O Telegram recusou o token, e nada mudou.", "Telegram rejected the token, and nothing changed.")

    fun tokenUnchecked(detail: String): String =
        localized(language, "Não deu para conferir o token agora, e nada mudou: $detail", "The token could not be checked now, and nothing changed: $detail")

    fun setupDone(bot: String?): String =
        bot?.let { name -> localized(language, "bot @$name configurado e ligado", "bot @$name set up and enabled") }
            ?: localized(language, "bot configurado e ligado", "bot set up and enabled")

    val pairNext: String get() = localized(language, "Pareie uma conversa com:", "Pair a chat with:")

    fun switched(enabled: Boolean): String = if (enabled) localized(language, "bot ligado", "bot enabled") else localized(language, "bot desligado", "bot disabled")

    val needsCollector: String
        get() = localized(
            language,
            "Quem atende o bot é quem coleta, e nada está coletando agora. Inicie o serviço ou abra o app, e rode de novo. Para coletar aqui:",
            "The bot is answered by whoever collects, and nothing is collecting now. Start the service or open the app, and run it again. To collect here:"
        )

    val needsToken: String get() = localized(language, "O bot ainda não tem token. Configure com:", "The bot has no token yet. Set it up with:")

    fun pairInstructions(bot: String?, code: String): String =
        bot?.let { name -> localized(language, "No Telegram, abra o seu bot (@$name) e envie: /start $code", "In Telegram, open your bot (@$name) and send: /start $code") }
            ?: localized(language, "No Telegram, abra o seu bot e envie: /start $code", "In Telegram, open your bot and send: /start $code")

    fun pairLink(bot: String, code: String): String = localized(language, "ou abra https://t.me/$bot?start=$code", "or open https://t.me/$bot?start=$code")

    fun waiting(until: String): String = localized(language, "aguardando… (o código vale até $until BRT)", "waiting… (the code is valid until $until BRT)")

    fun paired(name: String): String = localized(language, "pareado com $name", "paired with $name")

    val pairExpired: String
        get() = localized(language, "O código venceu sem nenhuma conversa pareada. Rode de novo para um código novo.", "The code expired with no chat paired. Run it again for a new code.")

    fun testSent(count: Int): String =
        localized(language, if (count == 1) "mensagem de teste enviada para 1 conversa" else "mensagem de teste enviada para $count conversas", if (count == 1) "test message sent to 1 chat" else "test message sent to $count chats")

    fun testFailed(detail: String): String = localized(language, "A mensagem de teste não saiu: $detail", "The test message was not sent: $detail")

    val noChats: String get() = localized(language, "Nenhuma conversa pareada. Pareie com:", "No chat is paired. Pair one with:")

    val rowBot: String get() = localized(language, "bot", "bot")
    val rowToken: String get() = localized(language, "token", "token")
    val rowChats: String get() = localized(language, "conversas", "chats")
    val rowPairing: String get() = localized(language, "pareamento", "pairing")
    val on: String get() = localized(language, "ligado", "on")
    val off: String get() = localized(language, "desligado", "off")
    val tokenSaved: String get() = localized(language, "salvo", "saved")
    val none: String get() = localized(language, "nenhuma", "none")
    val noCode: String get() = localized(language, "nenhum código aberto", "no open code")
    fun codeOpen(until: String): String = localized(language, "código aberto até $until BRT", "code open until $until BRT")
    val control: String get() = localized(language, "controle", "control")
    val readOnly: String get() = localized(language, "só leitura", "read only")

    fun chatRemoved(name: String): String = localized(language, "$name não é mais atendida", "$name is no longer answered")

    fun chatReadOnly(name: String, readOnly: Boolean): String =
        if (readOnly) localized(language, "$name só lê", "$name is read only") else localized(language, "$name com controle", "$name has control")

    fun unknownChat(chat: String): String = localized(language, "A conversa '$chat' não está pareada. Veja as conversas com:", "The chat '$chat' is not paired. See the chats with:")
}
