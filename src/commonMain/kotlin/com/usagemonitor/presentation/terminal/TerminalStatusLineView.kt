package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.CliSessionMetrics
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.formatMicrosUsdShort
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A linha de status do Claude Code (telas 1.1 e 1.2 do protótipo): a conta que **este**
 * Claude Code está usando, as janelas de 5 h e 7 d, o custo e a vazão da sessão em curso e
 * o modelo. Abaixo do limiar de atenção a linha só informa; acima, escreve o estado e
 * quanto falta para reiniciar no lugar da hora.
 *
 * Cada parte só aparece quando foi medida: sem conta reconhecida, sem cotas; sem a sessão
 * no índice, sem custo (não medido não vira zero); sem turno medido, sem vazão.
 */
internal object TerminalStatusLineView {
    private const val BAR_WIDTH = 12

    /** Leitura mais velha que isto aparece com a hora dela: leitura velha não se passa por atual. */
    private val STALE_AFTER = 10.minutes

    fun claudeCode(
        account: TerminalAccount?,
        session: CliSessionMetrics?,
        model: String?,
        now: Instant,
        labels: TerminalLabels,
        profile: TerminalProfile
    ): TerminalLine {
        val parts = mutableListOf<List<TerminalSpan>>()
        if (account != null) {
            val fiveHour = account.quotas.firstOrNull { quota -> quota.label == AnthropicQuotaLabels.FIVE_HOUR }
            val sevenDay = account.quotas.firstOrNull { quota -> quota.label == AnthropicQuotaLabels.SEVEN_DAY }
            parts += head(account, fiveHour, profile)
            fiveHour?.percent?.let { percent -> parts += window("5h", percent, fiveHour, account, now, labels, profile, withBar = true) }
            sevenDay?.percent?.let { percent -> parts += window("7d", percent, sevenDay, account, now, labels, profile, withBar = false) }
            account.fetchedAt?.takeIf { fetched -> now - fetched > STALE_AFTER }?.let { fetched ->
                parts += listOf(TerminalSpan(labels.readingFrom(TelegramBotMessages.clock(fetched)), TerminalColor.Warn))
            }
        }
        if (session != null) {
            val partial = if (session.unpricedTurns > 0) "+" else ""
            parts += listOf(TerminalSpan(labels.session + " "), TerminalSpan(formatMicrosUsdShort(session.costMicros) + partial, bold = true))
            session.throughput?.tokensPerSecond?.let { rate -> parts += listOf(TerminalSpan("${rate.roundToLong()} tok/s")) }
        }
        model?.takeIf { name -> name.isNotBlank() }?.let { name -> parts += listOf(TerminalSpan(name, TerminalColor.Muted)) }
        val separator = TerminalSpan(" ${profile.glyphs.separator} ", TerminalColor.Muted)
        return TerminalLine(parts.flatMapIndexed { index, part -> if (index == 0) part else listOf(separator) + part })
    }

    private fun head(account: TerminalAccount, fiveHour: UsageSnapshotQuota?, profile: TerminalProfile): List<TerminalSpan> {
        val color = TerminalColor.Source(account.source)
        val name = account.profile ?: account.name
        val ring = fiveHour?.percent?.let(profile.glyphs::ring)
        return listOf(TerminalSpan(listOfNotNull(ring, name).joinToString(" "), color))
    }

    private fun window(
        name: String,
        percent: Int,
        quota: UsageSnapshotQuota,
        account: TerminalAccount,
        now: Instant,
        labels: TerminalLabels,
        profile: TerminalProfile,
        withBar: Boolean
    ): List<TerminalSpan> {
        val zone = account.zoneOf(quota)
        val pressing = zone == QuotaZone.ATTENTION || zone == QuotaZone.CRITICAL
        val spans = mutableListOf(TerminalSpan("$name "), TerminalSpan("$percent%", bold = true))
        if (pressing) {
            spans += TerminalSpan(" ")
            spans += TerminalPrimitives.zoneWord(zone!!, labels, profile)
        }
        if (withBar) {
            spans += TerminalSpan(" ")
            spans += TerminalPrimitives.quotaBar(percent, BAR_WIDTH, TerminalColor.Source(account.source), profile.glyphs)
            quota.resetsAt?.takeIf { at -> at > now }?.let { at ->
                val reset = if (pressing) labels.resetsIn(at - now) else TelegramBotMessages.clock(at)
                spans += TerminalSpan(" $reset", TerminalColor.Muted)
            }
        }
        return spans
    }
}
