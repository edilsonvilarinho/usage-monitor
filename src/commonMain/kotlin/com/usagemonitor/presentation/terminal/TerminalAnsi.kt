package com.usagemonitor.presentation.terminal

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
            TerminalPalette.ANSI16 -> TerminalColors.ansi16(color).toString()
            TerminalPalette.TRUECOLOR_LIGHT -> truecolor(TerminalColors.hex(color, light = true), background = false)
            else -> truecolor(TerminalColors.hex(color, light = false), background = false)
        }

    private fun selectionBackground(palette: TerminalPalette): String =
        when (palette) {
            TerminalPalette.ANSI16 -> "100"
            TerminalPalette.TRUECOLOR_LIGHT -> truecolor(TerminalColors.SELECTION_LIGHT, background = true)
            else -> truecolor(TerminalColors.SELECTION_DARK, background = true)
        }

    private fun truecolor(rgb: Int, background: Boolean): String {
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        return "${if (background) 48 else 38};2;$red;$green;$blue"
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
