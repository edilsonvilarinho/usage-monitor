package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionMetrics
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Telas 1.1 e 1.2 do protótipo. */
class TerminalStatusLineViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TerminalLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val ascii = TerminalProfile(TerminalPalette.NONE, unicode = false)

    private fun quota(label: String, percent: Int, resetsAt: Instant) =
        UsageSnapshotQuota(label, PeriodType.INTERVAL, UsageUnit.PERCENTAGE, percent.toLong(), 100, percent, resetsAt, "USD")

    private fun account(five: Int, seven: Int, fetchedAt: Instant = now - 1.minutes) = TerminalAccount(
        ApiSource.ANTHROPIC, "ANTHROPIC:default", "Anthropic", "pessoal", fetchedAt, listOf(70, 90),
        listOf(quota("Claude 5h", five, now + 47.minutes), quota("Claude 7d", seven, now + 4.days))
    )

    private val session = CliSessionMetrics(costMicros = 1_840_000, unpricedTurns = 0, throughput = OutputThroughput(31_000, 1_000_000))

    @Test
    fun `em dia, a linha so informa a hora do reinicio`() {
        val line = TerminalStatusLineView.claudeCode(account(62, 38), session, "Opus 5.5", now, pt, color)
        assertEquals("◑ pessoal · 5h 62% ███████▍░░░░ 15:10 · 7d 38% · sessão $1.84 · 31 tok/s · Opus 5.5", line.plainText)
    }

    @Test
    fun `acima do limiar escreve o estado e quanto falta`() {
        val line = TerminalStatusLineView.claudeCode(account(91, 74), session, null, now, pt, color)
        assertEquals("● pessoal · 5h 91% crítico ██████████▉░ reinicia em 47 min · 7d 74% atenção · sessão $1.84 · 31 tok/s", line.plainText)
    }

    @Test
    fun `custo parcial leva mais, e sem turno medido nao ha vazao`() {
        val partial = CliSessionMetrics(costMicros = 310_000, unpricedTurns = 2, throughput = null)
        val line = TerminalStatusLineView.claudeCode(null, partial, "MiniMax-M3", now, pt, color)
        assertEquals("sessão $0.31+ · MiniMax-M3", line.plainText)
    }

    @Test
    fun `sem conta reconhecida e sem sessao no indice sobra o modelo`() {
        assertEquals("Opus 5.5", TerminalStatusLineView.claudeCode(null, null, "Opus 5.5", now, pt, color).plainText)
        assertEquals("", TerminalStatusLineView.claudeCode(null, null, null, now, pt, color).plainText)
    }

    @Test
    fun `leitura velha aparece com a hora dela`() {
        val line = TerminalStatusLineView.claudeCode(account(62, 38, fetchedAt = now - 2.hours), null, null, now, pt, color)
        assertEquals("◑ pessoal · 5h 62% ███████▍░░░░ 15:10 · 7d 38% · leitura de 12:23", line.plainText)
    }

    @Test
    fun `sem Unicode nao ha anel e as barras e separadores viram ASCII`() {
        val line = TerminalStatusLineView.claudeCode(account(91, 38), null, null, now, pt, ascii)
        assertEquals("pessoal - 5h 91% crítico [#########-] reinicia em 47 min - 7d 38%", TerminalAnsi.render(line, ascii).replace("critico", "crítico").replace("CRITICO", "crítico"))
    }
}
