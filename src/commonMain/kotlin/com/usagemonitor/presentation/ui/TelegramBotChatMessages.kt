package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramKeyboardPage
import com.usagemonitor.domain.entity.ReleaseNotes
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.TelegramPendingRestart
import com.usagemonitor.domain.entity.TelegramPing
import com.usagemonitor.domain.entity.TelegramRestartOutcome
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

    /**
     * A assinatura do PC no fim da mensagem (AA20), quando ligada e o nome é
     * conhecido. Sem nome, a mensagem segue sem ela — nunca um nome inventado.
     */
    fun withMachine(text: String, machine: String?): String =
        machine?.takeIf { name -> name.isNotBlank() }?.let { name -> "$text\n<i>💻 ${TelegramBotMessages.escape(name)}</i>" } ?: text

    /**
     * `/time` (AA16): por apelido — nunca e-mail nem máquina —, trabalhando agora,
     * online parado ou fora (com a última batida). Ordem total: trabalhando, online,
     * fora; depois apelido e dispositivo. [serverNow] é o agora do servidor
     * (`serverReferenceNow`), o mesmo relógio de `lastSeenAt`.
     */
    fun team(allowed: Boolean, presence: Result<List<TeamMemberPresence>>?, serverNow: Instant, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (!allowed) {
            return if (pt) {
                "🔒 Mostrar o time no bot está desligado. Ligue em Configurações › Alertas › Bot do Telegram."
            } else {
                "🔒 Showing the team in the bot is off. Turn it on in Settings › Alerts › Telegram bot."
            }
        }
        if (presence == null) {
            return if (pt) "👥 Nenhuma conta participa de um time neste app." else "👥 No account takes part in a team in this app."
        }
        val members = presence.getOrNull()
            ?: return if (pt) "👥 O servidor do time não respondeu agora." else "👥 The team server did not answer right now."
        if (members.isEmpty()) {
            return if (pt) "👥 Ninguém do time apareceu nos últimos 30 min." else "👥 Nobody from the team showed up in the last 30 min."
        }
        val ordered = members.sortedWith(
            compareBy<TeamMemberPresence> { entry -> if (entry.isWorkingNow) 0 else if (entry.isOnline) 1 else 2 }
                .thenBy { entry -> entry.alias }
                .thenBy { entry -> entry.deviceId }
        )
        val lines = ordered.map { entry ->
            val name = "<b>${TelegramBotMessages.escape(entry.alias)}</b>"
            when {
                entry.isWorkingNow -> "⚡ $name — " + (if (pt) "trabalhando · ${entry.activeSessionCount} sessão(ões)" else "working · ${entry.activeSessionCount} session(s)")
                entry.isOnline -> "🟢 $name — " + (if (pt) "online, parado" else "online, idle")
                else -> "⚪ $name — " + (entry.lastSeenAt?.let { at -> (if (pt) "visto " else "seen ") + TelegramBotMessages.elapsed(serverNow - at, pt) } ?: if (pt) "fora" else "away")
            }
        }
        val header = if (pt) "<b>👥 Time agora</b>" else "<b>👥 Team now</b>"
        return (listOf(header) + lines).joinToString("\n")
    }

    /**
     * Aviso da versão pronta (AA17). O texto diz **o que** reinicia — o app neste PC —,
     * como a faixa de atualização do app, e onde chega a resposta (#417).
     */
    fun updateReady(version: String, language: AppLanguage): String {
        val escaped = TelegramBotMessages.escape(version)
        return localized(
            language,
            "⬆ <b>Usage Monitor $escaped pronto para instalar</b>\nO app fecha e reabre neste PC para instalar. Depois do reinício, a confirmação e as novidades chegam aqui.",
            "⬆ <b>Usage Monitor $escaped ready to install</b>\nThe app closes and reopens on this PC to install. After the restart, the confirmation and what's new arrive here."
        )
    }

    /**
     * A resposta do app reaberto ao "Atualizar e reiniciar" (#417). [notes] é a busca das
     * novidades: sucesso nulo é release sem nada a mostrar (só `chore`/`docs`) e não vira
     * lista; falha aponta a janela do app, que tenta de novo sozinha; nulo é "não buscou".
     * Até [RESTART_NOTES_LIMIT] itens, os mesmos da janela, e o resto pelo link da release.
     */
    fun restartDone(
        outcome: TelegramRestartOutcome,
        pending: TelegramPendingRestart,
        currentVersion: String,
        notes: Result<ReleaseNotes?>?,
        language: AppLanguage
    ): String {
        val current = TelegramBotMessages.escape(currentVersion)
        if (outcome == TelegramRestartOutcome.NOT_APPLIED) {
            val target = TelegramBotMessages.escape(pending.targetVersion)
            return localized(
                language,
                "⚠ <b>A atualização para $target não foi aplicada</b>\nO app reabriu neste PC na versão $current.",
                "⚠ <b>The update to $target was not applied</b>\nThe app reopened on this PC on version $current."
            )
        }
        val header = localized(
            language,
            "✅ <b>Usage Monitor $current instalado</b>\nO app reiniciou neste PC.",
            "✅ <b>Usage Monitor $current installed</b>\nThe app restarted on this PC."
        )
        if (notes == null) return header
        val fetched = notes.getOrElse {
            return header + "\n" + localized(language, "Novidades na janela do app.", "What's new shows in the app.")
        } ?: return header
        val shown = fetched.items.take(RESTART_NOTES_LIMIT).map { item -> "• ${TelegramBotMessages.escape(item)}" }
        val hidden = fetched.items.size - shown.size
        val linkText = if (hidden > 0) {
            localized(language, "+$hidden na página da versão", "+$hidden on the release page")
        } else {
            localized(language, "Página da versão", "Release page")
        }
        val link = "<a href=\"${TelegramBotMessages.escape(fetched.releasePageUrl)}\">$linkText</a>"
        val title = "<b>" + localized(language, "Novidades", "What's new") + "</b>"
        return (listOf(header, "", title) + shown + link).joinToString("\n")
    }

    /** Itens das novidades na mensagem do reinício (#417): o resto fica no link da release. */
    const val RESTART_NOTES_LIMIT = 10

    fun updateButton(language: AppLanguage): String =
        if (language == AppLanguage.PT) "⬆ Atualizar e reiniciar o app" else "⬆ Update and restart the app"

    /** Resposta do toque (AA17): reiniciando, ou por que não. */
    fun updateTap(result: UpdateTapResult, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        return when (result) {
            UpdateTapResult.STARTED -> if (pt) "Reiniciando o app para instalar…" else "Restarting the app to install…"
            UpdateTapResult.NOT_ALLOWED -> if (pt) "Atualizar pelo bot está desligado." else "Updating from the bot is off."
            UpdateTapResult.STALE -> if (pt) "Essa versão não está mais pronta." else "That version is no longer ready."
        }
    }

    enum class UpdateTapResult { STARTED, NOT_ALLOWED, STALE }

    /** `/hud` (AA29): o pedido saiu, ou a permissão está desligada. */
    fun focusHud(allowed: Boolean, requested: Boolean, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        return when {
            !allowed && pt -> "🔒 Ações no PC pelo bot estão desligadas. Ligue em Configurações › Alertas › Bot do Telegram › Permitir ações no PC."
            !allowed -> "🔒 PC actions from the bot are off. Turn them on in Settings › Alerts › Telegram bot › Allow PC actions."
            !requested && pt -> "Não deu para pedir a HUD agora."
            !requested -> "Could not ask for the HUD right now."
            pt -> "🖥 HUD trazida para a frente no PC."
            else -> "🖥 HUD brought to the front on the PC."
        }
    }

    /** Confirmação do `/teclado` (AA30). */
    fun keyboard(on: Boolean, language: AppLanguage): String = when {
        on && language == AppLanguage.PT -> "⌨ Atalhos no lugar do teclado. <code>/teclado off</code> tira."
        on -> "⌨ Shortcuts in place of the keyboard. <code>/keyboard off</code> removes them."
        language == AppLanguage.PT -> "⌨ Atalhos removidos."
        else -> "⌨ Shortcuts removed."
    }

    /** Troca de página do teclado fixo (rodada AE, AE4): o Telegram só troca o teclado junto de uma mensagem. */
    fun keyboardPage(page: TelegramKeyboardPage, language: AppLanguage): String = when (page) {
        TelegramKeyboardPage.MORE -> localized(language, "☰ Mais atalhos.", "☰ More shortcuts.")
        TelegramKeyboardPage.MAIN -> localized(language, "‹ Atalhos principais.", "‹ Main shortcuts.")
    }

    /** O teclado novo (AE4) chegando a uma conversa pareada antes dele, uma vez. */
    fun keyboardIntroduced(language: AppLanguage): String = localized(
        language,
        "⌨ <b>Atalhos novos</b> no lugar do teclado: o uso diário aqui embaixo e o resto em ☰ Mais. " +
            "Todos os comandos continuam em /ajuda; <code>/teclado off</code> tira os atalhos.",
        "⌨ <b>New shortcuts</b> in place of the keyboard: daily use down here and the rest under ☰ More. " +
            "Every command is still in /help; <code>/keyboard off</code> removes the shortcuts."
    )

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
