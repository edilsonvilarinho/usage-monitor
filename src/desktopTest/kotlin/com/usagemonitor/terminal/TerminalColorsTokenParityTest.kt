package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.presentation.terminal.TerminalColor
import com.usagemonitor.presentation.terminal.TerminalColors
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `TerminalColors` contra `docs/design-system/tokens/terminal.css`, a fonte de
 * verdade das paletas do modo terminal. Divergência aqui é hex copiado errado de
 * um lado ou token mudado sem o código acompanhar.
 */
class TerminalColorsTokenParityTest {

    private val css = File("docs/design-system/tokens/terminal.css").readText()

    /** Token do CSS por cor do código. Fontes que dividem token aparecem duas vezes. */
    private val tokens: List<Pair<String, TerminalColor>> = listOf(
        "muted" to TerminalColor.Muted,
        "dim" to TerminalColor.Dim,
        "border" to TerminalColor.Border,
        "gold" to TerminalColor.Gold,
        "ok" to TerminalColor.Ok,
        "warn" to TerminalColor.Warn,
        "crit" to TerminalColor.Crit,
        "input" to TerminalColor.Input,
        "output" to TerminalColor.Output,
        "cread" to TerminalColor.CacheRead,
        "cwrite" to TerminalColor.CacheWrite,
        "anthropic" to TerminalColor.Source(ApiSource.ANTHROPIC),
        "codex" to TerminalColor.Source(ApiSource.CODEX),
        "oc" to TerminalColor.Source(ApiSource.OPENCODE),
        "oc" to TerminalColor.Source(ApiSource.OPENCODE_GO),
        "cursor" to TerminalColor.Source(ApiSource.CURSOR),
        "minimax" to TerminalColor.Source(ApiSource.MINIMAX),
        "deepseek" to TerminalColor.Source(ApiSource.DEEPSEEK),
        "kilo" to TerminalColor.Source(ApiSource.KILO),
        "openrouter" to TerminalColor.Source(ApiSource.OPENROUTER),
        "gemini" to TerminalColor.Source(ApiSource.GEMINI),
        "gemini" to TerminalColor.Source(ApiSource.ANTIGRAVITY)
    )

    @Test
    fun `every source has a token in both truecolor palettes`() {
        val mapped = tokens.mapNotNull { (_, color) -> (color as? TerminalColor.Source)?.source }.toSet()
        assertEquals(ApiSource.entries.toSet(), mapped)
    }

    @Test
    fun `dark truecolor matches the root block`() {
        val palette = block(":root {")
        for ((token, color) in tokens) {
            assertEquals(palette.getValue(token), TerminalColors.hex(color, light = false), "--term-$token escuro")
        }
        assertEquals(palette.getValue("selection"), TerminalColors.SELECTION_DARK)
    }

    @Test
    fun `light truecolor matches the light block`() {
        val palette = block("[data-term-palette=\"light\"] {")
        for ((token, color) in tokens) {
            assertEquals(palette.getValue(token), TerminalColors.hex(color, light = true), "--term-$token claro")
        }
        assertEquals(palette.getValue("selection"), TerminalColors.SELECTION_LIGHT)
    }

    /** O mapa de 16 cores do comentário do bloco 3: o código emite o índice, não o hex. */
    @Test
    fun `ansi16 follows the documented map`() {
        val expected = mapOf(
            TerminalColor.Source(ApiSource.ANTHROPIC) to 34,
            TerminalColor.Input to 34,
            TerminalColor.Source(ApiSource.CODEX) to 36,
            TerminalColor.Source(ApiSource.OPENCODE) to 32,
            TerminalColor.Source(ApiSource.OPENCODE_GO) to 32,
            TerminalColor.CacheRead to 32,
            TerminalColor.Ok to 32,
            TerminalColor.Source(ApiSource.MINIMAX) to 33,
            TerminalColor.CacheWrite to 33,
            TerminalColor.Warn to 33,
            TerminalColor.Gold to 33,
            TerminalColor.Source(ApiSource.DEEPSEEK) to 35,
            TerminalColor.Output to 35,
            TerminalColor.Source(ApiSource.GEMINI) to 35,
            TerminalColor.Source(ApiSource.ANTIGRAVITY) to 35,
            TerminalColor.Source(ApiSource.OPENROUTER) to 35,
            TerminalColor.Crit to 31,
            TerminalColor.Source(ApiSource.CURSOR) to 92,
            TerminalColor.Source(ApiSource.KILO) to 93,
            TerminalColor.Muted to 90,
            TerminalColor.Dim to 90,
            TerminalColor.Border to 90
        )
        for ((color, code) in expected) {
            assertEquals(code, TerminalColors.ansi16(color), "$color")
        }
    }

    private fun block(header: String): Map<String, Int> {
        val start = css.indexOf(header)
        check(start >= 0) { "bloco '$header' não encontrado em terminal.css" }
        val body = css.substring(start, css.indexOf('}', start))
        return Regex("""--term-([a-z]+):\s*#([0-9A-Fa-f]{6})""").findAll(body)
            .associate { match -> match.groupValues[1] to match.groupValues[2].toInt(16) }
    }
}
