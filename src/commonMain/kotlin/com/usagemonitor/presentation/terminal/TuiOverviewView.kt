package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.components.formatCents
import com.usagemonitor.presentation.ui.displayQuotaLabel
import com.usagemonitor.presentation.ui.formatActiveTime
import com.usagemonitor.presentation.ui.formatMicrosUsdShort
import com.usagemonitor.presentation.ui.formatThroughput
import kotlin.time.Instant

/** A seleção de uma lista com detalhe: o item e, empilhado, se o detalhe está aberto. */
data class TuiListState(val selected: Int = 0, val detailOpen: Boolean = false) {
    fun clamped(count: Int): TuiListState = copy(selected = selected.coerceIn(0, (count - 1).coerceAtLeast(0)))

    /** `↑ ↓` movem a seleção; empilhado, `enter` abre o detalhe e `esc` volta à lista. */
    fun reduce(key: TuiKey, count: Int, stacked: Boolean): TuiListState? =
        when (key) {
            TuiKey.Up -> copy(selected = (selected - 1).coerceAtLeast(0))
            TuiKey.Down -> copy(selected = (selected + 1).coerceAtMost((count - 1).coerceAtLeast(0)))
            TuiKey.Enter -> if (stacked && count > 0) copy(detailOpen = true) else null
            TuiKey.Escape -> if (detailOpen) copy(detailOpen = false) else null
            else -> null
        }
}

/**
 * Aba 1, Visão geral (3.1): as contas à esquerda e, à direita, o detalhe da escolhida, com
 * as cotas, a sessão ativa e as últimas 24 h. Leitura que falhou ganha a faixa de aviso (6.2)
 * no alto do detalhe, e os números ficam os da leitura anterior.
 */
internal object TuiOverviewView {
    private const val DETAIL_BAR_WIDTH = 40
    private const val MIN_BAR_WIDTH = 10
    private const val LABEL_WIDTH_CAP = 16
    private const val PERCENT_WIDTH = 4
    private const val GAP = "  "

    fun render(
        data: TuiData,
        state: TuiListState,
        size: TuiSize,
        labels: TuiOverviewLabels,
        terminalLabels: TerminalLabels,
        profile: TerminalProfile
    ): TuiTabView {
        val glyphs = profile.glyphs
        val shortcuts = buildList {
            add(TuiShortcut("↑↓", labels.keySource))
            // Lado a lado, `enter` abre o histórico da conta; empilhado, o detalhe dela.
            add(if (size.stacked && state.detailOpen) TuiShortcut("esc", TuiLabels(labels.language).keyBackLabel) else TuiShortcut("enter", labels.keyOpenAccount))
        }
        if (data.accounts.isEmpty()) {
            return TuiTabView(listOf(terminalLine(TerminalSpan(labels.noAccounts, TerminalColor.Muted))), shortcuts)
        }
        val selection = state.clamped(data.accounts.size)
        val left = list(data.accounts, selection.selected, size, labels, terminalLabels, glyphs)
        val right = detail(data.accounts[selection.selected], data.now, size.detailWidth, size.bodyHeight, labels, terminalLabels, profile)
        return TuiTabView(TuiFrame.listDetail(left, right, size, selection.detailOpen, glyphs.box), shortcuts)
    }

    private fun list(
        accounts: List<TuiAccount>,
        selected: Int,
        size: TuiSize,
        labels: TuiOverviewLabels,
        terminalLabels: TerminalLabels,
        glyphs: TerminalGlyphs
    ): List<TerminalLine> {
        val visible = (size.bodyHeight - 2).coerceAtLeast(1)
        val start = TuiFrame.scrollStart(selected, accounts.size, visible)
        val rows = accounts.subList(start, (start + visible).coerceAtMost(accounts.size)).mapIndexed { offset, item ->
            val account = item.account
            val value = listValue(account, terminalLabels)
            // `▸ `, o nome, um espaço, o valor alinhado à direita e a célula antes do divisor.
            val nameWidth = (size.listWidth - 2 - 1 - value.length - 1).coerceAtLeast(1)
            val name = displayName(account).take(nameWidth).padEnd(nameWidth)
            val content = terminalLine(TerminalSpan(name, TerminalColor.Source(account.source)), TerminalSpan(" $value"))
            TuiFrame.listItem(content, selected = start + offset == selected, width = size.listWidth, glyphs = glyphs)
        }
        return listOf(TuiFrame.header(labels.headerSources), TerminalLine.EMPTY) + rows
    }

    /** O nome na lista: o apelido da conta, ou o da fonte quando ela tem uma conta só. */
    private fun displayName(account: TerminalAccount): String = account.profile?.takeIf { label -> label.isNotBlank() } ?: account.name

    /**
     * O maior percentual da conta, ou o valor da primeira cota sem percentual, no formato curto
     * do card do app (`$7.82`): a coluna tem 21 células.
     */
    private fun listValue(account: TerminalAccount, labels: TerminalLabels): String {
        val highest = account.quotas.mapNotNull { quota -> quota.percent }.maxOrNull()
        if (highest != null) {
            return "$highest%".padStart(PERCENT_WIDTH)
        }
        val quota = account.quotas.firstOrNull() ?: return "–"
        if (quota.unit == UsageUnit.CURRENCY_USD) {
            return formatCents(quota.total - quota.used, quota.currencyCode)
        }
        return TelegramBotMessages.quotaValue(quota, labels.pt)
    }

    private fun detail(
        item: TuiAccount,
        now: Instant,
        width: Int,
        height: Int,
        labels: TuiOverviewLabels,
        terminalLabels: TerminalLabels,
        profile: TerminalProfile
    ): List<TerminalLine> {
        val account = item.account
        val color = TerminalColor.Source(account.source)
        val lines = mutableListOf(header(account, width, color))
        lines += TerminalLine.EMPTY
        item.failure?.let { failure ->
            lines += failureBanner(failure, account.fetchedAt, width, labels, profile.glyphs)
            lines += TerminalLine.EMPTY
        }
        val extras = extraLines(item, width, labels, terminalLabels, profile)
        val quotaBlocks = account.quotas.map { quota -> quotaLines(account, quota, now, width, terminalLabels, profile) }
        val spaced = quotaBlocks.flatMapIndexed { index, block -> if (index > 0) listOf(TerminalLine.EMPTY) + block else block }
        val tight = quotaBlocks.flatten()
        val room = height - lines.size - (if (extras.isEmpty()) 0 else extras.size + 1)
        lines += if (spaced.size <= room) spaced else tight
        if (extras.isNotEmpty()) {
            lines += TerminalLine.EMPTY
            lines += extras
        }
        return lines
    }

    /** `Claude Team · trabalho` em negrito na cor da fonte, e o plano à direita, em muted. */
    private fun header(account: TerminalAccount, width: Int, color: TerminalColor): TerminalLine {
        val left = mutableListOf(TerminalSpan(account.name, color, bold = true))
        account.profile?.takeIf { label -> label.isNotBlank() }?.let { label -> left += TerminalSpan(" · $label", TerminalColor.Muted) }
        val line = TerminalLine(left)
        val plan = account.plan?.takeIf { text -> text.isNotBlank() } ?: return line
        // Uma célula de folga antes da borda, como o resto do detalhe.
        val gap = width - 1 - line.width - plan.length
        return if (gap >= 2) line + terminalLine(TerminalSpan(" ".repeat(gap) + plan, TerminalColor.Muted)) else line
    }

    /** A faixa de aviso (6.2): `▲` e o que houve; embaixo, em muted, o que está na tela e quando tenta de novo. */
    private fun failureBanner(failure: TuiReadFailure, shownFrom: Instant?, width: Int, labels: TuiOverviewLabels, glyphs: TerminalGlyphs): List<TerminalLine> {
        val headline = labels.failed(failure.attemptedAt?.let(TelegramBotMessages::clock), failure.reason)
        val detail = labels.showing(shownFrom?.let(TelegramBotMessages::clock), failure.retryAt?.let(TelegramBotMessages::clock))
        val first = TuiText.wrap(headline, width - 2).mapIndexed { index, text ->
            val mark = if (index == 0) TerminalSpan(glyphs.warning + " ", TerminalColor.Warn) else TerminalSpan("  ")
            terminalLine(mark, TerminalSpan(text))
        }
        val second = TuiText.wrap(detail, width - 2).map { text -> terminalLine(TerminalSpan("  $text", TerminalColor.Muted)) }
        return first + second
    }

    private fun quotaLines(
        account: TerminalAccount,
        quota: UsageSnapshotQuota,
        now: Instant,
        width: Int,
        labels: TerminalLabels,
        profile: TerminalProfile
    ): List<TerminalLine> {
        val labelWidth = account.quotas.maxOf { item -> displayQuotaLabel(item.label, labels.pt).length }.coerceAtMost(LABEL_WIDTH_CAP)
        val label = TerminalSpan(displayQuotaLabel(quota.label, labels.pt).take(labelWidth).padEnd(labelWidth) + GAP)
        val percent = quota.percent
        if (percent == null) {
            return listOf(terminalLine(label, TerminalSpan(TelegramBotMessages.quotaValue(quota, labels.pt), bold = true)))
        }
        val zone = account.zoneOf(quota)
        val barWidth = (width - labelWidth - GAP.length * 3 - PERCENT_WIDTH - labels.zoneColumnWidth).coerceIn(MIN_BAR_WIDTH, DETAIL_BAR_WIDTH)
        val spans = mutableListOf(label)
        spans += TerminalPrimitives.quotaBar(percent, barWidth, TerminalColor.Source(account.source), profile.glyphs)
        spans += TerminalSpan(GAP + "$percent%".padStart(PERCENT_WIDTH), bold = true)
        if (zone != null) {
            spans += TerminalSpan(GAP)
            spans += TerminalPrimitives.zoneWord(zone, labels, profile)
        }
        val lines = mutableListOf(TerminalLine(spans))
        val resetsAt = quota.resetsAt
        if (resetsAt != null) {
            val text = if (resetsAt <= now) labels.resetting else TuiOverviewLabels(labels.language).resets(labels.resetAt(resetsAt, now), TerminalLabels.countdown(resetsAt - now))
            lines += terminalLine(TerminalSpan(" ".repeat(labelWidth + GAP.length) + text, TerminalColor.Muted))
        }
        return lines
    }

    /** A sessão ativa e as últimas 24 h, com os rótulos alinhados numa coluna. */
    private fun extraLines(item: TuiAccount, width: Int, labels: TuiOverviewLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): List<TerminalLine> {
        val labelWidth = maxOf(labels.activeSession.length, labels.last24h.length) + GAP.length
        val lines = mutableListOf<TerminalLine>()
        item.activeSession?.let { session ->
            val head = TerminalSpan(labels.activeSession.padEnd(labelWidth), TerminalColor.Muted, bold = true)
            lines += if (session == TuiActiveSession.NONE) {
                terminalLine(head, TerminalSpan(labels.noActiveSession, TerminalColor.Muted))
            } else {
                terminalLine(head) + sessionSummary(session, width - labelWidth - 1, profile.glyphs)
            }
        }
        val head = TerminalSpan(labels.last24h.padEnd(labelWidth), TerminalColor.Muted, bold = true)
        val spark = item.last24h
        lines += if (spark == null || spark.cells.all { cell -> cell == null }) {
            terminalLine(head, TerminalSpan(labels.noHistory, TerminalColor.Muted))
        } else {
            val room = (width - labelWidth).coerceAtLeast(1)
            val shown = if (spark.cells.size > room) spark.copy(cells = spark.cells.takeLast(room), resets = spark.resets.map { index -> index - (spark.cells.size - room) }.filter { index -> index >= 0 }.toSet()) else spark
            TerminalLine(listOf(head) + TuiSparkline.render(shown, TerminalColor.Source(item.account.source), profile.glyphs))
        }
        return lines
    }

    /**
     * `usage-monitor · Opus 5.5 · 1h12 · $9.40 · 38 tok/s`, sem as partes que não foram
     * medidas. Sem espaço, saem primeiro a vazão e depois o modelo: o custo e o tempo ficam.
     */
    private fun sessionSummary(session: TuiActiveSession, width: Int, glyphs: TerminalGlyphs): TerminalLine {
        val project = session.project?.let { name -> listOf(TerminalSpan(name)) }
        val model = session.model?.let { name -> listOf(TerminalSpan(name)) }
        val time = listOf(TerminalSpan(formatActiveTime(session.durationMillis)))
        val cost = session.costMicros?.let { micros -> listOf(TerminalSpan(formatMicrosUsdShort(micros) + if (session.costIncomplete) "+" else "", bold = true)) }
        val throughput = session.throughput?.let { value -> listOf(TerminalSpan(formatThroughput(value))) }
        val candidates = listOf(
            listOfNotNull(project, model, time, cost, throughput),
            listOfNotNull(project, model, time, cost),
            listOfNotNull(project, time, cost)
        )
        val separator = TerminalSpan(" ${glyphs.separator} ", TerminalColor.Muted)
        val lines = candidates.map { parts -> TerminalLine(parts.flatMapIndexed { index, part -> if (index > 0) listOf(separator) + part else part }) }
        return lines.firstOrNull { line -> line.width <= width } ?: lines.last()
    }
}
