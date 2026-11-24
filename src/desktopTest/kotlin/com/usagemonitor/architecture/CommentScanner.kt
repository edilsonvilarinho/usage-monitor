package com.usagemonitor.architecture

/** Bloco de linhas seguidas só com comentário; [startLine] começa em 1. */
internal data class CommentBlock(val startLine: Int, val lineCount: Int)

/** Blocos de [minLines] ou mais linhas só de comentário; linha em branco fora de comentário os separa. */
internal fun longCommentBlocks(source: String, minLines: Int = 6): List<CommentBlock> {
    val blocks = mutableListOf<CommentBlock>()
    var runStart = 0
    var run = 0
    scanCommentLines(source).forEachIndexed { index, line ->
        if (isCommentOnly(line)) {
            if (run == 0) runStart = index + 1
            run++
        } else {
            if (run >= minLines) blocks += CommentBlock(runStart, run)
            run = 0
        }
    }
    if (run >= minLines) blocks += CommentBlock(runStart, run)
    return blocks
}

/** Números (1-based) das linhas cujo comentário cita issue por `#` com 2 a 4 dígitos. */
internal fun issueReferenceCommentLines(source: String): List<Int> =
    scanCommentLines(source).withIndex()
        .filter { indexed -> ISSUE_REFERENCE.containsMatchIn(indexed.value.comment.toString()) }
        .map { indexed -> indexed.index + 1 }

private val ISSUE_REFERENCE = Regex("#\\d{2,4}\\b")

/** Linha sem código fora de comentário e com comentário, ou dentro de bloco aberto (vazia conta). */
private fun isCommentOnly(line: ScannedLine): Boolean =
    line.codeChars == 0 && (line.comment.isNotEmpty() || line.startsInBlock)

private class ScannedLine(val startsInBlock: Boolean) {
    var codeChars = 0
    val comment = StringBuilder()
}

private fun scanCommentLines(source: String): List<ScannedLine> =
    CommentLineScanner(source.replace("\r\n", "\n")).scan()

/** Varredura de caracteres: strings, raw strings e char literals não são comentário; bloco aninha. */
private class CommentLineScanner(private val text: String) {
    private enum class State { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, RAW_STRING, CHAR }

    private val lines = mutableListOf<ScannedLine>()
    private var line = ScannedLine(startsInBlock = false)
    private var state = State.CODE
    private var depth = 0

    fun scan(): List<ScannedLine> {
        var i = 0
        while (i < text.length) {
            i = if (text[i] == '\n') newLine(i) else scanChar(i)
        }
        lines += line
        return lines
    }

    private fun newLine(i: Int): Int {
        lines += line
        if (state == State.LINE_COMMENT) state = State.CODE
        line = ScannedLine(startsInBlock = state == State.BLOCK_COMMENT)
        return i + 1
    }

    private fun scanChar(i: Int): Int = when (state) {
        State.CODE -> scanCode(i)
        State.LINE_COMMENT -> appendComment(i, 1)
        State.BLOCK_COMMENT -> scanBlock(i)
        State.STRING -> scanQuoted(i, '"')
        State.CHAR -> scanQuoted(i, '\'')
        State.RAW_STRING -> scanRaw(i)
    }

    private fun scanCode(i: Int): Int = when {
        text.startsWith("//", i) -> open(State.LINE_COMMENT, i, 2)
        text.startsWith("/*", i) -> {
            depth = 1
            open(State.BLOCK_COMMENT, i, 2)
        }
        text.startsWith("\"\"\"", i) -> open(State.RAW_STRING, i, 3, code = true)
        text[i] == '"' -> open(State.STRING, i, 1, code = true)
        text[i] == '\'' -> open(State.CHAR, i, 1, code = true)
        else -> {
            if (!text[i].isWhitespace()) line.codeChars++
            i + 1
        }
    }

    private fun open(next: State, i: Int, width: Int, code: Boolean = false): Int {
        state = next
        if (code) line.codeChars++ else line.comment.append(text, i, i + width)
        return i + width
    }

    private fun appendComment(i: Int, width: Int): Int {
        line.comment.append(text, i, i + width)
        return i + width
    }

    private fun scanBlock(i: Int): Int = when {
        text.startsWith("/*", i) -> {
            depth++
            appendComment(i, 2)
        }
        text.startsWith("*/", i) -> {
            depth--
            if (depth == 0) state = State.CODE
            appendComment(i, 2)
        }
        else -> appendComment(i, 1)
    }

    private fun scanQuoted(i: Int, quote: Char): Int {
        val escaped = text[i] == '\\' && i + 1 < text.length && text[i + 1] != '\n'
        val width = if (escaped) 2 else 1
        line.codeChars++
        if (!escaped && text[i] == quote) state = State.CODE
        return i + width
    }

    private fun scanRaw(i: Int): Int {
        if (text.startsWith("\"\"\"", i)) {
            line.codeChars += 3
            state = State.CODE
            return i + 3
        }
        line.codeChars++
        return i + 1
    }
}
