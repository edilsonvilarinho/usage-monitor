package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.TelegramPing
import com.usagemonitor.domain.entity.UsageSnapshot
import kotlin.time.Instant

/**
 * Textos sobre o próprio app e a conversa (#408): `/ping` e, nas atividades
 * seguintes, conversas, idioma, teclado e ações no PC. Mesmo HTML e mesmo escape
 * de [TelegramBotMessages].
 */
internal object TelegramBotChatMessages {

    /** `/conversas` (AA22): as conversas pareadas, com esta marcada. */
    fun chats(chats: List<TelegramChat>, currentChatId: Long, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val lines = chats.map { chat ->
            val current = if (chat.id == currentChatId) (if (pt) " — esta" else " — this one") else ""
            "• ${TelegramBotMessages.escape(chat.name)}$current"
        }
        val header = if (pt) "<b>Conversas pareadas</b>" else "<b>Paired chats</b>"
        val footer = if (pt) "<i>/sair desfaz o pareamento desta conversa.</i>" else "<i>/leave unpairs this chat.</i>"
        return (listOf(header) + lines + footer).joinToString("\n")
    }

    /** Confirmação do `/idioma` (AA24), já no idioma novo. */
    fun languageSet(language: AppLanguage?, effective: AppLanguage): String = when {
        language == null && effective == AppLanguage.PT -> "🌐 Esta conversa segue o idioma do app (português)."
        language == null -> "🌐 This chat follows the app language (English)."
        effective == AppLanguage.PT -> "🌐 Esta conversa agora fala português."
        else -> "🌐 This chat now speaks English."
    }

    /** Recusa de conversa só de leitura (AA21): diz o papel e onde mudá-lo. */
    fun readOnly(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "🔒 Esta conversa é <b>só leitura</b>: ela vê as cotas, mas não muda alertas, fontes nem silêncio. Quem pareou dá controle em Configurações › Alertas › Bot do Telegram."
    } else {
        "🔒 This chat is <b>read only</b>: it sees the quotas but does not change alerts, sources or quiet hours. Whoever paired it can grant control in Settings › Alerts › Telegram bot."
    }

    /** Aviso curto do toque numa conversa só de leitura (AA21). */
    fun readOnlyTap(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Conversa só de leitura." else "Read-only chat."

    /** Resposta do `/sair` (AA22): a última mensagem que esta conversa recebe. */
    fun left(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "👋 Pareamento desfeito. Esta conversa não recebe mais nada. Para voltar, gere um código novo nas Configurações."
    } else {
        "👋 Unpaired. This chat will not receive anything else. To come back, create a new code in Settings."
    }

    /**
     * `/ping` (AA19): versão, tempo aberto, próxima coleta e, por conta, a hora da
     * última leitura. [now] vem de quem chama: o retrato pode não existir ainda.
     */
    fun ping(ping: TelegramPing?, snapshot: UsageSnapshot?, now: Instant, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (ping == null) {
            return if (pt) "🏓 Usage Monitor aberto." else "🏓 Usage Monitor is running."
        }
        val header = "<b>🏓 Usage Monitor ${TelegramBotMessages.escape(ping.version)}</b>"
        val uptime = (if (pt) "Aberto há " else "Running for ") + TelegramBotInsightMessages.span(now - ping.startedAt)
        val next = ping.nextRefreshAt?.let { at ->
            (if (pt) "Próxima coleta: " else "Next collection: ") + TelegramBotMessages.clock(at) + " BRT"
        }
        val accounts = snapshot?.accounts.orEmpty().map { account ->
            val name = "<b>${TelegramBotMessages.escape(account.label)}</b>"
            val fetched = account.fetchedAt?.let { at ->
                (if (pt) "coleta " else "collected ") + TelegramBotMessages.clock(at) + " · " + TelegramBotMessages.elapsed(now - at, pt)
            } ?: if (pt) "sem coleta ainda" else "not collected yet"
            "$name — $fetched"
        }
        return (listOfNotNull(header, uptime, next) + listOf("") + accounts).joinToString("\n").trimEnd()
    }
}
