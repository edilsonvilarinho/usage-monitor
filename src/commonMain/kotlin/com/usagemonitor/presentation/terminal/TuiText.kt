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
}
