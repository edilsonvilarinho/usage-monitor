package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramButton
import com.usagemonitor.domain.entity.ANALYSIS_SOURCES
import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AnalysisViewKind
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.BotTap
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.accountByKey
import com.usagemonitor.domain.entity.botAccountKey
import com.usagemonitor.domain.entity.data
import com.usagemonitor.presentation.ui.TelegramAnalysisMessages
import com.usagemonitor.presentation.ui.TelegramAnalysisTexts

/**
 * O `/analise` e as abas dele (rodada AB). Mora fora do [TelegramBotHandlers] pelo tamanho:
 * escolha da conta, abas que editam a mensagem no lugar (AB12) e as vistas de imagem e
 * arquivo, que saem como mensagem nova.
 */
internal class TelegramAnalysisHandler(
    private val api: TelegramBotApi,
    private val snapshotProvider: () -> UsageSnapshot?,
    private val readings: TelegramBotReadings,
    /** A assinatura do PC (AA20) quando ligada. */
    private val signed: (String) -> String
) {

    suspend fun command(token: String, chatId: Long, command: BotCommand.Analysis, language: AppLanguage) {
        val accounts = analyzableAccounts().filter { account -> command.source == null || account.source == command.source }
        when {
            accounts.isEmpty() -> send(token, chatId, TelegramAnalysisMessages.noAccount(language))
            command.all -> scoreboard(token, chatId, accounts, language)
            accounts.size == 1 -> show(token, chatId, accounts.single(), command.view ?: AnalysisView.PANEL, editMessageId = null, language)
            else -> api.sendMessage(
                token, chatId, TelegramAnalysisMessages.pickAccount(language), html = true,
                buttons = (
                    accounts.map { account -> TelegramButton(account.label, BotTap.AnalysisAccount(botAccountKey(account)).data()) } +
                        TelegramButton(TelegramAnalysisMessages.scoreboardButton(language), BotTap.AnalysisAccount(null).data())
                    ).chunked(2)
            )
        }
    }

    /** AB13 · placar: a análise de cada conta, lado a lado; conta sem histórico fica fora. */
    private suspend fun scoreboard(token: String, chatId: Long, accounts: List<UsageSnapshotAccount>, language: AppLanguage) {
        val analyses = accounts.mapNotNull { account -> account.targetKey?.let { key -> readings.sessionAnalysis(key) } }
        if (analyses.isEmpty()) return send(token, chatId, TelegramAnalysisMessages.notReady(language))
        send(token, chatId, TelegramAnalysisTexts.scoreboard(analyses, language))
    }

    /** Toque na escolha da conta ou numa aba; a conta é relida do retrato de agora. */
    suspend fun tap(token: String, chatId: Long, messageId: Long?, callbackId: String, tap: BotTap, language: AppLanguage) {
        api.answerCallbackQuery(token, callbackId)
        val (key, view, editId) = when (tap) {
            is BotTap.AnalysisAccount -> Triple(tap.key, AnalysisView.PANEL, null)
            is BotTap.AnalysisShow -> Triple(tap.key, tap.view, messageId)
            else -> return
        }
        if (key == null) return scoreboard(token, chatId, analyzableAccounts(), language)
        val account = snapshotProvider()?.accountByKey(key)?.takeIf { item -> item in analyzableAccounts() }
        if (account == null) {
            send(token, chatId, TelegramAnalysisMessages.noAccount(language))
            return
        }
        show(token, chatId, account, view, editId, language)
    }

    private suspend fun show(token: String, chatId: Long, account: UsageSnapshotAccount, view: AnalysisView, editMessageId: Long?, language: AppLanguage) {
        val targetKey = account.targetKey ?: return send(token, chatId, TelegramAnalysisMessages.noAccount(language))
        val analysis = readings.sessionAnalysis(targetKey) ?: return send(token, chatId, TelegramAnalysisMessages.notReady(language))
        val accountKey = botAccountKey(account)
        val shown = view.takeIf { item -> item in TelegramAnalysisMessages.availableViews } ?: AnalysisView.PANEL
        when (shown.kind) {
            AnalysisViewKind.TEXT -> {
                val text = signed(TelegramAnalysisMessages.text(shown, analysis, language) ?: TelegramAnalysisMessages.panel(analysis, language))
                val buttons = TelegramAnalysisMessages.tabs(accountKey, shown, language).map { row -> row.map { (label, data) -> TelegramButton(label, data) } }
                if (editMessageId != null) {
                    api.editMessageText(token, chatId, editMessageId, text, buttons)
                } else {
                    api.sendMessage(token, chatId, text, html = true, buttons = buttons)
                }
            }
            AnalysisViewKind.IMAGE, AnalysisViewKind.FILE -> send(token, chatId, TelegramAnalysisMessages.panel(analysis, language))
        }
    }

    private fun analyzableAccounts(): List<UsageSnapshotAccount> =
        snapshotProvider()?.accounts.orEmpty().filter { account -> account.source in ANALYSIS_SOURCES && account.targetKey != null }

    private suspend fun send(token: String, chatId: Long, text: String) {
        api.sendMessage(token, chatId, signed(text), html = true)
    }
}
