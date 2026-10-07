package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
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

    private fun mark(on: Boolean): String = if (on) "✅" else "⬜"

    private const val SOURCE_BUTTONS_PER_ROW = 2
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")
}
