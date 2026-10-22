package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Os números da tela 2.2 do protótipo: quarta, 21/10, 14:23 BRT, limiares 70 e 90. */
class TerminalStatusViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val collected = now - 38.seconds
    private val pt = TerminalLabels(AppLanguage.PT)
    private val en = TerminalLabels(AppLanguage.EN)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val ascii = TerminalProfile(TerminalPalette.NONE, unicode = false)

    private fun percent(label: String, value: Int, resetsAt: Instant?, type: PeriodType = PeriodType.INTERVAL) =
        UsageSnapshotQuota(label, type, UsageUnit.PERCENTAGE, value.toLong(), 100, value, resetsAt, "USD")

    private val accounts = listOf(
        TerminalAccount(
            ApiSource.ANTHROPIC, "ANTHROPIC:default", "Anthropic", "pessoal", collected, listOf(70, 90),
            listOf(percent("Claude 5h", 62, now + 2.hours + 17.minutes), percent("Claude 7d", 38, Instant.parse("2026-10-26T12:00:00Z"), PeriodType.WEEKLY))
        ),
        TerminalAccount(
            ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", "trabalho", collected, listOf(70, 90),
            listOf(percent("Claude 5h", 91, now + 47.minutes))
        ),
        TerminalAccount(
            ApiSource.DEEPSEEK, "DEEPSEEK", "DeepSeek", null, collected, listOf(70, 90),
            listOf(UsageSnapshotQuota("Saldo", PeriodType.REPORTED, UsageUnit.CURRENCY_USD, 0, 782, null, null, "USD"))
        )
    )

    private fun text(output: TerminalOutput) = output.lines.map { line -> line.plainText }

    @Test
    fun `tabela com uma linha por cota, na ordem recebida, e rodape de leitura`() {
        val output = TerminalStatusView.render(accounts, now, collectorRunning = true, maxAge = null, labels = pt, profile = color)
        assertEquals(TerminalStatusView.EXIT_OK, output.exitCode)
        assertEquals(
            listOf(
                " FONTE        CONTA     COTA       USO                        ESTADO   REINICIA",
                " ● Anthropic  pessoal   Claude 5h  ████████████▍░░░░░░░  62%  em dia   16:40 BRT · em 2h17",
                "                        Claude 7d  ███████▌░░░░░░░░░░░░  38%  em dia   seg 09:00 BRT · em 4d18h",
                " ● Anthropic  trabalho  Claude 5h  ██████████████████▏░  91%  crítico  15:10 BRT · em 47 min",
                " ● DeepSeek             Saldo      saldo US$ 7,82",
                "",
                " lido há 38 s pelo app aberto · 4 cotas: 1 crítica, 2 em dia"
            ),
            text(output)
        )
    }

    @Test
    fun `sem Unicode, barras ASCII, marca so na linha critica e texto sem acento no render`() {
        val output = TerminalStatusView.render(accounts, now, collectorRunning = true, maxAge = null, labels = pt, profile = ascii)
        val rendered = TerminalAnsi.render(output.lines, ascii).lines()
        assertEquals("   Anthropic  pessoal   Claude 5h  [###########-------]  62%  em dia   16:40 BRT - em 2h17", rendered[1])
        assertEquals(" * Anthropic  trabalho  Claude 5h  [################--]  91%  CRITICO  15:10 BRT - em 47 min", rendered[3])
    }

    @Test
    fun `em ingles os cabecalhos e as palavras mudam`() {
        val output = TerminalStatusView.render(accounts, now, collectorRunning = true, maxAge = null, labels = en, profile = color)
        assertTrue(text(output).first().startsWith(" SOURCE"))
        assertTrue(text(output)[3].contains("critical"))
        assertEquals(" read 38 s ago by the open app · 4 quotas: 1 critical, 2 on track", text(output).last())
    }

    @Test
    fun `nada coletando avisa a idade da leitura e nao imprime o rodape`() {
        val old = accounts.map { account -> account.copy(fetchedAt = now - 18.minutes) }
        val output = TerminalStatusView.render(old, now, collectorRunning = false, maxAge = null, labels = pt, profile = color)
        assertEquals(TerminalStatusView.EXIT_OK, output.exitCode)
        assertEquals("▲ Nada está coletando. Mostrando a última leitura, de 14:05 BRT (há 18 min).", text(output)[0])
        assertTrue(text(output).none { line -> line.contains("pelo app aberto") })
    }

    @Test
    fun `leitura mais velha que o max-age sai com 3`() {
        val old = accounts.map { account -> account.copy(fetchedAt = now - 18.minutes) }
        val output = TerminalStatusView.render(old, now, collectorRunning = false, maxAge = 10.minutes, labels = pt, profile = color)
        assertEquals(TerminalStatusView.EXIT_NO_READING, output.exitCode)
        assertEquals(listOf("sem leitura  a última tem 18 min, mais que os 10 min pedidos"), text(output))
    }

    @Test
    fun `max-age abaixo de um minuto fala em segundos`() {
        val output = TerminalStatusView.render(accounts, now, collectorRunning = true, maxAge = 10.seconds, labels = en, profile = color)
        assertEquals(listOf("no reading  the last one is 38 s old, more than the 10 s asked for"), text(output))
    }

    @Test
    fun `sem leitura nenhuma sai com 3 e diz o que fazer`() {
        val output = TerminalStatusView.render(emptyList(), now, collectorRunning = false, maxAge = null, labels = pt, profile = color)
        assertEquals(TerminalStatusView.EXIT_NO_READING, output.exitCode)
        assertEquals(listOf("▲ Ainda não há leitura. Abra o Usage Monitor e espere a primeira coleta."), text(output))
    }

    @Test
    fun `reinicio a mais de 6 dias mostra a data, e nao um dia da semana ambiguo`() {
        val monthly = listOf(accounts[0].copy(quotas = listOf(percent("Go mensal", 25, Instant.parse("2026-11-05T18:47:00Z"), PeriodType.MONTHLY))))
        val output = TerminalStatusView.render(monthly, now, collectorRunning = true, maxAge = null, labels = pt, profile = color)
        assertTrue(text(output)[1].endsWith("05/11 15:47 BRT · em 15d01h"))
        val english = TerminalStatusView.render(monthly, now, collectorRunning = true, maxAge = null, labels = en, profile = color)
        assertTrue(text(english)[1].endsWith("11/05 15:47 BRT · in 15d01h"))
    }

    @Test
    fun `janela que ja reiniciou diz reiniciando em vez de uma hora passada`() {
        val expired = listOf(accounts[1].copy(quotas = listOf(percent("Claude 5h", 91, now - 1.minutes))))
        val output = TerminalStatusView.render(expired, now, collectorRunning = true, maxAge = null, labels = pt, profile = color)
        assertTrue(text(output)[1].endsWith("crítico  reiniciando"))
    }
}
