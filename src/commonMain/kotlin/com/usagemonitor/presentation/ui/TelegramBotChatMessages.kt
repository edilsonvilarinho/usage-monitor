package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramPing
import com.usagemonitor.domain.entity.UsageSnapshot
import kotlin.time.Instant

/**
 * Textos sobre o próprio app e a conversa (#408): `/ping` e, nas atividades
 * seguintes, conversas, idioma, teclado e ações no PC. Mesmo HTML e mesmo escape
 * de [TelegramBotMessages].
 */
internal object TelegramBotChatMessages {

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
