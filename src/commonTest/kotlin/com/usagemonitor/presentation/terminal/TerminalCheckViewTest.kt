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
import kotlin.time.Instant

class TerminalCheckViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TerminalLabels(AppLanguage.PT)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)

    private fun quota(label: String, value: Int) =
        UsageSnapshotQuota(label, PeriodType.INTERVAL, UsageUnit.PERCENTAGE, value.toLong(), 100, value, now + 1.hours, "USD")

    private fun account(profile: String?, vararg quotas: UsageSnapshotQuota, source: ApiSource = ApiSource.ANTHROPIC, name: String = "Anthropic") =
        TerminalAccount(source, "${source.name}:$profile", name, profile, now - 1.minutes, listOf(70, 90), quotas.toList())

    private fun check(accounts: List<TerminalAccount>, max: Int? = null, quiet: Boolean = false) =
        TerminalCheckView.render(accounts, now, max, maxAge = null, quiet = quiet, labels = pt, profile = color)

    private fun text(output: TerminalOutput) = output.lines.map { line -> line.plainText }

    @Test
    fun `sem --max decide pela zona dos limiares da conta`() {
        assertEquals(0, check(listOf(account("pessoal", quota("Claude 5h", 62)))).exitCode)
        assertEquals(1, check(listOf(account("pessoal", quota("Claude 5h", 74)))).exitCode)
        assertEquals(2, check(listOf(account("trabalho", quota("Claude 5h", 91)), account("pessoal", quota("Claude 5h", 74)))).exitCode)
    }

    @Test
    fun `imprime as cotas que pesaram, critico primeiro`() {
        val output = check(listOf(account("pessoal", quota("Claude 7d", 74)), account("trabalho", quota("Claude 5h", 91))))
        assertEquals(listOf("crítico  Anthropic · trabalho · Claude 5h 91%", "atenção  Anthropic · pessoal · Claude 7d 74%"), text(output))
    }

    @Test
    fun `com --max so ha passou ou nao passou, e a linha cita o limite`() {
        val accounts = listOf(account("trabalho", quota("Claude 5h", 91), quota("Claude 7d", 74)))
        val output = check(accounts, max = 90)
        assertEquals(2, output.exitCode)
        assertEquals(listOf("crítico  Anthropic · trabalho · Claude 5h 91% (limite 90%)"), text(output))
        assertEquals(0, check(accounts, max = 95).exitCode)
    }

    @Test
    fun `tudo em dia diz qual e o maior uso`() {
        val output = check(listOf(account("pessoal", quota("Claude 5h", 62), quota("Claude 7d", 38))))
        assertEquals(listOf("em dia  maior uso: Anthropic · pessoal · Claude 5h 62%"), text(output))
    }

    @Test
    fun `--quiet nao imprime nada e mantem o codigo`() {
        val output = check(listOf(account("trabalho", quota("Claude 5h", 91))), quiet = true)
        assertEquals(2, output.exitCode)
        assertTrue(output.lines.isEmpty())
    }

    @Test
    fun `sem leitura ou leitura velha sai com 3`() {
        assertEquals(3, check(emptyList()).exitCode)
        val old = listOf(account("pessoal", quota("Claude 5h", 10)).copy(fetchedAt = now - 30.minutes))
        assertEquals(3, TerminalCheckView.render(old, now, null, 10.minutes, false, pt, color).exitCode)
    }

    @Test
    fun `id da cota e estavel, sem acento e independente do idioma`() {
        val personal = account("Padrão", quota("Claude 5h", 62))
        assertEquals("anthropic:padrao:claude-5h", terminalQuotaId(personal, personal.quotas.single()))
        val go = account(null, quota("Go semanal", 19), source = ApiSource.OPENCODE_GO, name = "OpenCode Go")
        assertEquals("opencode_go:go-semanal", terminalQuotaId(go, go.quotas.single()))
        val codex = account(null, quota("Codex limit GPT-5.3-Codex-Spark (7d)", 12), source = ApiSource.CODEX, name = "Codex")
        assertEquals("codex:codex-limit-gpt-5.3-codex-spark-7d", terminalQuotaId(codex, codex.quotas.single()))
    }
}
