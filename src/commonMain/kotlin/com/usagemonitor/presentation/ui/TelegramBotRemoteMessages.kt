package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AlertKind
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.isOn
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.botAccountKey
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.SourceHealthEvent
import com.usagemonitor.domain.entity.BotTap
import com.usagemonitor.domain.entity.TELEGRAM_MORNING_HOUR
import com.usagemonitor.domain.entity.data
import com.usagemonitor.domain.entity.displayName
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Textos do controle remoto do bot (#398, direção Y8): `/api` e o menu do
 * `/silencio`. Arquivo próprio pelo mesmo motivo de [TelegramBotMessages] não
 * crescer sem fim; mesmo HTML do Telegram e mesmo escape.
 */
internal object TelegramBotRemoteMessages {

    /**
     * Lista das fontes com ✅/⬜. Sem permissão, a mensagem diz onde ligá-la e não
     * traz botão: um teclado que só responde "não pode" seria promessa falsa.
     */
    fun sources(enabled: Set<ApiSource>, allowed: Boolean, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val header = if (pt) "<b>Fontes monitoradas</b>" else "<b>Monitored sources</b>"
        if (allowed) {
            return header + "\n" + (if (pt) "Toque para ligar ou desligar." else "Tap to turn on or off.")
        }
        val lines = ApiSource.entries.joinToString("\n") { source -> mark(source in enabled) + " " + TelegramBotMessages.escape(source.displayName(language)) }
        val hint = if (pt) {
            "<i>Mudar fontes pelo bot está desligado em Configurações › Alertas › Bot do Telegram.</i>"
        } else {
            "<i>Changing sources from the bot is off in Settings › Alerts › Telegram bot.</i>"
        }
        return header + "\n" + lines + "\n\n" + hint
    }

    /** Um botão por fonte, duas por linha; o rótulo diz o estado atual. */
    fun sourceButtons(enabled: Set<ApiSource>, language: AppLanguage): List<List<Pair<String, String>>> =
        ApiSource.entries
            .map { source -> (mark(source in enabled) + " " + source.displayName(language)) to BotTap.Source(source).data() }
            .chunked(SOURCE_BUTTONS_PER_ROW)

    fun sourceToggled(source: ApiSource, nowEnabled: Boolean, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val name = "<b>" + TelegramBotMessages.escape(source.displayName(language)) + "</b>"
        return when {
            nowEnabled && pt -> "✅ $name ligada. A primeira leitura chega na próxima coleta."
            nowEnabled -> "✅ $name on. Its first reading comes with the next collection."
            pt -> "⬜ $name desligada."
            else -> "⬜ $name off."
        }
    }

    /** Aviso curto do toque quando a permissão foi desligada depois do teclado enviado. */
    fun sourceControlOff(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Desligado nas Configurações." else "Turned off in Settings."

    fun quietMenu(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "🔕 Silenciar alertas por quanto tempo?\nHorário fixo: <code>/silencio 22-07</code>"
    } else {
        "🔕 Mute alerts for how long?\nFixed hours: <code>/quiet 22-07</code>"
    }

    fun quietButtons(language: AppLanguage): List<List<Pair<String, String>>> {
        val until = if (language == AppLanguage.PT) "até" else "until"
        return listOf(
            listOf(
                "1 h" to BotTap.SnoozeFor(60).data(),
                "4 h" to BotTap.SnoozeFor(240).data(),
                "$until ${TELEGRAM_MORNING_HOUR.toString().padStart(2, '0')}:00" to BotTap.SnoozeUntilMorning.data()
            )
        )
    }

    /** Confirmação do silêncio com hora e, se não for hoje, o dia. */
    fun snoozedUntil(untilMillis: Long, nowMillis: Long, language: AppLanguage): String {
        val until = Instant.fromEpochMilliseconds(untilMillis).toLocalDateTime(SAO_PAULO)
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(SAO_PAULO).date
        val clock = "${until.hour.toString().padStart(2, '0')}:${until.minute.toString().padStart(2, '0')}"
        val pt = language == AppLanguage.PT
        val day = when {
            until.date == today -> ""
            pt -> " de amanhã"
            else -> " tomorrow"
        }
        return if (pt) {
            "🔕 Alertas silenciados até <b>$clock</b>$day.\nPara voltar antes: <code>/alertas on</code>"
        } else {
            "🔕 Alerts muted until <b>$clock</b>$day.\nTo resume earlier: <code>/alerts on</code>"
        }
    }

    /** `/alertas` sem argumento (#408, AA9). */
    fun alertMenu(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "<b>Alertas</b> — toque para ligar ou desligar cada tipo.\nTodos de uma vez: <code>/alertas on</code> · <code>/alertas off</code>"
    } else {
        "<b>Alerts</b> — tap to turn each kind on or off.\nAll at once: <code>/alerts on</code> · <code>/alerts off</code>"
    }

    /** Um botão por tipo, dois por linha; o rótulo diz o estado atual com ✅/⬜ **e** o nome. */
    fun alertButtons(settings: UsageAlertSettings, language: AppLanguage): List<List<Pair<String, String>>> =
        AlertKind.entries
            .map { kind -> (mark(settings.isOn(kind)) + " " + alertKindName(kind, language)) to BotTap.ToggleAlert(kind).data() }
            .chunked(SOURCE_BUTTONS_PER_ROW)

    fun alertKindName(kind: AlertKind, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        return when (kind) {
            AlertKind.QUOTA -> if (pt) "Cota" else "Quota"
            AlertKind.RESET -> if (pt) "Reinício" else "Reset"
            AlertKind.SESSION -> if (pt) "Sessão saturada" else "Saturated session"
            AlertKind.STALLED -> if (pt) "Sem resposta" else "No reply"
            AlertKind.SPIKE -> if (pt) "Pico" else "Spike"
        }
    }

    private fun mark(on: Boolean): String = if (on) "✅" else "⬜"

    private const val SOURCE_BUTTONS_PER_ROW = 2
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

    /** `/foco` (#408, AA8): até quando, ou encerrado — o que ficou retido sai na próxima coleta. */
    fun focus(untilMillis: Long?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (untilMillis == null) {
            return if (pt) "🔔 Foco encerrado. O que ficou retido chega na próxima coleta." else "🔔 Focus ended. What was held back arrives with the next collection."
        }
        val until = TelegramBotMessages.clock(kotlin.time.Instant.fromEpochMilliseconds(untilMillis))
        return if (pt) {
            "🎧 <b>Foco até $until</b>\nSó chegam alertas críticos (≥ 90%). <code>/foco off</code> encerra."
        } else {
            "🎧 <b>Focus until $until</b>\nOnly critical alerts (≥ 90%) come through. <code>/focus off</code> ends it."
        }
    }

    /**
     * Fonte quebrada e de volta (#408, AA18). O motivo é a mensagem que a tela já mostra
     * (saneada), escapada; a hora dos números mantidos diz o quanto eles envelheceram.
     */
    fun sourceHealth(event: SourceHealthEvent, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        return when (event) {
            is SourceHealthEvent.Broken -> {
                val label = TelegramBotMessages.escape(event.label)
                val since = TelegramBotMessages.clock(event.failingSince)
                val reason = TelegramBotMessages.escape(event.reason)
                val kept = event.lastReadingAt?.let { at ->
                    if (pt) "Os números mostrados são de ${TelegramBotMessages.clock(at)}." else "The numbers shown are from ${TelegramBotMessages.clock(at)}."
                } ?: if (pt) "Não há leitura guardada desta conta." else "There is no stored reading for this account."
                if (pt) {
                    "⚠ <b>$label parou de atualizar</b>\nFalhando desde $since: $reason\n$kept"
                } else {
                    "⚠ <b>$label stopped updating</b>\nFailing since $since: $reason\n$kept"
                }
            }
            is SourceHealthEvent.Recovered -> {
                val label = TelegramBotMessages.escape(event.label)
                val at = TelegramBotMessages.clock(event.at)
                if (pt) "✅ <b>$label voltou</b> — coleta $at." else "✅ <b>$label is back</b> — collected $at."
            }
        }
    }

    /** `/limiar` sem números (#408, AA10): a pergunta e um botão por conta com cota em percentual, mais "Todas". */
    fun thresholdMenu(language: AppLanguage): String =
        if (language == AppLanguage.PT) "<b>Limiar de qual conta?</b>" else "<b>Threshold for which account?</b>"

    fun thresholdButtons(snapshot: UsageSnapshot?, language: AppLanguage): List<List<Pair<String, String>>> {
        val accounts = snapshot?.accounts.orEmpty().filter { account -> account.targetKey != null && account.quotas.any { quota -> quota.percent != null } }
        val buttons = accounts.map { account -> account.label to BotTap.ThresholdAccount(botAccountKey(account)).data() } +
            ((if (language == AppLanguage.PT) "Todas" else "All") to BotTap.ThresholdAccount(null).data())
        return buttons.chunked(SOURCE_BUTTONS_PER_ROW)
    }

    /** Depois do toque: o que responder. [label] `null` é "todas as contas" — o limiar geral. */
    fun thresholdAsk(label: String?, general: List<Int>, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val current = general.joinToString(", ") { percent -> "$percent%" }.ifEmpty { if (pt) "nenhum" else "none" }
        if (label == null) {
            return if (pt) {
                "Limiar <b>geral</b> (hoje $current): responda com os números, ex.: <code>75,90</code>."
            } else {
                "<b>General</b> threshold (now $current): reply with the numbers, e.g. <code>75,90</code>."
            }
        }
        val name = TelegramBotMessages.escape(label)
        return if (pt) {
            "Limiar de <b>$name</b>: responda com os números, ex.: <code>50,80</code> — ou <code>geral</code> para seguir o geral ($current)."
        } else {
            "Threshold for <b>$name</b>: reply with the numbers, e.g. <code>50,80</code> — or <code>general</code> to follow the general one ($current)."
        }
    }

    /** O limiar gravado; [percents] `null` é a conta que voltou ao geral. */
    fun thresholdSet(label: String?, percents: List<Int>?, general: List<Int>, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val generalText = general.joinToString(", ") { percent -> "$percent%" }.ifEmpty { if (pt) "nenhum" else "none" }
        val name = label?.let(TelegramBotMessages::escape)
        return when {
            name == null && pt -> "Limiar geral: <b>$generalText</b>."
            name == null -> "General threshold: <b>$generalText</b>."
            percents == null && pt -> "<b>$name</b> volta a seguir o limiar geral ($generalText)."
            percents == null -> "<b>$name</b> follows the general threshold again ($generalText)."
            pt -> "<b>$name</b>: alertas em ${percents.joinToString(" e ") { percent -> "$percent%" }}.\nAs outras seguem $generalText."
            else -> "<b>$name</b>: alerts at ${percents.joinToString(" and ") { percent -> "$percent%" }}.\nThe others follow $generalText."
        }
    }
}
