package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.quotaWindowLengthMillis
import com.usagemonitor.presentation.ui.PaceDiagonalModels
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.paceLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * O gráfico alto da aba 3 (#463, AI1): `enter` numa janela da tabela troca a tabela por oito
 * linhas de colunas em blocos, com o eixo Y escrito (100/50/0%), o reinício marcado no fim do
 * eixo X e, embaixo, a frase da janela. Uma coluna é a última leitura até o fim dela.
 */
internal object TuiHistoryChartView {
    const val ROWS = 8
    private const val MAX_COLUMNS = 60
    private const val MIN_COLUMNS = 8

    /** `100% ┤`: quatro células do número, um espaço e a junção da moldura. */
    private const val AXIS_WIDTH = 6
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

    fun render(row: TuiHistoryRow, window: QuotaWindowSummary, now: Instant, size: TuiSize, labels: TuiHistoryLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): TuiTabView {
        val shortcuts = listOf(TuiShortcut("esc", labels.keyBack), TuiShortcut("↑↓", labels.keyWindow))
        val color = TerminalColor.Source(row.account.source)
        val glyphs = profile.glyphs
        val lengthMillis = quotaWindowLengthMillis(row.quota.label, row.quota.periodType)
        // Sem duração nominal (a intervalar do MiniMax, a mensal), o eixo começa na primeira leitura.
        val startsAt = lengthMillis?.let { length -> window.resetsAt - length.milliseconds }?.coerceAtMost(window.firstObservedAt) ?: window.firstObservedAt
        val columns = (size.bodyWidth - 1 - AXIS_WIDTH - 1).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val cellMillis = ((window.resetsAt - startsAt).inWholeMilliseconds / columns).coerceAtLeast(1L)
        val values = columnValues(row.points, window, startsAt, cellMillis, columns, now)

        val body = mutableListOf<TerminalLine>()
        body += terminalLine(
            TerminalSpan(TuiHistoryView.rowLabel(row, terminalLabels.pt), color, bold = true),
            TerminalSpan(" ${glyphs.separator} " + labels.chartTitle(TuiHistoryView.dateTime(startsAt, labels), endLabel(startsAt, window.resetsAt, labels), PaceDiagonalModels.durationLabel(cellMillis)), TerminalColor.Muted)
        )
        body += TerminalLine.EMPTY
        if (values.all { value -> value == null }) {
            body += terminalLine(TerminalSpan(labels.noReading, TerminalColor.Muted))
        } else {
            body += bars(values, color, glyphs)
            body += terminalLine(TerminalSpan(" ".repeat(AXIS_WIDTH - 1) + glyphs.box.bottomLeft + glyphs.box.horizontal.toString().repeat(columns), TerminalColor.Muted), TerminalSpan(glyphs.resetMark, TerminalColor.Muted))
            body += terminalLine(TerminalSpan(" ".repeat(AXIS_WIDTH) + xLabels(PaceDiagonalModels.axisLabels(startsAt, window.resetsAt), columns + 1), TerminalColor.Muted))
        }
        body += TerminalLine.EMPTY
        body += verdict(row, window, now, labels, terminalLabels, profile)
        body += terminalLine(TerminalSpan(facts(window, labels, glyphs), TerminalColor.Muted))
        return TuiTabView(body, shortcuts)
    }

    /**
     * O valor de cada coluna: a última leitura da janela até o fim da coluna, em %, pelo piso.
     * `null` antes da primeira leitura e, na janela aberta, depois de agora: não medido não vira zero.
     */
    fun columnValues(points: List<UsageHistoryPoint>, window: QuotaWindowSummary, startsAt: Instant, cellMillis: Long, columns: Int, now: Instant): List<Int?> {
        val inWindow = points
            .filter { point -> point.hasKnownResetAt && point.displayTotal > 0L && point.capturedAt >= window.firstObservedAt && point.capturedAt <= window.lastObservedAt }
            .sortedBy { point -> point.capturedAt }
        var next = 0
        var last: Int? = null
        return (0 until columns).map { index ->
            val columnStart = startsAt + (cellMillis * index).milliseconds
            val columnEnd = columnStart + cellMillis.milliseconds
            while (next < inWindow.size && inWindow[next].capturedAt < columnEnd) {
                val point = inWindow[next]
                last = (point.displayUsed * 100 / point.displayTotal).toInt().coerceIn(0, 100)
                next++
            }
            if (window.isOpen && columnStart > now) null else last
        }
    }

    /**
     * Oito linhas, de cima para baixo: 64 oitavos de altura, a coluna cheia em `█` e o topo em
     * `▁…▇`. Uso medido que não chega a um oitavo ainda ganha o degrau mais baixo; zero medido
     * fica no degrau mais baixo em muted, para não se confundir com "sem leitura".
     */
    private fun bars(values: List<Int?>, color: TerminalColor, glyphs: TerminalGlyphs): List<TerminalLine> {
        val levels = glyphs.sparkLevels
        return (ROWS - 1 downTo 0).map { row ->
            val axis = when (row) {
                ROWS - 1 -> "100%".padStart(4) + " " + glyphs.box.teeRight
                ROWS / 2 - 1 -> "50%".padStart(4) + " " + glyphs.box.teeRight
                0 -> "0%".padStart(4) + " " + glyphs.box.teeRight
                else -> " ".repeat(5) + glyphs.box.vertical
            }
            val spans = mutableListOf(TerminalSpan(axis, TerminalColor.Muted))
            values.forEach { value ->
                val eighths = if (value == null) 0 else (value * ROWS * 8 / 100 - row * 8).coerceIn(0, 8)
                spans += when {
                    value == null -> TerminalSpan(" ")
                    eighths >= 8 -> TerminalSpan(glyphs.barFull.toString(), color)
                    eighths > 0 -> TerminalSpan(levels[eighths - 1].toString(), color)
                    row == 0 && value > 0 -> TerminalSpan(levels[0].toString(), color)
                    row == 0 -> TerminalSpan(levels[0].toString(), TerminalColor.Muted)
                    else -> TerminalSpan(" ")
                }
            }
            TerminalLine(spans)
        }
    }

    /** Início, meio e reinício em [width] células: o primeiro no começo, o do meio centrado, o último no fim. */
    private fun xLabels(labels: List<String>, width: Int): String {
        val axis = CharArray(width) { ' ' }
        val (first, middle, last) = labels
        fun put(text: String, at: Int) {
            text.forEachIndexed { offset, char -> axis.getOrNull(at + offset)?.let { axis[at + offset] = char } }
        }
        put(first, 0)
        val middleAt = width / 2 - middle.length / 2
        if (middleAt > first.length && middleAt + middle.length < width - last.length) {
            put(middle, middleAt)
        }
        put(last, (width - last.length).coerceAtLeast(first.length + 1))
        return axis.concatToString().trimEnd()
    }

    /** O fim da janela: só a hora no mesmo dia do início, `24/10 01:00` noutro dia. */
    private fun endLabel(startsAt: Instant, resetsAt: Instant, labels: TuiHistoryLabels): String =
        if (startsAt.toLocalDateTime(SAO_PAULO).date == resetsAt.toLocalDateTime(SAO_PAULO).date) TelegramBotMessages.clock(resetsAt) else TuiHistoryView.dateTime(resetsAt, labels)

    /**
     * Aberta: `64% usada com 97% da janela já passada · ✓ 33 pp atrás do tempo` (sem duração
     * nominal, só o uso e quando reinicia). Fechada: `pico 96% · sobrou 4%`, ou o pico e como esgotou.
     */
    private fun verdict(
        row: TuiHistoryRow,
        window: QuotaWindowSummary,
        now: Instant,
        labels: TuiHistoryLabels,
        terminalLabels: TerminalLabels,
        profile: TerminalProfile
    ): TerminalLine {
        val separator = TerminalSpan(" ${profile.glyphs.separator} ", TerminalColor.Muted)
        if (!window.isOpen) {
            return TerminalLine(listOf(TerminalSpan(labels.peak(window.peakPercent), bold = true), separator) + TuiHistoryView.ending(window, now, labels, terminalLabels, profile))
        }
        val percent = row.quota.percent ?: window.peakPercent
        // O mesmo tempo do `status` e da linha de status (#463, AI5).
        val time = TerminalQuotaTime.of(row.quota, now)
        if (time == null) {
            return terminalLine(TerminalSpan(labels.used(percent), bold = true), separator, TerminalSpan(labels.open(terminalLabels.resetAt(window.resetsAt, now)), TerminalColor.Muted))
        }
        return terminalLine(
            TerminalSpan(labels.usedWithElapsed(percent, time.elapsedPercent), bold = true),
            separator,
            TerminalPrimitives.timeGap(time.gap(percent), terminalLabels, profile.glyphs)
        )
    }

    /** `ativa 15:09–19:07 · ritmo médio 13 %/h`. */
    private fun facts(window: QuotaWindowSummary, labels: TuiHistoryLabels, glyphs: TerminalGlyphs): String =
        labels.active(TuiHistoryView.activeSpan(window, labels)) + " ${glyphs.separator} " + labels.pace(paceLabel(window.averagePercentPerHour, labels.language))
}
