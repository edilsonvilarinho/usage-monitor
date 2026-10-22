package com.usagemonitor.presentation.terminal

/**
 * Traduz o que o terminal manda em [TuiKey] (#441). Função pura sobre um leitor de
 * caracteres: o JLine entrega a entrada crua, e o teste entrega uma lista.
 *
 * As setas chegam nas duas formas: `ESC [ A` (modo normal) e `ESC O A` (modo de aplicação,
 * o do `keypad_xmit`, e o que o JLine gera no console do Windows pela terminfo `windows`).
 * `ESC` sozinho só é `esc` se nada vier em [ESCAPE_TIMEOUT_MILLIS]; sequência desconhecida
 * (F1, Alt+tecla, Home) é engolida inteira, para não virar teclas soltas.
 */
object TuiKeyDecoder {
    /** O que o leitor devolve quando o tempo acaba sem caractere (o `READ_EXPIRED` do JLine). */
    const val TIMEOUT = -2
    const val END_OF_INPUT = -1
    const val ESCAPE_TIMEOUT_MILLIS = 50L

    private const val ESC = 27
    private const val CTRL_C = 3
    private const val BACKSPACE = 8
    private const val TAB = 9
    private const val LINE_FEED = 10
    private const val CARRIAGE_RETURN = 13
    private const val DELETE = 127

    /** A tecla que começa em [first], lendo o resto por [next]; `null` quando não é tecla que a TUI use. */
    fun decode(first: Int, next: (timeoutMillis: Long) -> Int): TuiKey? =
        when (first) {
            END_OF_INPUT, CTRL_C -> TuiKey.Interrupt
            ESC -> escape(next)
            CARRIAGE_RETURN, LINE_FEED -> TuiKey.Enter
            TAB -> TuiKey.Tab
            BACKSPACE, DELETE -> TuiKey.Backspace
            ' '.code -> TuiKey.Space
            else -> if (first < ' '.code) null else TuiKey.Text(first.toChar())
        }

    private fun escape(next: (Long) -> Int): TuiKey? {
        val introducer = next(ESCAPE_TIMEOUT_MILLIS)
        if (introducer == TIMEOUT || introducer == END_OF_INPUT) {
            return TuiKey.Escape
        }
        if (introducer != '['.code && introducer != 'O'.code) {
            return null
        }
        // Parâmetros (`1;5`) até o byte final, de `@` a `~`: Ctrl+seta chega como `ESC [ 1 ; 5 A`.
        var final = next(ESCAPE_TIMEOUT_MILLIS)
        while (final in '0'.code..'?'.code) {
            final = next(ESCAPE_TIMEOUT_MILLIS)
        }
        return when (final) {
            'A'.code -> TuiKey.Up
            'B'.code -> TuiKey.Down
            'C'.code -> TuiKey.Right
            'D'.code -> TuiKey.Left
            'M'.code -> if (introducer == 'O'.code) TuiKey.Enter else null
            else -> null
        }
    }
}
