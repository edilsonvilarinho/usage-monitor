package com.usagemonitor.presentation.terminal

/**
 * A tabela de glifos do design system do modo terminal (`cli/readme.md`, "Glifos"),
 * nas duas colunas: Unicode e ASCII. Glifo fora daqui não entra numa tela; um novo
 * entra primeiro na tabela do readme.
 */
data class TerminalGlyphs(
    /** Marca da fonte, na cor dela. */
    val sourceMark: String,
    /** Marca da linha em estado crítico quando não há cor: no ASCII, a única marca. */
    val criticalMark: String,
    val warning: String,
    val done: String,
    val skipped: String,
    val resetMark: String,
    val separator: String,
    val barFull: Char,
    val barEmpty: Char,
    /** Parciais de um a sete oitavos; vazio no ASCII, que arredonda para a célula. */
    val barEighths: String,
    val barOpen: String,
    val barClose: String,
    /** Anel em quartos da linha de status; vazio no ASCII, onde o número basta. */
    val rings: String,
    /** Segredo salvo, no lugar do valor (#440): nunca o segredo. */
    val secretMask: String = "••••"
) {
    /** O quarto de anel de [percent], ou `null` quando a coluna não tem anel. */
    fun ring(percent: Int): String? {
        if (rings.isEmpty()) {
            return null
        }
        val index = when {
            percent < 13 -> 0
            percent < 38 -> 1
            percent < 63 -> 2
            percent < 88 -> 3
            else -> 4
        }
        return rings[index].toString()
    }

    companion object {
        val UNICODE = TerminalGlyphs(
            sourceMark = "●",
            criticalMark = "●",
            warning = "▲",
            done = "✓",
            skipped = "–",
            resetMark = "┆",
            separator = "·",
            barFull = '█',
            barEmpty = '░',
            barEighths = "▏▎▍▌▋▊▉",
            barOpen = "",
            barClose = "",
            rings = "○◔◑◕●"
        )

        val ASCII = TerminalGlyphs(
            sourceMark = " ",
            criticalMark = "*",
            warning = "!",
            done = "ok",
            skipped = "-",
            resetMark = "|",
            separator = "-",
            barFull = '#',
            barEmpty = '-',
            barEighths = "",
            barOpen = "[",
            barClose = "]",
            rings = "",
            secretMask = "****"
        )
    }
}
