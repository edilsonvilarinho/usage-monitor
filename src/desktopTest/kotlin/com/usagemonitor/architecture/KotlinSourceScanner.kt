package com.usagemonitor.architecture

import java.io.File

/** Um arquivo de produção lido do disco, com o caminho relativo à raiz do projeto. */
internal data class SourceFile(val path: String, val text: String) {
    /**
     * Linhas do arquivo sem as linhas em branco do fim. Normaliza CRLF antes: sem
     * isso a mesma árvore mediria diferente num checkout com `core.autocrlf`.
     */
    val lineCount: Int
        get() = text.replace("\r\n", "\n").split('\n').dropLastWhile { line -> line.isBlank() }.size

    /** Pacotes importados, na ordem em que aparecem. */
    val imports: List<String>
        get() = text.lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.startsWith("import ") }
            .map { line -> line.removePrefix("import ").substringBefore(" as ").trim() }
            .toList()
}

/** Uma função declarada, com o tamanho da declaração inteira (assinatura até a chave final). */
internal data class FunctionSpan(val name: String, val startLine: Int, val lineCount: Int)

/** Todos os `.kt` de produção (`src/<conjunto>Main/kotlin`), com o caminho em barras normais. */
internal fun productionSources(projectDir: File = File(".")): List<SourceFile> {
    val src = File(projectDir, "src")
    check(src.isDirectory) { "Raiz de fontes não encontrada em ${src.absolutePath}" }
    return src.listFiles().orEmpty()
        .filter { dir -> dir.isDirectory && dir.name.endsWith("Main") }
        .flatMap { dir -> File(dir, "kotlin").walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
        .map { file ->
            val relative = file.relativeTo(projectDir).invariantSeparatorsPath
            SourceFile(path = relative, text = file.readText())
        }
        .sortedBy { source -> source.path }
}

/**
 * Troca comentários e o conteúdo de strings e caracteres por nada, preservando as
 * quebras de linha: o que sobra tem as chaves do código e só elas, e a contagem
 * de linhas continua batendo com o arquivo.
 *
 * Template de string com chave dentro (`"${a}"`) some junto com a string, que é o
 * desejado — aquela chave não abre bloco nenhum.
 */
internal fun stripCommentsAndStrings(source: String): String {
    val out = StringBuilder(source.length)
    var index = 0
    val length = source.length
    while (index < length) {
        when {
            source.startsWith("//", index) -> {
                val end = source.indexOf('\n', index).let { found -> if (found < 0) length else found }
                index = end
            }
            source.startsWith("/*", index) -> {
                val close = source.indexOf("*/", index + 2)
                val end = if (close < 0) length else close + 2
                repeat(source.substring(index, end).count { char -> char == '\n' }) { out.append('\n') }
                index = end
            }
            source.startsWith("\"\"\"", index) -> {
                val close = source.indexOf("\"\"\"", index + 3)
                val end = if (close < 0) length else close + 3
                out.append("\"\"")
                repeat(source.substring(index, end).count { char -> char == '\n' }) { out.append('\n') }
                index = end
            }
            source[index] == '"' || source[index] == '\'' -> {
                val quote = source[index]
                var cursor = index + 1
                while (cursor < length && source[cursor] != quote && source[cursor] != '\n') {
                    if (source[cursor] == '\\') {
                        cursor++
                    }
                    cursor++
                }
                out.append(quote).append(quote)
                index = cursor + 1
            }
            else -> {
                out.append(source[index])
                index++
            }
        }
    }
    return out.toString()
}

private val FUN_KEYWORD = Regex("""\bfun\b""")
private val FUN_NAME = Regex("""^\s*(?:<[^>]*>\s*)?(?:[\w.]+\.)?(`[^`]+`|\w+)""")
private val NEXT_DECLARATION = Regex("""^\s*(fun|val|var|class|object|interface|@|private|internal|public|override|protected)\b""")

/**
 * Cada função com corpo de bloco (`{ ... }`, direto ou depois de `=` na mesma linha,
 * como `= application {`), com o tamanho em linhas da assinatura até a chave que fecha.
 *
 * Função de expressão sem bloco e declaração abstrata não têm corpo para medir e
 * ficam de fora. Lambdas e funções locais aninhadas contam dentro de quem as contém:
 * é o tamanho do bloco que o leitor precisa segurar na cabeça.
 */
internal fun functionSpans(source: String): List<FunctionSpan> {
    val code = stripCommentsAndStrings(source)
    val spans = mutableListOf<FunctionSpan>()
    for (match in FUN_KEYWORD.findAll(code)) {
        val bodyStart = bodyStartAfterSignature(code, match.range.last + 1) ?: continue
        val bodyEnd = matchingBrace(code, bodyStart) ?: continue
        val name = FUN_NAME.find(code.substring(match.range.last + 1, minOf(code.length, match.range.last + 160)))
            ?.groupValues?.get(1) ?: "?"
        val startLine = lineOf(code, match.range.first)
        val endLine = lineOf(code, bodyEnd)
        spans += FunctionSpan(name = name, startLine = startLine, lineCount = endLine - startLine + 1)
    }
    return spans
}

private fun bodyStartAfterSignature(code: String, from: Int): Int? {
    var index = from
    var depth = 0
    var closedParameters = false
    while (index < code.length && !closedParameters) {
        when (code[index]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) {
                    closedParameters = true
                }
            }
        }
        index++
    }
    if (!closedParameters) {
        return null
    }
    while (index < code.length) {
        val char = code[index]
        when {
            char == '{' -> return index
            char == '=' -> {
                val endOfLine = code.indexOf('\n', index).let { found -> if (found < 0) code.length else found }
                val brace = code.indexOf('{', index)
                return if (brace in index until endOfLine) brace else null
            }
            char == '}' -> return null
            char == '\n' && NEXT_DECLARATION.containsMatchIn(code.substring(index + 1, minOf(code.length, index + 60))
                .lineSequence().firstOrNull().orEmpty()) -> return null
        }
        index++
    }
    return null
}

private fun matchingBrace(code: String, open: Int): Int? {
    var depth = 0
    for (index in open until code.length) {
        when (code[index]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return index
                }
            }
        }
    }
    return null
}

private fun lineOf(code: String, offset: Int): Int {
    var line = 1
    for (index in 0 until offset) {
        if (code[index] == '\n') {
            line++
        }
    }
    return line
}
