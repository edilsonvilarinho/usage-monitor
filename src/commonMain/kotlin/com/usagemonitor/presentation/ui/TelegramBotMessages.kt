package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotButton
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.accountByKey
import com.usagemonitor.domain.entity.botAccountTapData
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
            "conta" to "Uma conta por vez",
            "atualizar" to "Coleta agora e mostra as cotas",
            "api" to "Fontes monitoradas",
            "resumo" to "Resumo do dia",
            "grafico" to "Gráfico do uso (24h ou 7d)",
            "alertas" to "Liga ou desliga os alertas (on ou off)",
            "silencio" to "Horário de silêncio (22-07 ou off)",
            "limiar" to "Limiares de alerta de cota (75,90)",
            "ajuda" to "Lista de comandos"
        )
    } else {
        listOf(
            "status" to "Quotas of every account",
            "account" to "One account at a time",
            "refresh" to "Collect now and show the quotas",
            "api" to "Monitored sources",
            "summary" to "Summary of the day",
            "chart" to "Usage chart (24h or 7d)",
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
            return noReading(pt)
        }
        // A hora é a da coleta, não a do pedido: o número pode ter minutos.
        val collected = snapshot.lastCollectedAt?.let { at ->
            (if (pt) "coleta " else "collected ") + "${clock(at)} BRT · " + elapsed(snapshot.generatedAt - at, pt)
        } ?: if (pt) "sem coleta ainda" else "not collected yet"
        val header = "<b>📊 Usage Monitor</b>\n<i>$collected</i>"
        val cards = snapshot.accounts.map { account -> accountCard(account, snapshot.generatedAt, language) }
        return (listOf(header) + cards).joinToString("\n\n")
    }

    /** Pergunta do `/conta` (#398, Y4); o teclado sai de [accountButtons]. */
    fun accountPicker(snapshot: UsageSnapshot?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) return noReading(pt)
        return if (pt) "Qual conta?" else "Which account?"
    }

    /**
     * Um botão por conta, duas por linha, com a palavra do pior risco no emoji —
     * o rótulo da conta já é o que o `/status` mostra.
     */
    fun accountButtons(snapshot: UsageSnapshot?): List<List<Pair<String, String>>> =
        snapshot?.accounts.orEmpty()
            .map { account ->
                val mark = account.worstRisk?.let { level -> riskEmoji(level) + " " }.orEmpty()
                (mark + account.label) to botAccountTapData(account)
            }
            .chunked(ACCOUNT_BUTTONS_PER_ROW)

    /** Só uma conta: o mesmo cartão do `/status`, com a hora da coleta dela. */
    fun account(snapshot: UsageSnapshot?, key: String, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val account = snapshot?.accountByKey(key)
            ?: return if (pt) "Essa conta não está mais na leitura. Mande /conta de novo." else "That account is no longer in the reading. Send /account again."
        val collected = account.fetchedAt?.let { at ->
            "<i>" + (if (pt) "coleta " else "collected ") + "${clock(at)} BRT · " + elapsed(snapshot.generatedAt - at, pt) + "</i>"
        } ?: "<i>" + (if (pt) "sem coleta ainda" else "not collected yet") + "</i>"
        return accountCard(account, snapshot.generatedAt, language) + "\n" + collected
    }

    internal fun noReading(pt: Boolean): String =
        if (pt) "Sem leitura ainda. Abra o Usage Monitor e habilite ao menos uma API." else "No reading yet. Open Usage Monitor and enable at least one API."

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

    internal fun quotaValue(quota: UsageSnapshotQuota, pt: Boolean): String {
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

    /** Teclado do `/status` (#396, W5): uma linha, nesta ordem. */
    fun statusButtons(language: AppLanguage): List<Pair<String, BotButton>> = if (language == AppLanguage.PT) {
        listOf("🔄 Atualizar" to BotButton.REFRESH, "🔕 Silenciar 1h" to BotButton.SNOOZE, "⚙ Limiares" to BotButton.THRESHOLDS)
    } else {
        listOf("🔄 Refresh" to BotButton.REFRESH, "🔕 Mute 1h" to BotButton.SNOOZE, "⚙ Thresholds" to BotButton.THRESHOLDS)
    }

    /** Aviso curto do Telegram enquanto o app coleta. */
    fun refreshing(language: AppLanguage): String = if (language == AppLanguage.PT) "Coletando…" else "Collecting…"

    fun snoozed(untilMillis: Long, language: AppLanguage): String {
        val until = clock(Instant.fromEpochMilliseconds(untilMillis))
        return if (language == AppLanguage.PT) {
            "🔕 Alertas silenciados até <b>$until</b>.\nPara voltar antes: <code>/alertas on</code>"
        } else {
            "🔕 Alerts muted until <b>$until</b>.\nTo resume earlier: <code>/alerts on</code>"
        }
    }

    fun thresholds(settings: UsageAlertSettings, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val percents = settings.quotaPercents.joinToString(", ") { percent -> "<b>$percent%</b>" }.ifEmpty { if (pt) "nenhum" else "none" }
        val off = if (settings.quotaAlertsEnabled) "" else if (pt) " (alertas de cota desligados)" else " (quota alerts off)"
        return if (pt) {
            "⚙ Limiares de cota: $percents$off\nMude com <code>/limiar 75,90</code>"
        } else {
            "⚙ Quota thresholds: $percents$off\nChange with <code>/threshold 75,90</code>"
        }
    }

    internal fun riskEmoji(level: UsageRiskLevel): String = when (level) {
        UsageRiskLevel.ON_TRACK -> "🟢"
        UsageRiskLevel.AT_RISK -> "🟡"
        UsageRiskLevel.WILL_EXCEED -> "🔴"
    }

    fun help(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "<b>Comandos</b>\n" +
            "<code>/status</code> — cotas de todas as contas\n" +
            "<code>/conta</code> — escolha uma conta e veja só ela\n" +
            "<code>/atualizar</code> — coleta agora e mostra as cotas\n" +
            "<code>/api</code> — fontes monitoradas\n" +
            "<code>/resumo</code> — resumo do dia\n" +
            "<code>/grafico</code> · <code>/grafico 7d</code> — gráfico do uso\n" +
            "<code>/alertas on</code> · <code>/alertas off</code> — liga ou desliga os alertas\n" +
            "<code>/silencio</code> — 1 h, 4 h ou até 08:00 · <code>/silencio 22-07</code> · <code>/silencio off</code>\n" +
            "<code>/limiar 75,90</code> — avisa em 75% e 90%\n" +
            "<i>Toque num comando para copiar.</i>"
    } else {
        "<b>Commands</b>\n" +
            "<code>/status</code> — quotas of every account\n" +
            "<code>/account</code> — pick one account and see only it\n" +
            "<code>/refresh</code> — collect now and show the quotas\n" +
            "<code>/api</code> — monitored sources\n" +
            "<code>/summary</code> — summary of the day\n" +
            "<code>/chart</code> · <code>/chart 7d</code> — usage chart\n" +
            "<code>/alerts on</code> · <code>/alerts off</code> — turn alerts on or off\n" +
            "<code>/quiet</code> — 1 h, 4 h or until 08:00 · <code>/quiet 22-07</code> · <code>/quiet off</code>\n" +
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

    internal fun elapsed(duration: kotlin.time.Duration, pt: Boolean): String {
        val minutes = duration.inWholeMinutes.coerceAtLeast(0L)
        return when {
            minutes < 1L -> if (pt) "há menos de 1 min" else "less than 1 min ago"
            minutes < 60L -> if (pt) "há $minutes min" else "$minutes min ago"
            else -> if (pt) "há ${minutes / 60} h" else "${minutes / 60} h ago"
        }
    }

    /** Reinício em menos de 24 h só com a hora; mais longe, com o dia da semana. */
    internal fun resetLabel(at: Instant, now: Instant, pt: Boolean): String {
        if (at - now < 24.hours) return clock(at)
        val day = at.toLocalDateTime(SAO_PAULO).dayOfWeek
        return "${weekday(day, pt)} ${clock(at)}"
    }

    internal fun weekday(day: DayOfWeek, pt: Boolean): String = when (day) {
        DayOfWeek.MONDAY -> if (pt) "seg" else "Mon"
        DayOfWeek.TUESDAY -> if (pt) "ter" else "Tue"
        DayOfWeek.WEDNESDAY -> if (pt) "qua" else "Wed"
        DayOfWeek.THURSDAY -> if (pt) "qui" else "Thu"
        DayOfWeek.FRIDAY -> if (pt) "sex" else "Fri"
        DayOfWeek.SATURDAY -> if (pt) "sáb" else "Sat"
        DayOfWeek.SUNDAY -> if (pt) "dom" else "Sun"
    }

    internal fun clock(instant: Instant): String {
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

    private const val ACCOUNT_BUTTONS_PER_ROW = 2
}
