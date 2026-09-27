package com.usagemonitor.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * As regras de arquitetura e de tamanho, impostas sobre o código de produção
 * (issue #298). Documentação sozinha não impede regressão: `runUsageMonitor`
 * chegou a 2.400 linhas com o `CLAUDE.md` pedindo para extrair antes de crescer.
 *
 * - `presentation → domain ← data`: domain não conhece infraestrutura nem outra
 *   camada; data não conhece presentation; presentation não conhece data. Quem
 *   liga as três é a raiz de composição do desktop (`Main.kt` e vizinhos no
 *   pacote raiz), que fica fora destas regras.
 * - Arquivo de produção até [MAX_FILE_LINES] linhas; função até
 *   [MAX_FUNCTION_LINES].
 *
 * As listas de exceção **só encolhem**. O teto de cada item é o tamanho de hoje:
 * crescer falha, e diminuir também falha até o teto ser baixado — senão a folga
 * deixada por uma extração viraria espaço para crescer de novo. Item que caiu
 * abaixo do limite sai da lista. Arquivo ou função nova acima do limite não entra
 * na lista: é dividida.
 */
class ArchitectureRulesTest {

    private val sources = productionSources()

    @Test
    fun `domain nao importa infraestrutura nem outra camada`() {
        assertNoImports(
            layerPrefix = "com/usagemonitor/domain/",
            forbidden = listOf(
                "io.ktor.",
                "androidx.compose.",
                "kotlinx.serialization.",
                "java.io.",
                "com.usagemonitor.data.",
                "com.usagemonitor.presentation."
            )
        )
    }

    @Test
    fun `data nao importa presentation`() {
        assertNoImports(
            layerPrefix = "com/usagemonitor/data/",
            forbidden = listOf("com.usagemonitor.presentation.", "androidx.compose.")
        )
    }

    @Test
    fun `presentation nao importa data`() {
        assertNoImports(layerPrefix = "com/usagemonitor/presentation/", forbidden = listOf("com.usagemonitor.data."))
    }

    @Test
    fun `arquivo de producao respeita o limite de linhas`() {
        val measured = sources.associate { source -> source.path to source.lineCount }
        assertCeilings(what = "arquivo", limit = MAX_FILE_LINES, measured = measured, ceilings = FILE_CEILINGS)
    }

    @Test
    fun `funcao de producao respeita o limite de linhas`() {
        val measured = mutableMapOf<String, Int>()
        for (source in sources) {
            for (span in functionSpans(source.text)) {
                val key = "${source.path}::${span.name}"
                measured[key] = maxOf(measured[key] ?: 0, span.lineCount)
            }
        }
        assertCeilings(what = "função", limit = MAX_FUNCTION_LINES, measured = measured, ceilings = FUNCTION_CEILINGS)
    }

    private fun assertNoImports(layerPrefix: String, forbidden: List<String>) {
        val violations = sources
            .filter { source -> source.path.substringAfter("/kotlin/").startsWith(layerPrefix) }
            .flatMap { source ->
                source.imports
                    .filter { imported -> forbidden.any { prefix -> imported.startsWith(prefix) } }
                    .map { imported -> "${source.path}: import $imported" }
            }
        if (violations.isNotEmpty()) {
            fail("Dependência proibida para ${layerPrefix.trimEnd('/')}:\n" + violations.joinToString("\n"))
        }
    }

    private fun assertCeilings(what: String, limit: Int, measured: Map<String, Int>, ceilings: Map<String, Int>) {
        val problems = mutableListOf<String>()
        for ((key, lines) in measured.toSortedMap()) {
            val ceiling = ceilings[key]
            when {
                ceiling == null && lines > limit ->
                    problems += "$key: $lines linhas (limite $limit). Divida; exceção nova não entra na lista."
                ceiling != null && lines > ceiling ->
                    problems += "$key: cresceu para $lines linhas (teto congelado $ceiling). Extraia em vez de crescer."
                ceiling != null && lines <= limit ->
                    problems += "$key: $lines linhas, dentro do limite. Remova-o da lista de exceções."
                ceiling != null && lines < ceiling ->
                    problems += "$key: encolheu para $lines linhas. Baixe o teto de $ceiling para $lines."
            }
        }
        for (key in ceilings.keys - measured.keys) {
            problems += "$key: não existe mais. Remova-o da lista de exceções."
        }
        if (problems.isNotEmpty()) {
            fail("Limite de $what:\n" + problems.joinToString("\n"))
        }
    }

    @Test
    fun `o scanner mede o bloco inteiro e ignora chaves em string e comentario`() {
        val source = """
            fun small() {
                val text = "{ not a block }"
                // }
                println(text)
            }

            fun expression() = 42

            private fun root(args: Array<String>) = run {
                val braces = '{'
                /* } */
                if (args.isEmpty()) {
                    println(braces)
                }
            }
        """.trimIndent()

        val spans = functionSpans(source).associate { span -> span.name to span.lineCount }

        assertEquals(mapOf("small" to 5, "root" to 7), spans)
    }

    private companion object {
        const val MAX_FILE_LINES = 800
        const val MAX_FUNCTION_LINES = 300

        /** Tetos congelados em 2026-09-26. Só encolhem. */
        val FILE_CEILINGS: Map<String, Int> = mapOf(
            "src/commonMain/kotlin/com/usagemonitor/presentation/ui/components/SettingsDialogContent.kt" to 1853
        )

        /** Tetos congelados em 2026-09-26. Só encolhem. */
        val FUNCTION_CEILINGS: Map<String, Int> = emptyMap()
    }
}
