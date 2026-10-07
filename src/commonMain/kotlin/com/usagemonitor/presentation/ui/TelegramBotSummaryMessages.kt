package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramDailySpend
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Resumo diário do bot (#398, direção Y1): uma linha por conta com a cota mais
 * cheia, o que reinicia hoje e o gasto do Claude Code nas últimas 24 h. Mesmo HTML
 * e mesmo escape de [TelegramBotMessages].
 */
internal object TelegramBotSummaryMessages {

    /**
     * [spend] `null` é "não medido" (índice indisponível): a linha de gasto some,
     * em vez de dizer US$ 0,00.
     */
    fun summary(snapshot: UsageSnapshot?, spend: TelegramDailySpend?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) return TelegramBotMessages.noReading(pt)
        val now = snapshot.generatedAt
        val local = now.toLocalDateTime(SAO_PAULO)
        val date = "${TelegramBotMessages.weekday(local.dayOfWeek, pt)} " +
            "${local.date.dayOfMonth.toString().padStart(2, '0')}/${local.date.monthNumber.toString().padStart(2, '0')}"
        val header = (if (pt) "<b>☀ Resumo de $date</b>" else "<b>☀ Summary for $date</b>")
        val collected = snapshot.lastCollectedAt?.let { at ->
            "<i>" + (if (pt) "coleta " else "collected ") + "${TelegramBotMessages.clock(at)} BRT · " +
                TelegramBotMessages.elapsed(now - at, pt) + "</i>"
        }
        val lines = snapshot.accounts.map { account -> accountLine(account, now, pt) }
        val resets = resetsToday(snapshot, now, pt)
        val spendLine = spend?.let { measured -> spendLine(measured, pt) }
        val footer = if (pt) "<i>/resumo a qualquer hora</i>" else "<i>/summary any time</i>"
        return listOfNotNull(
            listOfNotNull(header, collected).joinToString("\n"),
            lines.joinToString("\n"),
            listOfNotNull(resets, spendLine).joinToString("\n").ifEmpty { null },
            footer
        ).joinToString("\n\n")
    }

    /** A cota mais cheia da conta; sem percentual (saldo), a primeira, com o valor que o `/status` mostra. */
    private fun accountLine(account: UsageSnapshotAccount, now: Instant, pt: Boolean): String {
        val mark = account.worstRisk?.let { level -> TelegramBotMessages.riskEmoji(level) + " " }.orEmpty()
        val name = "<b>${TelegramBotMessages.escape(account.label)}</b>"
        val quota = account.quotas.filter { item -> item.percent != null }.maxByOrNull { item -> item.percent ?: 0 }
            ?: account.quotas.firstOrNull()
            ?: return mark + name
        val reset = quota.resetsAt?.let { at -> (if (pt) " · reinicia " else " · resets ") + TelegramBotMessages.resetLabel(at, now, pt) }.orEmpty()
        return "$mark$name · ${TelegramBotMessages.escape(quota.label)} ${valueOf(quota, pt)}$reset"
    }

    private fun valueOf(quota: UsageSnapshotQuota, pt: Boolean): String {
        val percent = quota.percent
        return if (percent != null) "<b>$percent%</b>" else TelegramBotMessages.escape(TelegramBotMessages.quotaValue(quota, pt))
    }

    /** Cotas que reiniciam ainda hoje (dia local), na ordem do relógio. */
    private fun resetsToday(snapshot: UsageSnapshot, now: Instant, pt: Boolean): String? {
        val today = now.toLocalDateTime(SAO_PAULO).date
        val entries = snapshot.accounts.flatMap { account ->
            account.quotas.mapNotNull { quota ->
                val at = quota.resetsAt?.takeIf { instant -> instant > now && instant.toLocalDateTime(SAO_PAULO).date == today }
                at?.let { instant -> instant to "${TelegramBotMessages.escape(account.label)} ${TelegramBotMessages.escape(quota.label)} " + (if (pt) "às " else "at ") + TelegramBotMessages.clock(instant) }
            }
        }.sortedBy { (instant, _) -> instant }
        if (entries.isEmpty()) return null
        return (if (pt) "<b>Reinicia hoje</b>: " else "<b>Resets today</b>: ") + entries.joinToString(" · ") { (_, text) -> text }
    }

    private fun spendLine(spend: TelegramDailySpend, pt: Boolean): String {
        val cents = spend.costMicros / MICROS_PER_CENT
        val value = "US$ ${cents / 100},${(cents % 100).toString().padStart(2, '0')}" + if (spend.unpricedTurns > 0) "+" else ""
        return if (pt) {
            "<b>Claude Code, últimas 24 h</b>: $value · ${spend.sessionCount} sessões"
        } else {
            "<b>Claude Code, last 24 h</b>: $value · ${spend.sessionCount} sessions"
        }
    }

    private const val MICROS_PER_CENT = 10_000L
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")
}
