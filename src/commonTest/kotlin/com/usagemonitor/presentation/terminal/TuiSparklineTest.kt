package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.UsageHistoryPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TuiSparklineTest {

    private val start = Instant.parse("2026-10-20T17:30:00Z")
    private val end = start + 24.hours
    private val firstWindowEnds = start + 3.hours
    private val secondWindowEnds = start + 8.hours

    private fun point(at: Instant, used: Long, windowEnds: Instant) =
        UsageHistoryPoint(capturedAt = at, used = used, total = 100, rawUsed = 0, rawTotal = 0, periodEndAt = windowEnds)

    @Test
    fun `cada celula leva o maior uso lido nela, e celula sem leitura fica nula`() {
        val spark = TuiSparkline.build(
            listOf(point(start + 5.minutes, 10, firstWindowEnds), point(start + 20.minutes, 30, firstWindowEnds), point(start + 2.hours, 55, firstWindowEnds)),
            start, end, cellCount = 48
        )
        assertEquals(48, spark.cells.size)
        assertEquals(30, spark.cells[0])
        assertEquals(null, spark.cells[1])
        assertEquals(55, spark.cells[4])
        assertEquals(emptySet(), spark.resets)
    }

    @Test
    fun `a celula onde o fim da janela muda e a do reinicio`() {
        val spark = TuiSparkline.build(
            listOf(point(start + 2.hours, 80, firstWindowEnds), point(start + 3.hours + 10.minutes, 4, secondWindowEnds)),
            start, end, cellCount = 48
        )
        assertEquals(setOf(6), spark.resets)
    }

    @Test
    fun `leitura fora do recorte nao entra`() {
        val spark = TuiSparkline.build(listOf(point(start - 1.hours, 50, firstWindowEnds), point(end + 1.hours, 50, firstWindowEnds)), start, end, 48)
        assertEquals(List<Int?>(48) { null }, spark.cells)
    }

    @Test
    fun `degraus na cor da fonte, reinicio em muted e o degrau minimo para uso medido`() {
        val spark = TuiSpark(listOf(0, 50, 100, null, 7), resets = setOf(3), startsAt = start, cellMillis = 1)
        val color = TerminalColor.Source(com.usagemonitor.domain.entity.ApiSource.ANTHROPIC)
        val spans = TuiSparkline.render(spark, color, TerminalGlyphs.UNICODE)
        assertEquals("▁▄█┆▁", spans.joinToString("") { span -> span.text })
        assertEquals(TerminalColor.Muted, spans.first { span -> span.text == "┆" }.color)
        assertEquals(".=@|.", TuiSparkline.render(spark, color, TerminalGlyphs.ASCII).joinToString("") { span -> span.text })
    }
}
