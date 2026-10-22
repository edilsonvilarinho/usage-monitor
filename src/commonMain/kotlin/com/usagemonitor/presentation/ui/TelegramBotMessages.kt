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
import com.usagemonitor.domain.entity.TelegramBatchItem
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

    /** Comandos do botão "Menu" do Telegram (`setMyCommands`), no idioma do app, por categoria. */
    fun menu(language: AppLanguage): List<Pair<String, String>> = TelegramCommandCatalog.menu(language)

    /**
     * `/status`: um cartão por conta — risco, uso e, por cota, reinício e a barra de
     * quadrados por zona (rodada AE, direção AE7), com a legenda dos limiares gerais no fim.
     * A sparkline das últimas 6 h (AA13) saiu: os blocos viravam um retângulo no celular.
     */
    fun status(snapshot: UsageSnapshot?, language: AppLanguage, alertSettings: UsageAlertSettings = UsageAlertSettings.DEFAULT): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) {
            return noReading(pt)
        }
        // A hora é a da coleta, não a do pedido: o número pode ter minutos.
        val collected = snapshot.lastCollectedAt?.let { at ->
            (if (pt) "coleta " else "collected ") + "${clock(at)} BRT · " + elapsed(snapshot.generatedAt - at, pt)
        } ?: if (pt) "sem coleta ainda" else "not collected yet"
        val header = "<b>📊 Usage Monitor</b>\n<i>$collected</i>"
        val cards = snapshot.accounts.map { account -> accountCard(account, snapshot.generatedAt, language, alertSettings) }
        val hasBar = snapshot.accounts.any { account -> account.quotas.any { quota -> quota.percent != null } }
        val legend = zoneLegend(alertSettings.effectiveQuotaPercents, language)?.takeIf { hasBar }?.let { text -> "<i>$text</i>" }
        return (listOf(header) + cards + listOfNotNull(legend)).joinToString("\n\n")
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
    fun account(snapshot: UsageSnapshot?, key: String, language: AppLanguage, alertSettings: UsageAlertSettings = UsageAlertSettings.DEFAULT): String {
        val pt = language == AppLanguage.PT
        val account = snapshot?.accountByKey(key)
            ?: return if (pt) "Essa conta não está mais na leitura. Mande /conta de novo." else "That account is no longer in the reading. Send /account again."
        val collected = account.fetchedAt?.let { at ->
            "<i>" + (if (pt) "coleta " else "collected ") + "${clock(at)} BRT · " + elapsed(snapshot.generatedAt - at, pt) + "</i>"
        } ?: "<i>" + (if (pt) "sem coleta ainda" else "not collected yet") + "</i>"
        // Só uma conta: a legenda dela vai no cartão, com ou sem limiar próprio.
        return accountCard(account, snapshot.generatedAt, language, alertSettings, alwaysLegend = true) + "\n" + collected
    }

    internal fun noReading(pt: Boolean): String =
        if (pt) "Sem leitura ainda. Abra o Usage Monitor e habilite ao menos uma API." else "No reading yet. Open Usage Monitor and enable at least one API."

    /**
     * O cartão de uma conta: o nome numa linha e o risco na seguinte — juntos quebravam no
     * celular. Conta com limiar próprio (AA10) leva a legenda dela, porque a do rodapé é a geral.
     */
    private fun accountCard(
        account: UsageSnapshotAccount,
        now: Instant,
        language: AppLanguage,
        alertSettings: UsageAlertSettings,
        alwaysLegend: Boolean = false
    ): String {
        val pt = language == AppLanguage.PT
        val tags = listOfNotNull(
            account.worstRisk?.let { level -> "${riskEmoji(level)} ${riskLevelLabel(level, language)}" },
            if (account.active) (if (pt) "⚡ em uso" else "⚡ in use") else null
        )
        val thresholds = alertSettings.quotaPercentsFor(account.targetKey)
        val ownThresholds = account.targetKey?.let { key -> key in alertSettings.quotaPercentsByTarget } == true
        val prefix = if (ownThresholds) localized(language, "limiar próprio: ", "own threshold: ") else ""
        val legend = zoneLegend(thresholds, language)
            ?.takeIf { (ownThresholds || alwaysLegend) && account.quotas.any { quota -> quota.percent != null } }
            ?.let { text -> "<i>$prefix$text</i>" }
        val head = listOfNotNull("<b>${escape(account.label)}</b>", tags.takeIf { list -> list.isNotEmpty() }?.joinToString(" · "), legend)
        val quotas = account.quotas.map { quota ->
            val reset = quota.resetsAt?.let { at -> (if (pt) " · reinicia " else " · resets ") + resetLabel(at, now, pt) }.orEmpty()
            "${escape(displayQuotaLabel(quota.label, pt))}$reset\n${quotaLine(quota, pt, thresholds)}"
        }
        return (head + quotas).joinToString("\n")
    }

    /**
     * A linha do valor de uma cota: com percentual, a barra de quadrados e o número em
     * negrito; saldo e contagem seguem em `<code>`, como antes.
     */
    internal fun quotaLine(quota: UsageSnapshotQuota, pt: Boolean, thresholds: List<Int>): String {
        val percent = quota.percent ?: return "<code>${escape(quotaValue(quota, pt))}</code>"
        return "${zoneBar(percent, thresholds)} <b>$percent%</b>"
    }

    internal fun quotaValue(quota: UsageSnapshotQuota, pt: Boolean): String {
        val percent = quota.percent
        return when {
            percent != null -> "$percent%"
            quota.unit == UsageUnit.CURRENCY_USD -> (if (pt) "saldo " else "balance ") + formatCents(quota.total - quota.used, quota.currencyCode, pt)
            quota.total > 0L -> "${quota.used} / ${quota.total}"
            else -> quota.used.toString()
        }
    }

    /**
     * Barra da rodada AE (direção AE7): dez quadrados, cheios pelo **piso** do percentual —
     * a barra nunca passa do número escrito ao lado. A cor de cada quadrado diz quantos
     * [thresholds] (normalizados) o limite superior da célula já passou: nenhum verde, um
     * amarelo, dois ou mais vermelho. Com os padrões 75/90/100, 80 e 90 são amarelos e 100
     * vermelho. Sem limiar, tudo verde. Emoji tem largura fixa no celular, o que `▰▱` em
     * `<code>` não tinha.
     */
    internal fun zoneBar(percent: Int, thresholds: List<Int>): String {
        val filled = percent.coerceIn(0, 100) / 10
        return (1..10).joinToString("") { cell ->
            val passed = thresholds.count { threshold -> threshold < cell * 10 }
            when {
                cell > filled -> EMPTY_CELL
                passed == 0 -> SAFE_CELL
                passed == 1 -> WARN_CELL
                else -> OVER_CELL
            }
        }
    }

    /** O que cada cor da [zoneBar] quer dizer com estes limiares; `null` sem limiar (tudo verde). */
    internal fun zoneLegend(thresholds: List<Int>, language: AppLanguage): String? {
        val first = thresholds.firstOrNull() ?: return null
        val upTo = localized(language, "até", "up to")
        val above = localized(language, "acima", "above")
        val second = thresholds.getOrNull(1) ?: return "$SAFE_CELL $upTo $first% · $WARN_CELL $above"
        return "$SAFE_CELL $upTo $first% · $WARN_CELL $upTo $second% · $OVER_CELL $above"
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
        // Limiar por conta (#408, AA10): uma linha por conta com limiar próprio, por rótulo.
        val own = settings.quotaPercentsByTarget.values.sortedBy { override -> override.label }.joinToString("") { override ->
            "\n• ${escape(override.label)}: " + override.percents.joinToString(", ") { percent -> "<b>$percent%</b>" }
        }
        return if (pt) {
            "⚙ Limiares de cota: $percents$off$own\nMude com <code>/limiar 75,90</code> ou, por conta, <code>/limiar</code>"
        } else {
            "⚙ Quota thresholds: $percents$off$own\nChange with <code>/threshold 75,90</code> or, per account, <code>/threshold</code>"
        }
    }

    internal fun riskEmoji(level: UsageRiskLevel): String = when (level) {
        UsageRiskLevel.ON_TRACK -> "🟢"
        UsageRiskLevel.AT_RISK -> "🟡"
        UsageRiskLevel.WILL_EXCEED -> "🔴"
    }

    /** O `/ajuda`, por categoria ([TelegramCommandCatalog]). */
    fun help(language: AppLanguage): String = TelegramCommandCatalog.help(language)

    fun paired(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "✅ <b>Conversa pareada.</b>\nOs atalhos estão aqui embaixo, no lugar do teclado; /ajuda lista todos os comandos."
    } else {
        "✅ <b>Chat paired.</b>\nThe shortcuts are down here, in place of the keyboard; /help lists every command."
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
            is BotCommand.Remind -> command.minutes?.let { minutes ->
                if (pt) {
                    "⏰ Combinado: aviso <b>$minutes min</b> antes de reiniciar qualquer cota acima de um limiar."
                } else {
                    "⏰ Done: I'll warn <b>$minutes min</b> before any quota above a threshold resets."
                }
            } ?: if (pt) "⏰ Lembrete antes do reinício desligado." else "⏰ Reminder before resets off."
            else -> help(language)
        }
    }

    fun invalid(usage: String, language: AppLanguage): String =
        (if (language == AppLanguage.PT) "Não entendi. Uso: " else "Not understood. Usage: ") + "<code>${escape(usage)}</code>"

    /**
     * O alerta da bandeja, no mesmo texto: título e corpo de `usageAlertMessage`. Alerta de
     * cota (limiar e reinício próximo) leva a barra da AE7 com os limiares daquela conta.
     */
    fun alert(alert: UsageAlert, language: AppLanguage, alertSettings: UsageAlertSettings = UsageAlertSettings.DEFAULT): String {
        val message = usageAlertMessage(alert, language)
        val bar = alertBar(alert, alertSettings)?.let { line -> "\n$line" }.orEmpty()
        return "<b>${escape(message.title)}</b>$bar\n${escape(message.body)}"
    }

    private fun alertBar(alert: UsageAlert, alertSettings: UsageAlertSettings): String? {
        val (target, percent) = when (alert) {
            is UsageAlert.QuotaThreshold -> alert.target to alert.actualPercent
            is UsageAlert.ResetSoon -> alert.target to alert.actualPercent
            else -> return null
        }
        return "${zoneBar(percent, alertSettings.quotaPercentsFor(target))} <b>$percent%</b>"
    }

    /**
     * Lote de alertas (#408, AA25): cabeçalho com a quantidade e a faixa de horário (BRT),
     * um tópico por item com título, repetições e corpo — o mesmo texto do alerta avulso.
     */
    fun alertBatch(items: List<TelegramBatchItem>, from: Instant, to: Instant, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val total = items.sumOf { item -> item.count }
        val header = "<b>🔔 $total " + (if (pt) "alertas" else "alerts") + " · ${clock(from)}–${clock(to)}</b>"
        val lines = items.map { item ->
            val message = usageAlertMessage(item.alert, language)
            val repeated = if (item.count > 1) " (${item.count}×)" else ""
            "• <b>${escape(message.title)}</b>$repeated: ${escape(message.body)}"
        }
        return (listOf(header) + lines).joinToString("\n")
    }

    /**
     * O mesmo alerta repetido no dia (#408, AA1): a mensagem anterior editada com o
     * contador, a hora da primeira e a da última.
     */
    fun repeatedAlert(
        alert: UsageAlert,
        count: Int,
        firstAt: Instant,
        lastAt: Instant,
        language: AppLanguage,
        alertSettings: UsageAlertSettings = UsageAlertSettings.DEFAULT
    ): String {
        val message = usageAlertMessage(alert, language)
        val pt = language == AppLanguage.PT
        val bar = alertBar(alert, alertSettings)?.let { line -> "\n$line" }.orEmpty()
        return "<b>${escape(message.title)}</b> · <b>$count×</b> " + (if (pt) "desde " else "since ") + clock(firstAt) +
            "$bar\n${escape(message.body)}\n<i>" + (if (pt) "última: " else "last: ") + clock(lastAt) + "</i>"
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

    /** O separador decimal é o do idioma da mensagem: `US$ 7,82` em português, `US$ 7.82` em inglês. */
    private fun formatCents(cents: Long, currency: String, pt: Boolean): String {
        val units = cents / 100
        val rest = (cents % 100).toString().padStart(2, '0')
        val symbol = if (currency == "USD") "US$" else currency
        return "$symbol $units${if (pt) ',' else '.'}$rest"
    }

    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

    private const val ACCOUNT_BUTTONS_PER_ROW = 2

    private const val EMPTY_CELL = "⬛"
    private const val SAFE_CELL = "🟩"
    private const val WARN_CELL = "🟨"
    private const val OVER_CELL = "🟥"
}
