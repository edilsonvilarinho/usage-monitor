package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.displayQuotaLabel
import kotlin.time.Duration
import kotlin.time.Instant

/** O que um comando de leitura imprime e com que código sai. */
internal data class TerminalOutput(val lines: List<TerminalLine>, val exitCode: Int)

/**
 * `usage-monitor status` (telas 2.2 e 6.1 do protótipo): uma linha por cota, na ordem do
 * painel, e o rodapé de leitura. Sai com 0, ou com 3 ("sem leitura") quando não há
 * leitura nenhuma ou quando ela é mais velha que o `--max-age` pedido.
 *
 * A coluna de cota mostra o rótulo do app ("Claude 5h", "Go semanal"), e não uma janela
 * abreviada: os rótulos são chave de série e variam por fonte ("Cursor Auto", "Codex limit
 * … (7d)"), e só o rótulo diz a mesma coisa que a HUD e o bot.
 */
internal object TerminalStatusView {
    const val EXIT_OK = 0
    const val EXIT_NO_READING = 3

    private const val BAR_WIDTH = 20
    private const val PERCENT_WIDTH = 4
    private const val GAP = "  "

    fun render(
        accounts: List<TerminalAccount>,
        now: Instant,
        /** Quem coleta agora; `null` quando nada coleta. */
        collector: CollectorKind?,
        maxAge: Duration?,
        labels: TerminalLabels,
        profile: TerminalProfile
    ): TerminalOutput {
        val lastCollectedAt = accounts.mapNotNull { account -> account.fetchedAt }.maxOrNull()
        if (accounts.isEmpty() || lastCollectedAt == null) {
            val line = terminalLine(terminalSpan(profile.glyphs.warning + " ", TerminalColor.Warn), terminalSpan(labels.noReadingYet))
            return TerminalOutput(listOf(line), EXIT_NO_READING)
        }
        val age = now - lastCollectedAt
        if (maxAge != null && age > maxAge) {
            val line = terminalLine(terminalSpan(labels.noReadingWord, TerminalColor.Crit), terminalSpan(GAP + labels.staleReading(age, maxAge)))
            return TerminalOutput(listOf(line), EXIT_NO_READING)
        }
        val lines = mutableListOf<TerminalLine>()
        if (collector == null) {
            val at = TelegramBotMessages.clock(lastCollectedAt)
            lines += terminalLine(terminalSpan(profile.glyphs.warning + " ", TerminalColor.Warn), terminalSpan(labels.nothingCollecting(at, age)))
            lines += terminalLine(terminalSpan("  " + labels.openAppToRefresh, TerminalColor.Muted))
            lines += TerminalLine.EMPTY
        }
        lines += table(accounts, now, labels, profile)
        if (collector != null) {
            lines += TerminalLine.EMPTY
            lines += terminalLine(terminalSpan(" ")) + TerminalPrimitives.readingFooter(collector, age, zonesOf(accounts), labels)
        }
        return TerminalOutput(lines, EXIT_OK)
    }

    private fun zonesOf(accounts: List<TerminalAccount>): List<QuotaZone?> =
        accounts.flatMap { account -> account.quotas.map(account::zoneOf) }

    private fun table(accounts: List<TerminalAccount>, now: Instant, labels: TerminalLabels, profile: TerminalProfile): List<TerminalLine> {
        val sourceWidth = maxOf(labels.headerSource.length, accounts.maxOf { account -> account.name.length } + 2)
        val accountWidth = maxOf(labels.headerAccount.length, accounts.maxOf { account -> account.profile.orEmpty().length })
        val quotaWidth = maxOf(labels.headerQuota.length, accounts.flatMap { account -> account.quotas }.maxOfOrNull { quota -> displayQuotaLabel(quota.label, labels.pt).length } ?: 0)
        val usageWidth = BAR_WIDTH + 1 + PERCENT_WIDTH
        val stateWidth = maxOf(labels.headerState.length, labels.zoneColumnWidth)
        val header = " " + listOf(
            labels.headerSource.padEnd(sourceWidth),
            labels.headerAccount.padEnd(accountWidth),
            labels.headerQuota.padEnd(quotaWidth),
            labels.headerUsage.padEnd(usageWidth),
            labels.headerState.padEnd(stateWidth),
            labels.headerResets
        ).joinToString(GAP)
        val widths = intArrayOf(sourceWidth, accountWidth, quotaWidth, usageWidth, stateWidth)
        val rows = accounts.flatMap { account ->
            account.quotas.flatMapIndexed { index, quota ->
                listOfNotNull(row(account, quota, first = index == 0, widths = widths, now, labels, profile), timeRow(quota, widths, now, labels, profile))
            }
        }
        return listOf(terminalLine(TerminalSpan(header, TerminalColor.Muted, bold = true))) + rows
    }

    private fun row(
        account: TerminalAccount,
        quota: UsageSnapshotQuota,
        first: Boolean,
        widths: IntArray,
        now: Instant,
        labels: TerminalLabels,
        profile: TerminalProfile
    ): TerminalLine {
        val (sourceWidth, accountWidth, quotaWidth, usageWidth, stateWidth) = widths.toList()
        val color = TerminalColor.Source(account.source)
        val zone = account.zoneOf(quota)
        val spans = mutableListOf(TerminalSpan(" "))
        if (first) {
            spans += TerminalPrimitives.sourceMark(color, zone, profile.glyphs)
            spans += TerminalSpan(" " + account.name.padEnd(sourceWidth - 2), color)
            spans += TerminalSpan(GAP + account.profile.orEmpty().padEnd(accountWidth), TerminalColor.Muted)
        } else {
            spans += TerminalSpan(" ".repeat(sourceWidth) + GAP + " ".repeat(accountWidth))
        }
        spans += TerminalSpan(GAP + displayQuotaLabel(quota.label, labels.pt).padEnd(quotaWidth) + GAP)
        val percent = quota.percent
        if (percent != null) {
            spans += TerminalPrimitives.quotaBar(percent, BAR_WIDTH, color, profile.glyphs)
            spans += TerminalSpan(" " + "$percent%".padStart(PERCENT_WIDTH), bold = true)
        } else {
            spans += TerminalSpan(TelegramBotMessages.quotaValue(quota, labels.pt).padEnd(usageWidth), bold = true)
        }
        spans += TerminalSpan(GAP)
        spans += if (zone != null) {
            val word = TerminalPrimitives.zoneWord(zone, labels, profile)
            word.copy(text = word.text.padEnd(stateWidth))
        } else {
            TerminalSpan(" ".repeat(stateWidth))
        }
        spans += resetSpans(quota.resetsAt, now, labels)
        return TerminalLine(spans).let { line -> TerminalLine(trimTrailingBlank(line.spans)) }
    }

    /**
     * Tempo × cota (#463, AI5), logo abaixo da cota com duração conhecida: `tempo  ███░ 97%  ✓ 33 pp
     * atrás do tempo · 4h51 de 5h`. A barra do tempo é muted, para não competir com a da cota.
     */
    private fun timeRow(quota: UsageSnapshotQuota, widths: IntArray, now: Instant, labels: TerminalLabels, profile: TerminalProfile): TerminalLine? {
        val percent = quota.percent ?: return null
        val time = TerminalQuotaTime.of(quota, now) ?: return null
        val (sourceWidth, accountWidth, quotaWidth) = widths.toList()
        val spans = mutableListOf(TerminalSpan(" " + " ".repeat(sourceWidth) + GAP + " ".repeat(accountWidth) + GAP))
        spans += TerminalSpan(labels.timeRow.padEnd(quotaWidth) + GAP, TerminalColor.Muted)
        spans += TerminalPrimitives.quotaBar(time.elapsedPercent, BAR_WIDTH, TerminalColor.Muted, profile.glyphs)
        spans += TerminalSpan(" " + "${time.elapsedPercent}%".padStart(PERCENT_WIDTH), TerminalColor.Muted)
        spans += TerminalSpan(GAP)
        spans += TerminalPrimitives.timeGap(time.gap(percent), labels, profile.glyphs)
        spans += TerminalSpan(" ${profile.glyphs.separator} " + labels.elapsedOf(time.elapsed, time.length), TerminalColor.Muted)
        return TerminalLine(spans)
    }

    private fun resetSpans(resetsAt: Instant?, now: Instant, labels: TerminalLabels): List<TerminalSpan> {
        val at = resetsAt ?: return emptyList()
        if (at <= now) {
            return listOf(TerminalSpan(GAP + labels.resetting, TerminalColor.Muted))
        }
        return listOf(
            TerminalSpan(GAP + labels.resetAt(at, now) + " BRT"),
            TerminalSpan(" · " + labels.inDuration(at - now), TerminalColor.Muted)
        )
    }

    /** Linha sem espaço sobrando no fim: copiar a saída não leva colunas vazias. */
    private fun trimTrailingBlank(spans: List<TerminalSpan>): List<TerminalSpan> {
        val result = spans.toMutableList()
        while (result.isNotEmpty() && result.last().text.isBlank()) {
            result.removeAt(result.lastIndex)
        }
        if (result.isNotEmpty() && result.last().color == null) {
            val last = result.last()
            result[result.lastIndex] = last.copy(text = last.text.trimEnd())
        }
        return result
    }
}
