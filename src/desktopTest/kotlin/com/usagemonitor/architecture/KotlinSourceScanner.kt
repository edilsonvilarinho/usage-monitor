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

/** Uma `data class`: nome, propriedades do construtor e corpo (vazio quando não tem). */
internal data class DataClassDeclaration(val name: String, val properties: List<String>, val body: String)

private val DATA_CLASS_HEADER = Regex("""\bdata\s+class\s+(\w+)\s*(?:<[^>]*>)?\s*\(""")
private val CONSTRUCTOR_PROPERTY = Regex("""\b(?:val|var)\s+(\w+)\s*:""")

/**
 * Cada `data class` do arquivo, lida do código sem comentários nem strings. O corpo
 * é o bloco depois do construtor (e da lista de supertipos, quando há), na mesma linha.
 */
internal fun dataClassDeclarations(source: String): List<DataClassDeclaration> {
    val code = stripCommentsAndStrings(source)
    return DATA_CLASS_HEADER.findAll(code).mapNotNull { match ->
        val open = match.range.last
        val close = matchingParenthesis(code, open) ?: return@mapNotNull null
        val properties = CONSTRUCTOR_PROPERTY.findAll(code.substring(open + 1, close))
            .map { property -> property.groupValues[1] }
            .toList()
        DataClassDeclaration(match.groupValues[1], properties, bodyAfterConstructor(code, close + 1))
    }.toList()
}

private fun bodyAfterConstructor(code: String, from: Int): String {
    var index = from
    while (index < code.length && code[index] != '\n' && code[index] != '{') {
        index++
    }
    if (index >= code.length || code[index] != '{') {
        return ""
    }
    val close = matchingBrace(code, index) ?: return ""
    return code.substring(index, close + 1)
}

private fun matchingParenthesis(code: String, open: Int): Int? {
    var depth = 0
    for (index in open until code.length) {
        when (code[index]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) {
                    return index
                }
            }
        }
    }
    return null
}

/** Propriedade de classe `@Serializable`: nome e se carrega `@SerialName`. */
internal data class SerializableProperty(val name: String, val hasSerialName: Boolean)

/** Classe `@Serializable` com as propriedades que entram no JSON. */
internal data class SerializableClassDeclaration(val name: String, val properties: List<SerializableProperty>)

private val SERIALIZABLE_ANNOTATION = Regex("""@Serializable\b""")
private val CLASS_KEYWORD = Regex("""(?<![:.\w])(enum\s+class|class|object|interface)\s+(\w+)""")
private val NOT_A_CLASS_BETWEEN = Regex("""\b(val|var|fun)\b|[{}]""")
private val BODY_OPEN = Regex("""\A\s*(?::[^{}\n]*)?\{""")
private val BY_DELEGATE = Regex("""\bby\b""")
private const val PROPERTY_PATTERN = """((?:@[\w.]+(?:\([^)]*\))?\s*)*)(?:(?:private|internal|public|protected|override|lateinit|const)\s+)*\b(val|var)\s+(\w+)"""
private val SERIAL_CONSTRUCTOR_PROPERTY = Regex("""^\s*$PROPERTY_PATTERN""")
private val BODY_PROPERTY = Regex(PROPERTY_PATTERN)

/** Classes `@Serializable` (não enum, object nem interface) com as propriedades que entram no JSON. */
internal fun serializableClassDeclarations(source: String): List<SerializableClassDeclaration> {
    val code = stripCommentsAndStrings(source)
    return SERIALIZABLE_ANNOTATION.findAll(code)
        .mapNotNull { annotation -> serializableClassAfter(code, annotation.range.last + 1) }
        .toList()
}

private fun serializableClassAfter(code: String, from: Int): SerializableClassDeclaration? {
    val keyword = CLASS_KEYWORD.find(code, from) ?: return null
    val between = code.substring(from, keyword.range.first)
    if (keyword.groupValues[1] != "class" || NOT_A_CLASS_BETWEEN.containsMatchIn(between)) {
        return null
    }
    return SerializableClassDeclaration(name = keyword.groupValues[2], properties = classProperties(code, keyword.range.last + 1))
}

/** Propriedades do construtor primário e do corpo (profundidade 1), nessa ordem. */
private fun classProperties(code: String, afterName: Int): List<SerializableProperty> {
    val properties = mutableListOf<SerializableProperty>()
    var cursor = afterName
    while (cursor < code.length && code[cursor] != '(' && code[cursor] != '{' && code[cursor] != '\n') {
        cursor++
    }
    if (cursor < code.length && code[cursor] == '(') {
        val close = matchingParenthesis(code, cursor) ?: return properties
        properties += constructorProperties(code.substring(cursor + 1, close))
        cursor = close + 1
    }
    val open = BODY_OPEN.find(code.substring(cursor))?.range?.last ?: return properties
    val bodyOpen = cursor + open
    val bodyClose = matchingBrace(code, bodyOpen) ?: return properties
    properties += bodyProperties(code.substring(bodyOpen + 1, bodyClose))
    return properties
}

private fun constructorProperties(parameters: String): List<SerializableProperty> =
    splitTopLevel(parameters).mapNotNull { parameter -> propertyOf(SERIAL_CONSTRUCTOR_PROPERTY.find(parameter)) }

private fun bodyProperties(body: String): List<SerializableProperty> {
    val masked = maskNestedBlocks(body)
    return BODY_PROPERTY.findAll(masked)
        .filter { match -> !isDelegatedOrComputed(masked.substring(match.range.last + 1)) }
        .mapNotNull { match -> propertyOf(match) }
        .toList()
}

/** Propriedade com `@Transient` fica de fora; `@SerialName` vale pelo prefixo de anotações. */
private fun propertyOf(match: MatchResult?): SerializableProperty? {
    match ?: return null
    val annotations = match.groupValues[1]
    if (annotations.contains("@Transient")) {
        return null
    }
    return SerializableProperty(name = match.groupValues[3], hasSerialName = annotations.contains("@SerialName"))
}

/** Propriedade delegada (`by`) ou computada (`get()`, na mesma linha ou na seguinte) não tem campo de backing. */
private fun isDelegatedOrComputed(tail: String): Boolean {
    val sameLine = tail.substringBefore('\n')
    if (BY_DELEGATE.containsMatchIn(sameLine) || sameLine.contains("get(")) {
        return true
    }
    val nextLine = tail.substringAfter('\n', "").lineSequence().firstOrNull { line -> line.isNotBlank() }
    return nextLine?.trim()?.startsWith("get(") == true
}

/** Troca o conteúdo de blocos aninhados por espaço, mantendo quebras de linha: sobra só o nível 1. */
private fun maskNestedBlocks(body: String): String {
    val out = StringBuilder(body.length)
    var depth = 0
    for (char in body) {
        when (char) {
            '{' -> {
                depth++
                out.append(' ')
            }
            '}' -> {
                depth--
                out.append(' ')
            }
            '\n' -> out.append('\n')
            else -> out.append(if (depth > 0) ' ' else char)
        }
    }
    return out.toString()
}

/** Divide os parâmetros por vírgula de nível 0 (ignora vírgula dentro de `<>`, `()`, `[]`, `{}`). */
private fun splitTopLevel(text: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    for (char in text) {
        when {
            char == ',' && depth == 0 -> {
                parts += current.toString()
                current.setLength(0)
            }
            char in "([{<" -> {
                depth++
                current.append(char)
            }
            char in ")]}>" -> {
                depth--
                current.append(char)
            }
            else -> current.append(char)
        }
    }
    parts += current.toString()
    return parts
}
