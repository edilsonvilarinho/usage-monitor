package com.usagemonitor.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Cancelamento de coroutine não pode ser engolido (issue #648). Em `suspend fun`, todo `catch`
 * genérico exige um `catch` de `CancellationException` antes, na mesma cadeia de `try`.
 */
class CancellationArchitectureRulesTest {

    @Test
    fun `suspend fun com catch generico tem CancellationException antes`() {
        if (productionCancellationViolations.isNotEmpty()) {
            fail(
                "catch genérico em suspend fun sem CancellationException antes. " +
                    "Adicione, antes dele, `catch (cancelled: CancellationException) { throw cancelled }`:\n" +
                    productionCancellationViolations.joinToString("\n")
            )
        }
    }

    @Test
    fun `catch generico em suspend fun sem cancelamento e reprovado`() {
        val found = cancellationViolations(
            """
            suspend fun load() {
                try { work() } catch (e: Exception) { }
            }
            """.trimIndent()
        )
        assertEquals(listOf("load"), found.map { violation -> violation.function })
    }

    @Test
    fun `CancellationException antes do generico passa, com nome qualificado ou simples`() {
        val found = cancellationViolations(
            """
            suspend fun load() {
                try { work() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (e: Throwable) { }
            }
            suspend fun sync() {
                try { work() } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) { }
            }
            """.trimIndent()
        )
        assertEquals(emptyList(), found.map { violation -> violation.function })
    }

    @Test
    fun `try interno sem cancelamento e reprovado mesmo com o externo protegido`() {
        val found = cancellationViolations(
            """
            suspend fun sync() {
                try {
                    try { work() } catch (e: Throwable) { }
                } catch (cancelled: CancellationException) { throw cancelled } catch (e: Throwable) { }
            }
            """.trimIndent()
        )
        assertEquals(listOf(3), found.map { violation -> violation.line })
    }

    @Test
    fun `fun comum com catch generico fica de fora`() {
        val found = cancellationViolations(
            """
            fun load() {
                try { work() } catch (e: Exception) { }
            }
            """.trimIndent()
        )
        assertEquals(emptyList(), found.map { violation -> violation.function })
    }

    @Test
    fun `catch de cancelamento depois do generico e reprovado`() {
        val found = cancellationViolations(
            """
            suspend fun load() {
                try { work() } catch (e: Exception) { } catch (cancelled: CancellationException) { throw cancelled }
            }
            """.trimIndent()
        )
        assertEquals(listOf("load"), found.map { violation -> violation.function })
    }

    @Test
    fun `lambda suspensa dentro de fun comum fica de fora e catch estreito passa`() {
        val found = cancellationViolations(
            """
            fun start(scope: CoroutineScope) {
                scope.launch { try { work() } catch (e: Exception) { } }
            }
            suspend fun narrow() {
                try { work() } catch (e: IllegalStateException) { }
            }
            """.trimIndent()
        )
        assertTrue(found.isEmpty(), "esperado sem violação, veio: $found")
    }

    @Test
    fun `corpo de expressao com try e catch generico e reprovado`() {
        val found = cancellationViolations(
            """
            suspend fun load() = try { work() } catch (e: Exception) { }
            """.trimIndent()
        )
        assertEquals(listOf("load"), found.map { violation -> violation.function })
    }

    @Test
    fun `corpo de expressao com withContext e try generico dentro e reprovado`() {
        val found = cancellationViolations(
            """
            suspend fun load() = withContext(x) { try { work() } catch (e: Throwable) { } }
            """.trimIndent()
        )
        assertEquals(listOf("load"), found.map { violation -> violation.function })
    }

    @Test
    fun `igual de expressao em linha propria e reprovado`() {
        val found = cancellationViolations(
            """
            suspend fun load(): Int
                = try {
                    work()
                } catch (e: Exception) {
                }
            """.trimIndent()
        )
        assertEquals(listOf("load"), found.map { violation -> violation.function })
    }

    @Test
    fun `valor padrao com igual na assinatura nao confunde o corpo de expressao`() {
        val found = cancellationViolations(
            """
            suspend fun load(retry: Int = 1) = try { work() } catch (e: Exception) { }
            """.trimIndent()
        )
        assertEquals(listOf("load"), found.map { violation -> violation.function })
    }

    @Test
    fun `corpo de expressao termina na linha e nao engole a funcao seguinte`() {
        val found = cancellationViolations(
            """
            suspend fun load() = work()
            suspend fun sync() {
                try { work() } catch (e: Exception) { }
            }
            """.trimIndent()
        )
        assertEquals(listOf("sync"), found.map { violation -> violation.function })
    }

    @Test
    fun `corpo de expressao com cancelamento antes do generico passa`() {
        val found = cancellationViolations(
            """
            suspend fun load() = try { work() } catch (cancelled: CancellationException) { throw cancelled } catch (e: Throwable) { }
            """.trimIndent()
        )
        assertEquals(emptyList(), found.map { violation -> violation.function })
    }

    private companion object {
        /** Violações de produção, uma linha por cadeia: arquivo, linha, função e o catch genérico. */
        val productionCancellationViolations: List<String> by lazy {
            productionSources().flatMap { source ->
                cancellationViolations(source.text).map { violation ->
                    "${source.path}:${violation.line} fun ${violation.function}: catch ${violation.genericCatch} sem CancellationException antes"
                }
            }
        }
    }
}

/** Uma cadeia `try` com catch genérico (`Throwable` ou `Exception`) sem `CancellationException` antes. */
internal data class CancellationViolation(val function: String, val line: Int, val genericCatch: String)

private val SUSPEND_FUN = Regex("""\bsuspend\s+fun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(`[^`]+`|\w+)\s*\(""")
private val TRY_BLOCK = Regex("""\btry\s*\{""")
private val CATCH_CLAUSE = Regex("""^\s*catch\s*\(([^)]*)\)\s*\{""")
private val GENERIC_CATCH = setOf("Throwable", "Exception")
private const val CANCELLATION = "CancellationException"

/**
 * Cada `try` dentro de `suspend fun` (corpo em bloco ou em expressão `= ...`) cujo catch genérico
 * não tem `CancellationException` antes na cadeia. `try` aninhado é julgado na própria cadeia.
 * Lambda suspensa dentro de `fun` comum fica de fora.
 */
internal fun cancellationViolations(source: String): List<CancellationViolation> {
    val code = stripCommentsAndStrings(source)
    val reported = mutableSetOf<Int>()
    val violations = mutableListOf<CancellationViolation>()
    for (declaration in SUSPEND_FUN.findAll(code)) {
        val body = bodyAfterSignature(code, declaration.range.last) ?: continue
        for (tryMatch in TRY_BLOCK.findAll(code.substring(body.start, body.end))) {
            val tryStart = body.start + tryMatch.range.first
            if (!reported.add(tryStart)) {
                continue
            }
            val tryClose = closingBrace(code, body.start + tryMatch.range.last) ?: continue
            val types = catchTypesAfter(code, tryClose + 1)
            val generic = types.indexOfFirst { type -> type in GENERIC_CATCH }
            val cancellation = types.indexOf(CANCELLATION)
            if (generic >= 0 && (cancellation < 0 || cancellation > generic)) {
                val line = code.substring(0, tryStart).count { char -> char == '\n' } + 1
                violations += CancellationViolation(declaration.groupValues[1], line, types[generic])
            }
        }
    }
    return violations
}

/** Corpo da função: intervalo `[start, end)` do bloco `{...}` ou da expressão depois de `=`. `null` se abstrata. */
private class Body(val start: Int, val end: Int)

/**
 * Procura o corpo depois dos parênteses da assinatura. `=` pode estar na linha seguinte;
 * quebra de linha sem `=` depois significa função sem corpo. Valor padrão dentro dos
 * parênteses fica de fora porque os parênteses são pulados antes.
 */
private fun bodyAfterSignature(code: String, parenthesis: Int): Body? {
    val closing = closingParenthesis(code, parenthesis) ?: return null
    var index = closing + 1
    while (index < code.length) {
        when (code[index]) {
            '{' -> {
                val close = closingBrace(code, index) ?: return null
                return Body(index, close + 1)
            }
            '=' -> return Body(index, expressionEnd(code, index))
            '\n' -> {
                val next = nextNonBlank(code, index + 1)
                if (next >= code.length || code[next] != '=') {
                    return null
                }
                index = next
            }
            else -> index++
        }
    }
    return null
}

/** Índice do `)` que fecha o `(` em `open`. */
private fun closingParenthesis(code: String, open: Int): Int? {
    var depth = 1
    var index = open + 1
    while (index < code.length && depth > 0) {
        when (code[index]) {
            '(' -> depth++
            ')' -> depth--
        }
        index++
    }
    return if (depth == 0) index - 1 else null
}

/**
 * Fim exclusivo da expressão que começa depois do `=` em `equals`. Termina na quebra de linha
 * fora de parênteses e colchetes, salvo se a linha seguinte começa com operador ou `.`.
 * Um `}` que fecha o último bloco da expressão só termina se não vier `catch` ou `finally` depois.
 */
private fun expressionEnd(code: String, equals: Int): Int {
    var depth = 0
    var index = equals + 1
    while (index < code.length) {
        when (code[index]) {
            '(', '[', '{' -> depth++
            ')', ']' -> {
                depth--
                if (depth < 0) return index
            }
            '}' -> {
                depth--
                if (depth < 0) return index
                if (depth == 0 && !handlerFollows(code, index + 1)) return index + 1
            }
            '\n' -> {
                if (depth == 0 && !continuesOnNextLine(code, index + 1)) return index
            }
        }
        index++
    }
    return code.length
}

/** `catch` ou `finally` logo depois (pulando espaços e quebras) continua a expressão `try`. */
private fun handlerFollows(code: String, from: Int): Boolean {
    val next = nextNonBlank(code, from)
    return code.startsWith("catch", next) || code.startsWith("finally", next)
}

private fun continuesOnNextLine(code: String, from: Int): Boolean {
    val next = nextNonBlank(code, from)
    return next < code.length && code[next] in CONTINUATION_CHARS
}

private fun nextNonBlank(code: String, from: Int): Int {
    var index = from
    while (index < code.length && code[index].isWhitespace()) {
        index++
    }
    return index
}

private val CONTINUATION_CHARS = setOf('.', '?', '&', '|', '+', '-', '*', '/')

/** Tipos dos `catch` em sequência a partir de `from`, na ordem do código; para no primeiro que não é `catch`. */
private fun catchTypesAfter(code: String, from: Int): List<String> {
    val types = mutableListOf<String>()
    var cursor = from
    while (true) {
        val clause = CATCH_CLAUSE.find(code.substring(cursor)) ?: break
        types += clause.groupValues[1].substringAfter(':', "").trim().removeSuffix("?").substringAfterLast('.')
        val close = closingBrace(code, cursor + clause.range.last) ?: break
        cursor = close + 1
    }
    return types
}

private fun closingBrace(code: String, open: Int): Int? {
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
