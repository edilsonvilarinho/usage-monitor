package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.presentation.ui.TelegramBotMessages
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A barra do tmux (tela 1.3 do protótipo): `#(usage-monitor statusline tmux)` no
 * `status-right`. O tmux não aceita ANSI na barra; a cor vai no formato dele,
 * `#[fg=#4F8CFF]`, e `#` do texto vira `##`.
 *
 * Uma entrada por conta: a sigla na cor da fonte e o maior percentual da conta em
 * negrito. O estado abreviado só aparece acima do limiar de atenção; em dia, nada.
 */
internal object TerminalTmux {

    private val STALE_AFTER = 10.minutes

    fun render(accounts: List<TerminalAccount>, now: Instant, labels: TerminalLabels, profile: TerminalProfile): String {
        val entries = accounts.mapNotNull { account -> entry(account, labels, profile) }
        val spans = mutableListOf<TerminalSpan>()
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                spans += TerminalSpan(" ")
            }
            spans += entry
        }
        accounts.mapNotNull { account -> account.fetchedAt }.maxOrNull()?.takeIf { fetched -> now - fetched > STALE_AFTER }?.let { fetched ->
            spans += TerminalSpan(" ")
            spans += TerminalSpan(labels.readingFrom(TelegramBotMessages.clock(fetched)), TerminalColor.Warn)
        }
        return spans.joinToString("") { span -> style(span, profile) }
    }

    private fun entry(account: TerminalAccount, labels: TerminalLabels, profile: TerminalProfile): List<TerminalSpan>? {
        val worst = account.quotas.filter { quota -> quota.percent != null }.maxByOrNull { quota -> quota.percent ?: 0 } ?: return null
        val percent = worst.percent ?: return null
        val spans = mutableListOf(
            TerminalSpan(initials(account.profile ?: account.name) + " ", TerminalColor.Source(account.source)),
            TerminalSpan(percent.toString(), bold = true)
        )
        val zone = account.zoneOf(worst)
        if (zone == QuotaZone.ATTENTION || zone == QuotaZone.CRITICAL) {
            spans += TerminalSpan(" " + labels.zoneShort(zone), TerminalPrimitives.zoneColor(zone))
        }
        return spans
    }

    /**
     * Duas letras que reconhecem a conta: as iniciais das duas primeiras palavras, ou as
     * duas primeiras letras de um nome de uma palavra só ("trabalho" → "tr", "OpenCode Go"
     * → "og"). Sem acento, porque a barra do tmux muitas vezes roda sem UTF-8.
     */
    internal fun initials(name: String): String {
        val words = withoutAccents(name).lowercase().split(' ', '-', '_').filter { word -> word.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}"
            words.isNotEmpty() -> words[0].take(2)
            else -> "?"
        }
    }

    private fun style(span: TerminalSpan, profile: TerminalProfile): String {
        val raw = if (profile.unicode) span.text else withoutAccents(span.text)
        val text = raw.replace("#", "##")
        if (profile.palette == TerminalPalette.NONE || text.isEmpty()) {
            return text
        }
        val attributes = buildList {
            span.color?.let { color -> add("fg=" + colour(color, profile.palette)) }
            if (span.bold) {
                add("bold")
            }
        }
        if (attributes.isEmpty()) {
            return text
        }
        return "#[${attributes.joinToString(",")}]$text#[default]"
    }

    /** Hex em truecolor; em 16 cores, o `colourN` do tmux com o mesmo índice ANSI. */
    private fun colour(color: TerminalColor, palette: TerminalPalette): String {
        if (palette == TerminalPalette.ANSI16) {
            val code = TerminalColors.ansi16(color)
            val index = if (code >= 90) code - 90 + 8 else code - 30
            return "colour$index"
        }
        val rgb = TerminalColors.hex(color, light = palette == TerminalPalette.TRUECOLOR_LIGHT)
        return "#" + rgb.toString(16).padStart(6, '0').uppercase()
    }
}
