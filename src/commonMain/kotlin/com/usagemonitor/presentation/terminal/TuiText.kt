package com.usagemonitor.presentation.terminal

/** Texto corrido dentro da moldura (#441). */
internal object TuiText {

    /**
     * Quebra [text] em linhas de até [width] células, entre palavras. Palavra maior que a
     * linha é cortada: a moldura nunca transborda.
     */
    fun wrap(text: String, width: Int): List<String> {
        if (width <= 0) {
            return emptyList()
        }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(' ').filter { part -> part.isNotEmpty() }) {
            var piece = word
            while (piece.length > width) {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current = StringBuilder()
                }
                lines += piece.take(width)
                piece = piece.drop(width)
            }
            if (current.isEmpty()) {
                current.append(piece)
            } else if (current.length + 1 + piece.length <= width) {
                current.append(' ').append(piece)
            } else {
                lines += current.toString()
                current = StringBuilder(piece)
            }
        }
        if (current.isNotEmpty()) {
            lines += current.toString()
        }
        return lines
    }

    /**
     * Tokens abreviados com uma casa, `4,8M` e `136K` em português e `4.8M` em inglês. Conta
     * inteira, sem `String.format`: o separador sai do idioma do app, nunca do locale da JVM,
     * para duas máquinas desenharem a mesma tela.
     */
    fun tokens(count: Long, pt: Boolean): String {
        val separator = if (pt) ',' else '.'
        fun scaled(unit: Long, suffix: String): String {
            val tenths = count * 10 / unit
            val whole = tenths / 10
            val fraction = tenths % 10
            return if (fraction == 0L || whole >= 100L) "$whole$suffix" else "$whole$separator$fraction$suffix"
        }
        return when {
            count >= 1_000_000L -> scaled(1_000_000L, "M")
            count >= 1_000L -> scaled(1_000L, "K")
            else -> count.toString()
        }
    }
}
