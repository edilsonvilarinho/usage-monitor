package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.presentation.ui.components.riskLevelLabel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Textos do bot do Telegram (#387), no idioma do app. Só metadados de uso —
 * nunca prompt, resposta ou caminho de projeto.
 *
 * Tudo sai em HTML do Telegram (`parse_mode: HTML`, #396 direção W1): `<b>`,
 * `<i>` e `<code>` — um toque no `<code>` copia o comando no Telegram. Todo texto
 * variável passa por [escape]: um `<` no rótulo de uma conta faria o Telegram
 * recusar a mensagem inteira com 400.
 */
internal object TelegramBotMessages {

    /** Comandos do botão "Menu" do Telegram (`setMyCommands`), no idioma do app. */
    fun menu(language: AppLanguage): List<Pair<String, String>> = if (language == AppLanguage.PT) {
        listOf(
            "status" to "Cotas de todas as contas",
            "alertas" to "Liga ou desliga os alertas (on ou off)",
            "silencio" to "Horário de silêncio (22-07 ou off)",
            "limiar" to "Limiares de alerta de cota (75,90)",
            "ajuda" to "Lista de comandos"
        )
    } else {
        listOf(
            "status" to "Quotas of every account",
            "alerts" to "Turn alerts on or off",
            "quiet" to "Quiet hours (22-07 or off)",
            "threshold" to "Quota alert thresholds (75,90)",
            "help" to "List of commands"
        )
    }

    /** `/status`: um cartão por conta — risco, uso e, por cota, reinício e barra de 10 células. */
    fun status(snapshot: UsageSnapshot?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) {
            return if (pt) "Sem leitura ainda. Abra o Usage Monitor e habilite ao menos uma API." else "No reading yet. Open Usage Monitor and enable at least one API."
        }
        // A hora é a da coleta, não a do pedido: o número pode ter minutos.
        val collected = snapshot.lastCollectedAt?.let { at ->
            (if (pt) "coleta " else "collected ") + "${clock(at)} BRT · " + elapsed(snapshot.generatedAt - at, pt)
        } ?: if (pt) "sem coleta ainda" else "not collected yet"
        val header = "<b>📊 Usage Monitor</b>\n<i>$collected</i>"
        val cards = snapshot.accounts.map { account -> accountCard(account, snapshot.generatedAt, language) }
        return (listOf(header) + cards).joinToString("\n\n")
    }

    private fun accountCard(account: UsageSnapshotAccount, now: Instant, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val tags = listOfNotNull(
            account.worstRisk?.let { level -> "${riskEmoji(level)} ${riskLevelLabel(level, language)}" },
            if (account.active) (if (pt) "⚡ em uso" else "⚡ in use") else null
        )
        val title = "<b>${escape(account.label)}</b>" + if (tags.isEmpty()) "" else " — " + tags.joinToString(" · ")
        val quotas = account.quotas.map { quota ->
            val reset = quota.resetsAt?.let { at -> (if (pt) " · reinicia " else " · resets ") + resetLabel(at, now, pt) }.orEmpty()
            "${escape(quota.label)}$reset\n<code>${escape(quotaValue(quota, pt))}</code>"
        }
        return (listOf(title) + quotas).joinToString("\n")
    }

    private fun quotaValue(quota: UsageSnapshotQuota, pt: Boolean): String {
        val percent = quota.percent
        return when {
            percent != null -> "${bar(percent)} ${percent.toString().padStart(3)}%"
            quota.unit == UsageUnit.CURRENCY_USD -> (if (pt) "saldo " else "balance ") + formatCents(quota.total - quota.used, quota.currencyCode)
            quota.total > 0L -> "${quota.used} / ${quota.total}"
            else -> quota.used.toString()
        }
    }

    /** Dez células, cheias pelo piso do percentual: a barra nunca passa do número escrito ao lado. */
    internal fun bar(percent: Int): String {
        val filled = (percent.coerceIn(0, 100) / 10)
        return "▰".repeat(filled) + "▱".repeat(10 - filled)
    }

    private fun riskEmoji(level: UsageRiskLevel): String = when (level) {
        UsageRiskLevel.ON_TRACK -> "🟢"
        UsageRiskLevel.AT_RISK -> "🟡"
        UsageRiskLevel.WILL_EXCEED -> "🔴"
    }

    fun help(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "<b>Comandos</b>\n" +
            "<code>/status</code> — cotas de todas as contas\n" +
            "<code>/alertas on</code> · <code>/alertas off</code> — liga ou desliga os alertas\n" +
            "<code>/silencio 22-07</code> — silêncio das 22h às 7h · <code>/silencio off</code>\n" +
            "<code>/limiar 75,90</code> — avisa em 75% e 90%\n" +
            "<i>Toque num comando para copiar.</i>"
    } else {
        "<b>Commands</b>\n" +
            "<code>/status</code> — quotas of every account\n" +
            "<code>/alerts on</code> · <code>/alerts off</code> — turn alerts on or off\n" +
            "<code>/quiet 22-07</code> — quiet from 22h to 7h · <code>/quiet off</code>\n" +
            "<code>/threshold 75,90</code> — alert at 75% and 90%\n" +
            "<i>Tap a command to copy it.</i>"
    }

    fun paired(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "✅ <b>Conversa pareada.</b>\nToque em /status ou abra o <b>Menu</b> para ver os comandos."
    } else {
        "✅ <b>Chat paired.</b>\nTap /status or open the <b>Menu</b> for the commands."
    }

    fun applied(command: BotCommand, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        return when (command) {
            is BotCommand.Alerts -> if (command.enabled) (if (pt) "🔔 Alertas ligados." else "🔔 Alerts on.") else (if (pt) "🔕 Alertas desligados." else "🔕 Alerts off.")
            is BotCommand.Quiet -> command.hours?.let { hours ->
                val range = "${hours.startHour.toString().padStart(2, '0')}:00 → ${hours.endHour.toString().padStart(2, '0')}:00"
                if (pt) "🔕 Silêncio $range aplicado." else "🔕 Quiet hours $range set."
            } ?: if (pt) "🔔 Silêncio desligado." else "🔔 Quiet hours off."
            is BotCommand.Threshold -> (if (pt) "Limiares de cota: " else "Quota thresholds: ") + command.percents.joinToString(", ") { "<b>$it%</b>" }
            else -> help(language)
        }
    }

    fun invalid(usage: String, language: AppLanguage): String =
        (if (language == AppLanguage.PT) "Não entendi. Uso: " else "Not understood. Usage: ") + "<code>${escape(usage)}</code>"

    /** O alerta da bandeja, no mesmo texto: título e corpo de `usageAlertMessage`. */
    fun alert(alert: UsageAlert, language: AppLanguage): String {
        val message = usageAlertMessage(alert, language)
        return "<b>${escape(message.title)}</b>\n${escape(message.body)}"
    }

    fun test(language: AppLanguage): String =
        if (language == AppLanguage.PT) "✅ Mensagem de teste do Usage Monitor. Os alertas chegam aqui." else "✅ Usage Monitor test message. Alerts arrive here."

    /** O texto como o Telegram o mostra, sem as marcas — para a prévia das Configurações. */
    fun plain(html: String): String =
        html.replace(Regex("<[^>]+>"), "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    /** Escape do HTML do Telegram: só `&`, `<` e `>` são reservados fora de atributo. */
    internal fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun elapsed(duration: kotlin.time.Duration, pt: Boolean): String {
        val minutes = duration.inWholeMinutes.coerceAtLeast(0L)
        return when {
            minutes < 1L -> if (pt) "há menos de 1 min" else "less than 1 min ago"
            minutes < 60L -> if (pt) "há $minutes min" else "$minutes min ago"
            else -> if (pt) "há ${minutes / 60} h" else "${minutes / 60} h ago"
        }
    }

    /** Reinício em menos de 24 h só com a hora; mais longe, com o dia da semana. */
    private fun resetLabel(at: Instant, now: Instant, pt: Boolean): String {
        if (at - now < 24.hours) return clock(at)
        val day = at.toLocalDateTime(SAO_PAULO).dayOfWeek
        return "${weekday(day, pt)} ${clock(at)}"
    }

    private fun weekday(day: DayOfWeek, pt: Boolean): String = when (day) {
        DayOfWeek.MONDAY -> if (pt) "seg" else "Mon"
        DayOfWeek.TUESDAY -> if (pt) "ter" else "Tue"
        DayOfWeek.WEDNESDAY -> if (pt) "qua" else "Wed"
        DayOfWeek.THURSDAY -> if (pt) "qui" else "Thu"
        DayOfWeek.FRIDAY -> if (pt) "sex" else "Fri"
        DayOfWeek.SATURDAY -> if (pt) "sáb" else "Sat"
        DayOfWeek.SUNDAY -> if (pt) "dom" else "Sun"
    }

    private fun clock(instant: Instant): String {
        val local = instant.toLocalDateTime(SAO_PAULO)
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    private fun formatCents(cents: Long, currency: String): String {
        val units = cents / 100
        val rest = (cents % 100).toString().padStart(2, '0')
        val symbol = if (currency == "USD") "US$" else currency
        return "$symbol $units,$rest"
    }

    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")
}
