package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TerminalWaitViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TerminalLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)

    private fun accounts(percent: Int, resetsAt: Instant? = now + 47.minutes) = listOf(
        TerminalAccount(
            ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", "trabalho", now, listOf(70, 90),
            listOf(UsageSnapshotQuota("Claude 5h", PeriodType.INTERVAL, UsageUnit.PERCENTAGE, percent.toLong(), 100, percent, resetsAt, "USD"))
        ),
        TerminalAccount(
            ApiSource.DEEPSEEK, "DEEPSEEK", "DeepSeek", null, now, listOf(70, 90),
            listOf(UsageSnapshotQuota("Saldo", PeriodType.REPORTED, UsageUnit.CURRENCY_USD, 0, 782, null, null, "USD"))
        )
    )

    private fun evaluate(percent: Int, id: String = "anthropic:trabalho:claude-5h", resetsAt: Instant? = now + 47.minutes) =
        TerminalWaitView.evaluate(accounts(percent, resetsAt), id, below = 50, now = now, labels = pt, profile = color)

    @Test
    fun `acima do limite continua esperando e diz quando reinicia`() {
        val step = assertIs<TerminalWaitStep.Waiting>(evaluate(91))
        assertEquals(91, step.percent)
        assertEquals("aguardando Claude 5h (Anthropic · trabalho) cair abaixo de 50% (agora 91%) · reinicia 15:10 BRT, em 47 min…", step.line.plainText)
    }

    @Test
    fun `janela vencida sem coleta nova continua esperando, sem citar reinicio passado`() {
        val step = assertIs<TerminalWaitStep.Waiting>(evaluate(91, resetsAt = now - 1.minutes))
        assertEquals("aguardando Claude 5h (Anthropic · trabalho) cair abaixo de 50% (agora 91%)…", step.line.plainText)
    }

    @Test
    fun `abaixo do limite termina com 0`() {
        val step = assertIs<TerminalWaitStep.Finished>(evaluate(3))
        assertEquals(TerminalWaitView.EXIT_REACHED, step.exitCode)
        assertEquals("✓ Claude 5h (Anthropic · trabalho) está em 3%, abaixo de 50%", step.lines.single().plainText)
    }

    @Test
    fun `id e comparado sem caixa`() {
        assertIs<TerminalWaitStep.Finished>(evaluate(3, id = " Anthropic:Trabalho:Claude-5h "))
    }

    @Test
    fun `id desconhecido sai com 2 e lista os ids que existem`() {
        val step = assertIs<TerminalWaitStep.Finished>(evaluate(91, id = "anthropic:trabalho:5h"))
        assertEquals(TerminalWaitView.EXIT_UNKNOWN_QUOTA, step.exitCode)
        assertTrue(step.lines.single().plainText.endsWith("Cotas: anthropic:trabalho:claude-5h, deepseek:saldo"))
    }

    @Test
    fun `saldo nao tem percentual para esperar`() {
        val step = assertIs<TerminalWaitStep.Finished>(evaluate(91, id = "deepseek:saldo"))
        assertEquals(TerminalWaitView.EXIT_UNKNOWN_QUOTA, step.exitCode)
    }

    @Test
    fun `sem leitura sai com 3`() {
        val step = assertIs<TerminalWaitStep.Finished>(TerminalWaitView.evaluate(emptyList(), "x", 50, now, pt, color))
        assertEquals(TerminalWaitView.EXIT_NO_READING, step.exitCode)
    }
}
