package com.usagemonitor.presentation.terminal

import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.formatActiveTime
import com.usagemonitor.presentation.ui.formatMicrosUsdShort
import com.usagemonitor.presentation.ui.formatThroughput
import kotlin.math.roundToInt

/**
 * Aba 2, Sessões (3.2): as sessões CLI do corte, com custo e vazão, e embaixo a composição de
 * tokens, os modelos da sessão (#445) e o contexto da selecionada. `enter` troca a lista pelos turnos dela. Custo do índice
 * (recalculado pela `ModelPricingTable`), com `+` quando algum modelo não tem tarifa.
 */
internal object TuiSessionsView {
    private const val MARKER_WIDTH = 2
    private const val DURATION_WIDTH = 8
    private const val TOKENS_WIDTH = 8
    private const val COST_WIDTH = 10
    private const val THROUGHPUT_WIDTH = 11
    private const val BAR_WIDTH = 40

    /** O bloco da sessão selecionada: linha em branco, título, composição em duas linhas e contexto. */
    private const val DETAIL_ROWS = 5

    /** Linhas de modelo no bloco da selecionada (#445); acima disso a última vira "+N modelos". */
    private const val MAX_MODEL_ROWS = 3

    private const val MODEL_NAME_MAX = 22
    /** Cabe "sem tarifa", a palavra mais longa da coluna. */
    private const val MODEL_SHARE_WIDTH = 10
    private const val MODEL_BAR_WIDTH = 16

    fun render(sessions: TuiSessions?, state: TuiSessionsState, size: TuiSize, labels: TuiSessionsLabels, profile: TerminalProfile): TuiTabView {
        val shortcuts = if (state.turnsOpen) {
            listOf(TuiShortcut("↑↓", labels.keyScroll), TuiShortcut("esc", labels.keyBack))
        } else {
            listOf(TuiShortcut("↑↓", labels.keySession), TuiShortcut("←→", labels.keyPeriod), TuiShortcut("enter", labels.keyTurns), TuiShortcut("e", labels.keyCsv))
        }
        val list = sessions?.sessions.orEmpty()
        val body = mutableListOf(periodLine(state.period, list, size.bodyWidth, labels))
        body += TerminalLine.EMPTY
        val failure = sessions?.failure
        when {
            failure != null -> body += terminalLine(TerminalSpan(profile.glyphs.warning + " ", TerminalColor.Warn), TerminalSpan(failure))
            list.isEmpty() -> body += terminalLine(TerminalSpan(labels.noSessions, TerminalColor.Muted))
            state.turnsOpen -> body += turns(list[state.selected.coerceIn(0, list.lastIndex)], sessions?.turns, state.turnsScroll, size, labels, profile)
            else -> body += table(list, state.selected.coerceIn(0, list.lastIndex), size, labels, profile)
        }
        return TuiTabView(body, shortcuts)
    }

    /** `hoje  7 dias  30 dias` com o corte ativo no fundo de seleção, e à direita o total. */
    private fun periodLine(active: TuiSessionPeriod, sessions: List<TuiSession>, width: Int, labels: TuiSessionsLabels): TerminalLine {
        val spans = mutableListOf<TerminalSpan>()
        TuiSessionPeriod.entries.forEachIndexed { index, period ->
            if (index > 0) {
                spans += TerminalSpan("  ")
            }
            spans += if (period == active) TerminalSpan(" ${labels.period(period)} ", bold = true, selected = true) else TerminalSpan(" ${labels.period(period)} ", TerminalColor.Muted)
        }
        val left = TerminalLine(spans)
        val priced = sessions.mapNotNull { session -> session.costMicros }
        val total = mutableListOf(TerminalSpan(labels.sessionCount(sessions.size), TerminalColor.Muted))
        if (priced.isNotEmpty()) {
            val incomplete = sessions.any { session -> session.costIncomplete }
            total += TerminalSpan(" · ", TerminalColor.Muted)
            total += TerminalSpan(formatMicrosUsdShort(priced.sum()) + if (incomplete) "+" else "", bold = true)
        }
        val right = TerminalLine(total)
        val gap = width - 1 - left.width - right.width
        return if (gap >= 2) left + terminalLine(TerminalSpan(" ".repeat(gap))) + right else left
    }

    /** As colunas que mudam com a largura: projeto e modelo dividem o que sobra das fixas. */
    private class Columns(width: Int) {
        val rest = (width - 1 - MARKER_WIDTH - DURATION_WIDTH - TOKENS_WIDTH - COST_WIDTH - THROUGHPUT_WIDTH).coerceAtLeast(16)
        val project = rest * 45 / 100
        val model = rest - project
    }

    private fun table(sessions: List<TuiSession>, selected: Int, size: TuiSize, labels: TuiSessionsLabels, profile: TerminalProfile): List<TerminalLine> {
        val columns = Columns(size.bodyWidth)
        val unpricedModels = sessions.filter { session -> session.costIncomplete }.flatMap(::unpricedModelsOf).distinct().sorted()
        val footnote = if (unpricedModels.isEmpty()) emptyList() else listOf(terminalLine(TerminalSpan("  " + labels.unpriced(unpricedModels), TerminalColor.Muted)))
        // Duas linhas antes da tabela (o corte e a em branco) e o cabeçalho.
        val room = size.bodyHeight - 3 - footnote.size
        val detailRows = DETAIL_ROWS + modelRowCount(sessions[selected])
        val withDetail = room - detailRows >= 3
        val visible = (if (withDetail) room - detailRows else room).coerceAtLeast(1)
        val start = TuiFrame.scrollStart(selected, sessions.size, visible)
        val header = " ".repeat(MARKER_WIDTH) + labels.headerProject.padEnd(columns.project) + labels.headerModel.padEnd(columns.model) +
            labels.headerDuration.padEnd(DURATION_WIDTH) + labels.headerTokens.padStart(TOKENS_WIDTH - 1) + " " +
            labels.headerCost.padStart(COST_WIDTH) + labels.headerThroughput.padStart(THROUGHPUT_WIDTH)
        val lines = mutableListOf(TuiFrame.header(header))
        sessions.subList(start, (start + visible).coerceAtMost(sessions.size)).forEachIndexed { offset, session ->
            lines += row(session, start + offset == selected, columns, size.bodyWidth, labels, profile)
        }
        lines += footnote
        if (withDetail) {
            lines += TerminalLine.EMPTY
            lines += detail(sessions[selected], size.bodyWidth, labels, profile)
        }
        return lines
    }

    private fun row(session: TuiSession, selected: Boolean, columns: Columns, width: Int, labels: TuiSessionsLabels, profile: TerminalProfile): TerminalLine {
        val project = (session.project ?: labels.noProject).take(columns.project - 1).padEnd(columns.project)
        // Com troca de modelo, o que mais pesou e `+N` (#445); o detalhe embaixo abre a lista.
        val switched = (session.modelMix.shares.size - 1).coerceAtLeast(0)
        val suffix = if (switched > 0) " +$switched" else ""
        val model = ((session.model ?: "—").take((columns.model - 1 - suffix.length).coerceAtLeast(1)) + suffix).padEnd(columns.model)
        val cost = session.costMicros?.let { micros -> formatMicrosUsdShort(micros) + if (session.costIncomplete) "+" else "" } ?: "—"
        val content = terminalLine(
            TerminalSpan(project),
            TerminalSpan(model, TerminalColor.Source(session.source)),
            TerminalSpan(formatActiveTime(session.durationMillis).padEnd(DURATION_WIDTH)),
            TerminalSpan(TuiText.tokens(session.totalTokens, labels.pt).padStart(TOKENS_WIDTH - 1) + " "),
            TerminalSpan(cost.padStart(COST_WIDTH), bold = true),
            TerminalSpan(formatThroughput(session.throughput).padStart(THROUGHPUT_WIDTH), TerminalColor.Muted)
        )
        return if (selected) {
            (terminalLine(TerminalSpan(profile.glyphs.selection + " ", TerminalColor.Gold)) + content).fit(width - 1).selected()
        } else {
            terminalLine(TerminalSpan(" ".repeat(MARKER_WIDTH))) + content
        }
    }

    /** `── usage-monitor · modelo · iniciada 13:02 BRT · 87 turnos ───`, a composição e o contexto. */
    private fun detail(session: TuiSession, width: Int, labels: TuiSessionsLabels, profile: TerminalProfile): List<TerminalLine> {
        val lines = mutableListOf(titleLine(session, width, labels, profile))
        lines += composition(session, labels, profile)
        lines += models(session, labels, profile)
        val window = session.contextWindow
        val context = session.contextTokens
        if (window != null && window > 0L && context != null) {
            val percent = (context * 100 / window).toInt().coerceIn(0, 100)
            lines += TerminalLine(
                listOf(TerminalSpan(labels.context.padEnd(LEGEND_INDENT), TerminalColor.Muted)) +
                    TerminalPrimitives.quotaBar(percent, BAR_WIDTH, TerminalColor.Source(session.source), profile.glyphs) +
                    listOf(TerminalSpan("  " + TuiText.tokens(context, labels.pt), bold = true), TerminalSpan(labels.contextOf(TuiText.tokens(window, labels.pt)), TerminalColor.Muted))
            )
        }
        return lines
    }

    private fun titleLine(session: TuiSession, width: Int, labels: TuiSessionsLabels, profile: TerminalProfile): TerminalLine {
        val dash = profile.glyphs.box.horizontal.toString()
        val separator = " ${profile.glyphs.separator} "
        val name = listOfNotNull(session.project ?: labels.noProject, session.model).joinToString(separator)
        val facts = separator + labels.startedAt(TelegramBotMessages.clock(session.startedAt)) + separator + labels.turnCount(session.turnCount) + " "
        val head = terminalLine(TerminalSpan(dash.repeat(2) + " ", TerminalColor.Border), TerminalSpan(name, bold = true), TerminalSpan(facts, TerminalColor.Muted))
        return head + terminalLine(TerminalSpan(dash.repeat((width - 1 - head.width).coerceAtLeast(0)), TerminalColor.Border))
    }

    /**
     * Composição de tokens: segmentos nas cores da série, pelo piso acumulado (a barra nunca
     * passa da largura), e a legenda em duas linhas com a porcentagem de cada série. No ASCII
     * a série é a inicial dela, porque a cor não pode ser o único sinal.
     */
    private fun composition(session: TuiSession, labels: TuiSessionsLabels, profile: TerminalProfile): List<TerminalLine> {
        val series = listOf(
            Series(labels.seriesInput, labels.markInput, session.inputTokens, TerminalColor.Input),
            Series(labels.seriesOutput, labels.markOutput, session.outputTokens, TerminalColor.Output),
            Series(labels.seriesCacheRead, labels.markCacheRead, session.cacheReadTokens, TerminalColor.CacheRead),
            Series(labels.seriesCacheWrite, labels.markCacheWrite, session.cacheWriteTokens, TerminalColor.CacheWrite)
        )
        val total = series.sumOf { item -> item.tokens }
        val glyphs = profile.glyphs
        val bar = mutableListOf<TerminalSpan>()
        var cumulative = 0L
        var drawn = 0
        for (item in series) {
            cumulative += item.tokens
            val end = if (total > 0L) (cumulative * BAR_WIDTH / total).toInt() else 0
            val cells = end - drawn
            if (cells > 0) {
                val cell = if (glyphs.legend.isEmpty()) item.mark else glyphs.barFull
                bar += TerminalSpan(cell.toString().repeat(cells), item.color)
            }
            drawn = end
        }
        if (drawn < BAR_WIDTH) {
            bar += TerminalSpan(glyphs.barEmpty.toString().repeat(BAR_WIDTH - drawn), TerminalColor.Dim)
        }
        fun legend(index: Int): List<TerminalSpan> {
            val item = series[index]
            val percent = if (total > 0L) (item.tokens * 100 / total).toInt() else 0
            val mark = glyphs.legend.ifEmpty { item.mark.toString() }
            return listOf(TerminalSpan(mark, item.color), TerminalSpan(" ${item.name} $percent%  "))
        }
        val first = TerminalLine(listOf(TerminalSpan(labels.composition.padEnd(LEGEND_INDENT), TerminalColor.Muted)) + bar + TerminalSpan("  ") + legend(0) + legend(1))
        val second = TerminalLine(listOf(TerminalSpan(" ".repeat(LEGEND_INDENT + BAR_WIDTH + 2))) + legend(2) + legend(3))
        return listOf(first, second)
    }

    /**
     * Os modelos sem tarifa da sessão, pelo corte por modelo (#445): numa sessão que trocou de
     * modelo, o da linha pode ser justamente o que tem tarifa. Sem o corte lido, o da linha.
     */
    private fun unpricedModelsOf(session: TuiSession): List<String> {
        val buckets = session.modelMix.shares.map { share -> share.bucket }
        if (buckets.isEmpty()) {
            return listOfNotNull(session.model)
        }
        return buckets.filter { bucket -> !bucket.isCostComplete }.mapNotNull { bucket -> bucket.label }
    }

    private fun modelRowCount(session: TuiSession): Int = session.modelMix.shares.size.coerceAtMost(MAX_MODEL_ROWS)

    /**
     * Os modelos da sessão (#445), um por linha: nome na cor da fonte, a fatia, a barra dela e o
     * cache lido e gravado de cada um. A legenda da primeira linha diz "modelos"; a da segunda,
     * a base da fatia (custo ou tokens). Sem o corte lido, nada. Com mais de [MAX_MODEL_ROWS]
     * modelos a última linha vira "+N modelos".
     */
    private fun models(session: TuiSession, labels: TuiSessionsLabels, profile: TerminalProfile): List<TerminalLine> {
        val mix = session.modelMix
        if (mix.isEmpty) {
            return emptyList()
        }
        val shown = if (mix.shares.size > MAX_MODEL_ROWS) mix.shares.take(MAX_MODEL_ROWS - 1) else mix.shares
        val nameWidth = shown.maxOf { share -> (share.bucket.label ?: labels.unknownModel).length }.coerceAtMost(MODEL_NAME_MAX) + 2
        val separator = profile.glyphs.separator
        val lines = shown.mapIndexed { index, share ->
            val legend = when (index) {
                0 -> labels.models
                1 -> labels.shareBasis(mix.basis)
                else -> ""
            }
            val name = (share.bucket.label ?: labels.unknownModel).take(nameWidth - 2).padEnd(nameWidth)
            val fraction = share.share
            val shareText = fraction?.let { value -> "${(value * 100.0).roundToInt()}%" } ?: labels.unpricedShare
            val bar = if (fraction == null) {
                listOf(TerminalSpan(" ".repeat(MODEL_BAR_WIDTH)))
            } else {
                TerminalPrimitives.quotaBar((fraction * 100.0).roundToInt(), MODEL_BAR_WIDTH, TerminalColor.Source(session.source), profile.glyphs)
            }
            val bucket = share.bucket
            val cache = labels.modelCache(TuiText.tokens(bucket.cacheReadTokens, labels.pt), TuiText.tokens(bucket.cacheWriteTokens, labels.pt), separator)
            TerminalLine(
                listOf(
                    TerminalSpan(legend.padEnd(LEGEND_INDENT), TerminalColor.Muted),
                    TerminalSpan(name, TerminalColor.Source(session.source)),
                    TerminalSpan(shareText.padStart(MODEL_SHARE_WIDTH) + "  ", bold = true)
                ) + bar + listOf(TerminalSpan("  $cache", TerminalColor.Muted))
            )
        }
        if (shown.size == mix.shares.size) {
            return lines
        }
        val legend = if (lines.size == 1) labels.shareBasis(mix.basis) else ""
        return lines + terminalLine(
            TerminalSpan(legend.padEnd(LEGEND_INDENT), TerminalColor.Muted),
            TerminalSpan(labels.moreModels(mix.shares.size - shown.size), TerminalColor.Muted)
        )
    }

    /** Os turnos da sessão aberta, do mais recente ao mais antigo. */
    private fun turns(session: TuiSession, turns: List<TuiTurn>?, scroll: Int, size: TuiSize, labels: TuiSessionsLabels, profile: TerminalProfile): List<TerminalLine> {
        val lines = mutableListOf(titleLine(session, size.bodyWidth, labels, profile), TerminalLine.EMPTY)
        if (turns.isNullOrEmpty()) {
            lines += terminalLine(TerminalSpan(labels.noTurns, TerminalColor.Muted))
            return lines
        }
        val modelWidth = (size.bodyWidth - 1 - 7 - 3 * TOKENS_WIDTH - COST_WIDTH - 4).coerceIn(10, 28)
        lines += TuiFrame.header(
            labels.headerTime.padEnd(7) + labels.headerModel.padEnd(modelWidth) + labels.headerInput.padStart(TOKENS_WIDTH) + " " +
                labels.headerOutput.padStart(TOKENS_WIDTH) + " " + labels.headerCacheRead.padStart(TOKENS_WIDTH + 2) + labels.headerCost.padStart(COST_WIDTH)
        )
        val visible = (size.bodyHeight - 2 - lines.size).coerceAtLeast(1)
        val start = scroll.coerceIn(0, (turns.size - visible).coerceAtLeast(0))
        for (turn in turns.subList(start, (start + visible).coerceAtMost(turns.size))) {
            lines += terminalLine(
                TerminalSpan(TelegramBotMessages.clock(turn.at).padEnd(7)),
                TerminalSpan((turn.model ?: "—").take(modelWidth - 1).padEnd(modelWidth), TerminalColor.Source(session.source)),
                TerminalSpan(TuiText.tokens(turn.inputTokens, labels.pt).padStart(TOKENS_WIDTH) + " "),
                TerminalSpan(TuiText.tokens(turn.outputTokens, labels.pt).padStart(TOKENS_WIDTH) + " "),
                TerminalSpan(TuiText.tokens(turn.cacheReadTokens, labels.pt).padStart(TOKENS_WIDTH + 2)),
                TerminalSpan((turn.costMicros?.let(::formatMicrosUsdShort) ?: "—").padStart(COST_WIDTH), bold = true)
            )
        }
        return lines
    }

    private const val LEGEND_INDENT = 12

    /** Uma série da composição: o nome, a letra que a marca sem cor, os tokens e a cor. */
    private class Series(val name: String, val mark: Char, val tokens: Long, val color: TerminalColor)
}
