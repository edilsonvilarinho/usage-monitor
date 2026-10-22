package com.usagemonitor.terminal

import com.usagemonitor.ConfigChange
import com.usagemonitor.ConfigOutcome
import com.usagemonitor.ConfigRefusals
import com.usagemonitor.DETAIL_CHAT
import com.usagemonitor.DETAIL_CODE
import com.usagemonitor.DETAIL_EXPIRES_AT
import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramUnauthorizedException
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalColor
import com.usagemonitor.presentation.terminal.TerminalConfigLabels
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalLine
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalSettingRow
import com.usagemonitor.presentation.terminal.TerminalSpan
import com.usagemonitor.presentation.terminal.TerminalTelegramLabels
import com.usagemonitor.presentation.terminal.terminalLine
import com.usagemonitor.presentation.ui.TelegramBotMessages
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * `telegram …` (#440, seção do bot e 5.2 do protótipo). Mudar a configuração vai pelo
 * [TerminalConfigClient]; falar com o Telegram (conferir o token, mandar o teste) sai
 * daqui mesmo, com o token lido do `telegram.json`. O pareamento é atendido pelo bot que
 * roda no coletor: o terminal pede o código a ele e espera a conversa aparecer no arquivo.
 */
internal class TerminalTelegramRunner(
    private val client: TerminalConfigClient,
    private val collector: () -> CollectorKind?,
    private val settings: () -> TelegramBotSettings,
    private val api: () -> TelegramBotApi,
    private val secretInput: TerminalSecretInput,
    private val clock: Clock = Clock.System,
    private val sleep: (Duration) -> Unit = { duration -> Thread.sleep(duration.inWholeMilliseconds) }
) {

    fun run(command: TelegramCommand, configLabels: TerminalConfigLabels, console: TerminalConsole): Int {
        val labels = TerminalTelegramLabels(configLabels.language)
        val output = when (command) {
            TelegramCommand.Setup -> setup(labels, configLabels, console)
            is TelegramCommand.Switch -> delivered(client.apply(ConfigChange.TelegramEnabled(command.enabled)), configLabels, console.profile, labels.switched(command.enabled)) { null }
            is TelegramCommand.Pair -> return pair(command, labels, configLabels, console)
            TelegramCommand.Test -> test(labels, console)
            TelegramCommand.Status -> status(labels, console)
            is TelegramCommand.Remove -> chatChange(ConfigChange.TelegramRemoveChat(command.chat), command.chat, labels, configLabels, console) { name -> labels.chatRemoved(name) }
            is TelegramCommand.ReadOnly -> chatChange(ConfigChange.TelegramChatReadOnly(command.chat, command.readOnly), command.chat, labels, configLabels, console) { name ->
                labels.chatReadOnly(name, command.readOnly)
            }
        }
        console.print(TerminalAnsi.render(output.lines, console.profile))
        return output.exitCode
    }

    /** Confere o token no Telegram (`getMe`) antes de gravar: token recusado não muda nada. */
    private fun setup(labels: TerminalTelegramLabels, configLabels: TerminalConfigLabels, console: TerminalConsole): TerminalOutput {
        val glyphs = console.profile.glyphs
        val token = secretInput.read(labels.tokenPrompt)?.trim()?.takeIf { text -> text.isNotEmpty() }
            ?: return TerminalOutput(TerminalConfigView.warning(configLabels.noSecretGiven, null, glyphs), TerminalConfigView.EXIT_REFUSED)
        val bot = runBlocking { runCatching { api().getMe(token) } }.getOrElse { error ->
            val text = if (error is TelegramUnauthorizedException) labels.tokenRejected else labels.tokenUnchecked(error.message ?: error::class.simpleName.orEmpty())
            return TerminalOutput(TerminalConfigView.warning(text, null, glyphs), TerminalConfigView.EXIT_REFUSED)
        }
        val saved = delivered(client.apply(ConfigChange.TelegramSetup(token)), configLabels, console.profile, labels.setupDone(bot)) { null }
        return if (saved.exitCode == TerminalConfigView.EXIT_OK) {
            TerminalOutput(saved.lines + TerminalConfigView.note(labels.pairNext) + TerminalConfigView.commandLine("usage-monitor telegram pair"), saved.exitCode)
        } else {
            saved
        }
    }

    /**
     * Pede o código ao coletor, mostra o que enviar e espera, relendo o `telegram.json`,
     * até uma conversa nova aparecer ou o código vencer.
     */
    private fun pair(command: TelegramCommand.Pair, labels: TerminalTelegramLabels, configLabels: TerminalConfigLabels, console: TerminalConsole): Int {
        val glyphs = console.profile.glyphs
        fun print(lines: List<TerminalLine>) = console.print(TerminalAnsi.render(lines, console.profile))
        if (collector() == null) {
            print(TerminalConfigView.warning(labels.needsCollector, "usage-monitor service run", glyphs))
            return TerminalConfigView.EXIT_REFUSED
        }
        val before = settings().authorizedChats.map { chat -> chat.id }.toSet()
        val delivery = client.apply(ConfigChange.TelegramStartPairing)
        val applied = (delivery as? ConfigDelivery.Answered)?.outcome as? ConfigOutcome.Applied
        if (applied == null) {
            print(
                delivered(delivery, configLabels, console.profile, "") { refused ->
                    if (refused.code == ConfigRefusals.NO_BOT_TOKEN) TerminalConfigView.warning(labels.needsToken, "usage-monitor telegram setup", glyphs) else null
                }.lines
            )
            return TerminalConfigView.EXIT_REFUSED
        }
        val code = applied.details[DETAIL_CODE].orEmpty()
        val expiresAt = applied.details[DETAIL_EXPIRES_AT]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds) ?: (clock.now() + PAIRING_FALLBACK)
        val deadline = command.timeout?.let { timeout -> minOf(expiresAt, clock.now() + timeout) } ?: expiresAt
        val bot = runCatching { runBlocking { api().getMe(settings().botToken) } }.getOrNull()
        val instructions = mutableListOf(terminalLine(TerminalSpan(labels.pairInstructions(bot, code), bold = true)))
        bot?.let { name -> instructions += TerminalConfigView.note(labels.pairLink(name, code)) }
        instructions += terminalLine(TerminalSpan(labels.waiting(TelegramBotMessages.clock(expiresAt)), TerminalColor.Muted))
        print(instructions)
        while (clock.now() < deadline) {
            sleep(PAIRING_POLL)
            val chat = settings().authorizedChats.firstOrNull { candidate -> candidate.id !in before }
            if (chat != null) {
                print(listOf(TerminalConfigView.done(labels.paired(chat.name), null, glyphs)))
                return TerminalConfigView.EXIT_OK
            }
        }
        print(TerminalConfigView.warning(labels.pairExpired, null, glyphs))
        return TerminalConfigView.EXIT_REFUSED
    }

    /** A mensagem de teste das Configurações, mandada daqui para cada conversa pareada. */
    private fun test(labels: TerminalTelegramLabels, console: TerminalConsole): TerminalOutput {
        val glyphs = console.profile.glyphs
        val current = settings()
        if (current.botToken.isBlank()) {
            return TerminalOutput(TerminalConfigView.warning(labels.needsToken, "usage-monitor telegram setup", glyphs), TerminalConfigView.EXIT_REFUSED)
        }
        if (current.authorizedChats.isEmpty()) {
            return TerminalOutput(TerminalConfigView.warning(labels.noChats, "usage-monitor telegram pair", glyphs), TerminalConfigView.EXIT_REFUSED)
        }
        val text = TelegramBotMessages.test(labels.language)
        val failure = runBlocking {
            runCatching { current.authorizedChats.forEach { chat -> api().sendMessage(current.botToken, chat.id, text, html = true) } }.exceptionOrNull()
        }
        return if (failure == null) {
            TerminalOutput(listOf(TerminalConfigView.done(labels.testSent(current.authorizedChats.size), null, glyphs)), TerminalConfigView.EXIT_OK)
        } else {
            TerminalOutput(TerminalConfigView.warning(labels.testFailed(failure.message ?: failure::class.simpleName.orEmpty()), null, glyphs), TerminalConfigView.EXIT_REFUSED)
        }
    }

    /** O que o card do bot mostra, sem o token. */
    private fun status(labels: TerminalTelegramLabels, console: TerminalConsole): TerminalOutput {
        val current = settings()
        val now = clock.now().toEpochMilliseconds()
        val chats = current.authorizedChats.joinToString(", ") { chat -> chatText(chat, labels) }.ifEmpty { labels.none }
        val openCode = current.pairingExpiresAtMillis?.takeIf { at -> current.pairingCode != null && now < at }
        val rows = listOf(
            TerminalSettingRow(labels.rowBot, if (current.enabled) labels.on else labels.off, masked = false),
            TerminalSettingRow(labels.rowToken, current.botToken.takeIf { token -> token.isNotBlank() }?.let { console.profile.glyphs.secretMask }, masked = true),
            TerminalSettingRow(labels.rowChats, chats, masked = false),
            TerminalSettingRow(labels.rowPairing, openCode?.let { at -> labels.codeOpen(TelegramBotMessages.clock(Instant.fromEpochMilliseconds(at))) } ?: labels.noCode, masked = false)
        )
        return TerminalConfigView.settings(rows, TerminalConfigLabels(labels.language), console.profile.glyphs)
    }

    private fun chatText(chat: TelegramChat, labels: TerminalTelegramLabels): String =
        "${chat.name} (${if (chat.readOnly) labels.readOnly else labels.control})"

    private fun chatChange(
        change: ConfigChange,
        chat: String,
        labels: TerminalTelegramLabels,
        configLabels: TerminalConfigLabels,
        console: TerminalConsole,
        doneText: (String) -> String
    ): TerminalOutput {
        val delivery = client.apply(change)
        val name = ((delivery as? ConfigDelivery.Answered)?.outcome as? ConfigOutcome.Applied)?.details?.get(DETAIL_CHAT) ?: chat
        return delivered(delivery, configLabels, console.profile, doneText(name)) { refused ->
            if (refused.code == ConfigRefusals.UNKNOWN_CHAT) TerminalConfigView.warning(labels.unknownChat(chat), "usage-monitor telegram status", console.profile.glyphs) else null
        }
    }

    private companion object {
        val PAIRING_POLL: Duration = 2.seconds
        val PAIRING_FALLBACK: Duration = 600.seconds
    }
}
