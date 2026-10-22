package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource

/**
 * Escreve linhas com estilo em sequências ANSI (SGR), na paleta do perfil.
 *
 * Os hexes são os de `docs/design-system/tokens/terminal.css`, que repete os
 * acentos de `colors.css`; em 16 cores sai o índice ANSI e o terminal decide o tom.
 * Sem cor, sai só o texto. Sem Unicode, o texto perde o acento: o console que não
 * é UTF-8 (o do Windows sem a página 65001) trocaria a letra por lixo.
 */
object TerminalAnsi {
    private const val ESC = "\u001B["
    private const val RESET = "${ESC}0m"

    fun render(lines: List<TerminalLine>, profile: TerminalProfile): String =
        lines.joinToString("\n") { line -> render(line, profile) }

    fun render(line: TerminalLine, profile: TerminalProfile): String =
        line.spans.joinToString("") { span -> renderSpan(span, profile) }

    private fun renderSpan(span: TerminalSpan, profile: TerminalProfile): String {
        val text = if (profile.unicode) span.text else withoutAccents(span.text)
        if (profile.palette == TerminalPalette.NONE || text.isEmpty()) {
            return text
        }
        val codes = buildList {
            if (span.bold) {
                add("1")
            }
            span.color?.let { color -> add(foreground(color, profile.palette)) }
            if (span.selected) {
                add(selectionBackground(profile.palette))
            }
        }
        if (codes.isEmpty()) {
            return text
        }
        return "$ESC${codes.joinToString(";")}m$text$RESET"
    }

    private fun foreground(color: TerminalColor, palette: TerminalPalette): String =
        when (palette) {
            TerminalPalette.ANSI16 -> ansi16Foreground(color).toString()
            TerminalPalette.TRUECOLOR_LIGHT -> truecolor(lightHex(color), background = false)
            else -> truecolor(darkHex(color), background = false)
        }

    private fun selectionBackground(palette: TerminalPalette): String =
        when (palette) {
            TerminalPalette.ANSI16 -> "100"
            TerminalPalette.TRUECOLOR_LIGHT -> truecolor(0xEFEAEA, background = true)
            else -> truecolor(0x2A2626, background = true)
        }

    private fun truecolor(rgb: Int, background: Boolean): String {
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        return "${if (background) 48 else 38};2;$red;$green;$blue"
    }

    /** O mapa de 16 cores comentado em `terminal.css`. */
    private fun ansi16Foreground(color: TerminalColor): Int =
        when (color) {
            TerminalColor.Muted, TerminalColor.Dim, TerminalColor.Border -> 90
            TerminalColor.Gold, TerminalColor.Warn, TerminalColor.CacheWrite -> 33
            TerminalColor.Ok, TerminalColor.CacheRead -> 32
            TerminalColor.Crit -> 31
            TerminalColor.Input -> 34
            TerminalColor.Output -> 35
            is TerminalColor.Source -> when (color.source) {
                ApiSource.ANTHROPIC -> 34
                ApiSource.CODEX -> 36
                ApiSource.OPENCODE, ApiSource.OPENCODE_GO -> 32
                ApiSource.MINIMAX -> 33
                ApiSource.DEEPSEEK, ApiSource.OPENROUTER, ApiSource.GEMINI, ApiSource.ANTIGRAVITY -> 35
                ApiSource.CURSOR -> 92
                ApiSource.KILO -> 93
            }
        }

    private fun darkHex(color: TerminalColor): Int =
        when (color) {
            TerminalColor.Muted -> 0xB8B2B2
            TerminalColor.Dim -> 0x4A4444
            TerminalColor.Border -> 0x5A5353
            TerminalColor.Gold -> 0xE8AE63
            TerminalColor.Ok -> 0x4CAF50
            TerminalColor.Warn -> 0xFFA726
            TerminalColor.Crit -> 0xE86A6A
            TerminalColor.Input -> 0x4C8DFF
            TerminalColor.Output -> 0xB07CFF
            TerminalColor.CacheRead -> 0x4CAF50
            TerminalColor.CacheWrite -> 0xFFA726
            is TerminalColor.Source -> when (color.source) {
                ApiSource.ANTHROPIC -> 0x4F8CFF
                ApiSource.CODEX -> 0x27BFA3
                ApiSource.OPENCODE, ApiSource.OPENCODE_GO -> 0x7BD389
                ApiSource.MINIMAX -> 0xFF8A3D
                ApiSource.DEEPSEEK -> 0xC084FC
                ApiSource.KILO -> 0xE6D84E
                ApiSource.OPENROUTER -> 0xFF6FA8
                ApiSource.GEMINI, ApiSource.ANTIGRAVITY -> 0xE2A5E9
                ApiSource.CURSOR -> 0xC7E9A5
            }
        }

    private fun lightHex(color: TerminalColor): Int =
        when (color) {
            TerminalColor.Muted -> 0x686060
            TerminalColor.Dim -> 0xD7D0D0
            TerminalColor.Border -> 0xB9B1B1
            TerminalColor.Gold -> 0x8C643F
            TerminalColor.Ok -> 0x2E7D32
            TerminalColor.Warn -> 0x8A5000
            TerminalColor.Crit -> 0xB3261E
            TerminalColor.Input -> 0x1565C0
            TerminalColor.Output -> 0x6A3FB5
            TerminalColor.CacheRead -> 0x2E7D32
            TerminalColor.CacheWrite -> 0x8A5000
            is TerminalColor.Source -> when (color.source) {
                ApiSource.ANTHROPIC -> 0x1565C0
                ApiSource.CODEX -> 0x00695C
                ApiSource.OPENCODE, ApiSource.OPENCODE_GO -> 0x2E7D32
                ApiSource.MINIMAX -> 0x9A4A00
                ApiSource.DEEPSEEK -> 0x7B3FC4
                ApiSource.KILO -> 0x5F5500
                ApiSource.OPENROUTER -> 0xB23368
                ApiSource.GEMINI, ApiSource.ANTIGRAVITY -> 0x7C1D87
                ApiSource.CURSOR -> 0x477915
            }
        }
}

private const val ACCENTED = "áàâãäéèêëíìîïóòôõöúùûüçñÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑ"
private const val PLAIN = "aaaaaeeeeiiiiooooouuuucnAAAAAEEEEIIIIOOOOOUUUUCN"

/**
 * O texto sem acento, letra por letra. A pontuação tipográfica dos textos (`·`, `–`,
 * `—`, `…`) vira a equivalente ASCII de mesma largura, e qualquer outro caractere fora
 * do ASCII vira `?`. Tabela própria em vez de `java.text.Normalizer`, que não existe no
 * `commonMain`; o app só escreve em português e inglês.
 */
internal fun withoutAccents(text: String): String =
    buildString(text.length) {
        for (char in text) {
            val accented = ACCENTED.indexOf(char)
            when {
                accented >= 0 -> append(PLAIN[accented])
                char.code < 128 -> append(char)
                char == '·' || char == '–' || char == '—' -> append('-')
                char == '…' -> append('.')
                else -> append('?')
            }
        }
    }
