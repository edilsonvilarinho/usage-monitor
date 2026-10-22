package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource

/**
 * Cor de um trecho de texto no terminal, pelo **papel** do token
 * (`docs/design-system/tokens/terminal.css`), nunca pelo hex: o mesmo papel vira
 * truecolor, índice ANSI ou nada, conforme o terminal de quem lê.
 */
sealed interface TerminalColor {
    data object Muted : TerminalColor
    data object Dim : TerminalColor
    data object Border : TerminalColor
    data object Gold : TerminalColor
    data object Ok : TerminalColor
    data object Warn : TerminalColor
    data object Crit : TerminalColor
    data object Input : TerminalColor
    data object Output : TerminalColor
    data object CacheRead : TerminalColor
    data object CacheWrite : TerminalColor

    /** Acento de identidade da fonte, o mesmo de `colors.css`. */
    data class Source(val source: ApiSource) : TerminalColor
}

/**
 * Um trecho de texto com um estilo só. [color] nulo é a cor do texto do terminal.
 * [selected] pinta o fundo de seleção, o único fundo que o modo terminal pinta.
 */
data class TerminalSpan(
    val text: String,
    val color: TerminalColor? = null,
    val bold: Boolean = false,
    val selected: Boolean = false
)

/**
 * Uma linha de saída. A largura é contada em `Char`: todo glifo do design system
 * ocupa uma célula e cabe numa unidade UTF-16, por isso a tabela de glifos é fechada.
 */
data class TerminalLine(val spans: List<TerminalSpan>) {
    val width: Int
        get() = spans.sumOf { span -> span.text.length }

    val plainText: String
        get() = spans.joinToString("") { span -> span.text }

    operator fun plus(other: TerminalLine): TerminalLine = TerminalLine(spans + other.spans)

    /** Completa com espaços até [targetWidth]; linha mais larga fica como está. */
    fun padEnd(targetWidth: Int): TerminalLine {
        val missing = targetWidth - width
        return if (missing > 0) TerminalLine(spans + TerminalSpan(" ".repeat(missing))) else this
    }

    companion object {
        val EMPTY = TerminalLine(emptyList())
    }
}

fun terminalLine(vararg spans: TerminalSpan): TerminalLine = TerminalLine(spans.toList())

fun terminalSpan(text: String, color: TerminalColor? = null, bold: Boolean = false): TerminalSpan =
    TerminalSpan(text, color, bold)
