package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Tela 1.3 do protótipo. */
class TerminalTmuxTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val pt = TerminalLabels(AppLanguage.PT)
    private val truecolor = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val plain = TerminalProfile(TerminalPalette.NONE, unicode = true)

    private fun quota(label: String, percent: Int?) =
        UsageSnapshotQuota(label, PeriodType.INTERVAL, UsageUnit.PERCENTAGE, (percent ?: 0).toLong(), 100, percent, now + 1.hours, "USD")

    private fun account(source: ApiSource, name: String, profile: String?, vararg quotas: UsageSnapshotQuota, fetchedAt: Instant = now) =
        TerminalAccount(source, "${source.name}:$profile", name, profile, fetchedAt, listOf(70, 90), quotas.toList())

    private val accounts = listOf(
        account(ApiSource.ANTHROPIC, "Anthropic", "Claude Max", quota("Claude 5h", 62), quota("Claude 7d", 38)),
        account(ApiSource.ANTHROPIC, "Anthropic", "trabalho", quota("Claude 5h", 91)),
        account(ApiSource.CODEX, "Codex", null, quota("Codex 5h", 24)),
        account(ApiSource.CURSOR, "Cursor", null, quota("Cursor Included", 73)),
        account(ApiSource.DEEPSEEK, "DeepSeek", null, quota("Saldo", null))
    )

    @Test
    fun `uma entrada por conta com o maior percentual e o estado so acima do limiar`() {
        assertEquals("cm 62 tr 91 crít co 24 cu 73 atn", TerminalTmux.render(accounts, now, pt, plain))
    }

    @Test
    fun `cor no formato do tmux, e nunca ANSI`() {
        val text = TerminalTmux.render(accounts.take(2), now, pt, truecolor)
        assertEquals("#[fg=#4F8CFF]cm #[default]#[bold]62#[default] #[fg=#4F8CFF]tr #[default]#[bold]91#[default]#[fg=#E86A6A] crít#[default]", text)
    }

    @Test
    fun `16 cores usa o colourN do tmux`() {
        val text = TerminalTmux.render(listOf(accounts[2]), now, pt, TerminalProfile(TerminalPalette.ANSI16, unicode = true))
        assertEquals("#[fg=colour6]co #[default]#[bold]24#[default]", text)
    }

    @Test
    fun `leitura velha mostra a hora dela`() {
        val old = listOf(accounts[2].copy(fetchedAt = now - 2.hours))
        assertEquals("co 24 leitura de 12:23", TerminalTmux.render(old, now, pt, plain))
    }

    @Test
    fun `sem Unicode as siglas e o estado perdem o acento`() {
        assertEquals("tr 91 crit", TerminalTmux.render(listOf(accounts[1]), now, pt, TerminalProfile(TerminalPalette.NONE, unicode = false)))
    }

    @Test
    fun `siglas pelas iniciais ou pelas duas primeiras letras, e o # do texto e escapado`() {
        assertEquals("og", TerminalTmux.initials("OpenCode Go"))
        assertEquals("pa", TerminalTmux.initials("Padrão"))
        assertEquals("cc", TerminalTmux.initials("claude-code"))
        assertEquals("##1 50", TerminalTmux.render(listOf(account(ApiSource.CODEX, "Codex", "#1", quota("Codex 5h", 50))), now, pt, plain))
    }

    @Test
    fun `sem leitura nenhuma a barra fica vazia`() {
        assertEquals("", TerminalTmux.render(emptyList(), now, pt, plain))
        assertEquals("", TerminalTmux.render(listOf(accounts[4]), now - 1.minutes, pt, plain))
    }
}
