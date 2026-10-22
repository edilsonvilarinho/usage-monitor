package com.usagemonitor.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
 * - Recorte nativo de janela (`window.shape`) só nos arquivos de
 *   [NATIVE_WINDOW_SHAPE_OWNERS] (issue #340).
 * - Comparações de idioma inline (`== AppLanguage.PT`) não crescem: o teto
 *   [INLINE_LANGUAGE_CHECKS_CEILING] só desce (plano `boas-praticas-377`).
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

    /**
     * O motor do serviço sem tela (#440) roda com `java.awt.headless` num servidor que
     * pode não ter as bibliotecas gráficas: importar Compose ou Skia nele faria o
     * serviço carregar o que não existe ali. O bot desenha imagens por arquivos
     * próprios, carregados só quando desenha.
     */
    @Test
    fun `o motor sem tela nao importa Compose nem Skia`() {
        val violations = sources
            .filter { source -> source.path in HEADLESS_ENGINE_FILES }
            .flatMap { source ->
                source.imports
                    .filter { imported -> imported.startsWith("androidx.compose.") || imported.startsWith("org.jetbrains.skia") }
                    .map { imported -> "${source.path}: import $imported" }
            }
        assertEquals(HEADLESS_ENGINE_FILES, sources.map { source -> source.path }.filter { path -> path in HEADLESS_ENGINE_FILES }.toSet())
        if (violations.isNotEmpty()) {
            fail("O motor sem tela importou Compose ou Skia (#440):\n" + violations.joinToString("\n"))
        }
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

    /**
     * `Window.shape` foi medido só no Windows, e no elementary OS (X11) o recorte
     * da HUD não saía ao abrir: o balão aparecia cortado (issue #340). Recorte
     * nativo novo fora destes arquivos precisa decidir, com medição, em que
     * plataformas vale — a HUD decide por `hudUsesHitRegion`.
     */
    @Test
    fun `recorte nativo de janela so nos arquivos que ja o controlam`() {
        val violations = sources
            .filter { source -> source.path !in NATIVE_WINDOW_SHAPE_OWNERS }
            .filter { source -> NATIVE_WINDOW_SHAPE.containsMatchIn(stripCommentsAndStrings(source.text)) }
            .map { source -> source.path }
        if (violations.isNotEmpty()) {
            fail(
                "Recorte nativo de janela fora dos donos (decida as plataformas, veja a issue #340):\n" +
                    violations.joinToString("\n")
            )
        }
    }

    /**
     * A janela da HUD precisa **nascer** na `GraphicsConfiguration` com alfa do
     * monitor em que ela vive (issue #426): construí-la em qualquer outro lugar
     * a faz nascer na configuração padrão, que no X11 é de 24 bits, e janela de
     * 24 bits não tem canal alfa — vira um retângulo preto que engole clique.
     *
     * É por isso que a HUD desceu para o `SwingWindow` de baixo nível: o
     * `Window` de alto nível do Compose constrói a janela por dentro e não
     * deixa escolher a configuração. Quem construir uma `ComposeWindow` fora
     * daqui perde essa escolha em silêncio.
     */
    @Test
    fun `a janela do Compose so e construida por quem escolhe a configuracao`() {
        val violations = sources
            .filter { source -> source.path !in COMPOSE_WINDOW_OWNERS }
            .filter { source -> COMPOSE_WINDOW_CONSTRUCTION.containsMatchIn(stripCommentsAndStrings(source.text)) }
            .map { source -> source.path }
        if (violations.isNotEmpty()) {
            fail(
                "ComposeWindow construída fora de quem escolhe a GraphicsConfiguration (issue #426):\n" +
                    violations.joinToString("\n")
            )
        }
    }

    /**
     * Trocar o fundo de uma janela **não** devolve o alfa dela (issue #426).
     *
     * `java.awt.Window.setBackground` só troca a `GraphicsConfiguration` por
     * uma com alfa quando o alfa **antigo é 255 e o novo é menor**. Numa janela
     * já transparente, repetir `Color(0,0,0,0)` é um no-op — e foi assim que a
     * primeira tentativa de correção registrou `reaplicar=ok` sem ter feito
     * nada, deixando o log parecer saudável por três versões.
     *
     * Pior: o par que **funciona** (`null` e depois alfa 0) obriga o AWT a
     * recriar a janela nativa, e medido no Bazzite o Compose para de emitir
     * quadros depois disso — a barra fica correta e congelada. Não há uso
     * legítimo: a janela nasce certa e renasce quando precisa.
     */
    @Test
    fun `o fundo de uma janela nao e usado para consertar alfa`() {
        val violations = sources
            .filter { source -> WINDOW_BACKGROUND_ASSIGNMENT.containsMatchIn(stripCommentsAndStrings(source.text)) }
            .map { source -> source.path }
        if (violations.isNotEmpty()) {
            fail(
                "Atribuição de `background` em janela: não devolve o alfa e congela o desenho (issue #426):\n" +
                    violations.joinToString("\n")
            )
        }
    }

    /** Os dois padrões pegam a construção e a atribuição, e não o que se parece com elas. */
    @Test
    fun `os padroes da janela ignoram tipo e modifier`() {
        assertTrue(COMPOSE_WINDOW_CONSTRUCTION.containsMatchIn("ComposeWindow(graphicsConfiguration).apply {"))
        assertTrue(COMPOSE_WINDOW_CONSTRUCTION.containsMatchIn("val w = ComposeWindow()"))
        assertFalse(COMPOSE_WINDOW_CONSTRUCTION.containsMatchIn("(window as? ComposeWindow)?.renderApi"))
        assertFalse(COMPOSE_WINDOW_CONSTRUCTION.containsMatchIn("dispose = ComposeWindow::dispose"))
        assertFalse(COMPOSE_WINDOW_CONSTRUCTION.containsMatchIn("fun f(window: ComposeWindow) {"))

        assertTrue(WINDOW_BACKGROUND_ASSIGNMENT.containsMatchIn("window.background = Color(0, 0, 0, 0)"))
        assertTrue(WINDOW_BACKGROUND_ASSIGNMENT.containsMatchIn("frame.background = null"))
        assertFalse(WINDOW_BACKGROUND_ASSIGNMENT.containsMatchIn("Modifier.background(AppSurfaces.card)"))
        assertFalse(WINDOW_BACKGROUND_ASSIGNMENT.containsMatchIn("if (window.background == null) return"))
    }

    /**
     * A raiz `~/.usage-monitor` tem dono único (`AppDataDirectory`, issue #419).
     * Caminho montado à mão fora dele ignora o `--user-data-dir` em silêncio: a
     * execução isolada passaria a gravar na pasta do usuário. Texto de ajuda que
     * cita `~/.usage-monitor/…` não casa, porque o literal não começa na raiz.
     */
    @Test
    fun `a raiz de dados so e montada pelo dono`() {
        val violations = sources
            .filter { source -> source.path != APP_DATA_DIRECTORY_OWNER }
            .filter { source -> APP_DATA_DIRECTORY_LITERAL.containsMatchIn(source.text) }
            .map { source -> source.path }
        if (violations.isNotEmpty()) {
            fail("Caminho em `.usage-monitor` montado fora do `AppDataDirectory` (#419):\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun `o padrao da raiz de dados pega o caminho e ignora o texto de ajuda e o marcador`() {
        assertTrue(APP_DATA_DIRECTORY_LITERAL.containsMatchIn("""File(home, ".usage-monitor/api-keys.json")"""))
        assertTrue(APP_DATA_DIRECTORY_LITERAL.containsMatchIn("""File(home, ".usage-monitor")"""))
        assertFalse(APP_DATA_DIRECTORY_LITERAL.containsMatchIn(""""salva em ~/.usage-monitor/api-keys.json""""))
        assertFalse(APP_DATA_DIRECTORY_LITERAL.containsMatchIn("""const val MARKER = ".usage-monitor-managed""""))
    }

    @Test
    fun `o padrao do recorte pega a chamada nativa e ignora o argumento do Compose`() {
        assertTrue(NATIVE_WINDOW_SHAPE.containsMatchIn("window.shape = null"))
        assertTrue(NATIVE_WINDOW_SHAPE.containsMatchIn("frame.setShape(rect)"))
        assertFalse(NATIVE_WINDOW_SHAPE.containsMatchIn("Surface(shape = RoundedCornerShape(6.dp))"))
        assertFalse(NATIVE_WINDOW_SHAPE.containsMatchIn("if (window.shape == null) return"))
    }

    /**
     * Texto PT/EN repetido em cada tela divergiu (nove traduções inconsistentes
     * na auditoria do plano `boas-praticas-377`). Texto novo passa por
     * `localized(language, pt, en)` ou por um objeto de rótulos existente
     * (`CommonLabels`, `CliSessionsLabels`…), nunca por comparação nova. A catraca
     * vale para o projeto inteiro: arquivo tocado que migra baixa o teto.
     */
    @Test
    fun `comparacao de idioma inline nao cresce`() {
        val count = sources.sumOf { source ->
            INLINE_LANGUAGE_CHECK.findAll(stripCommentsAndStrings(source.text)).count()
        }
        when {
            count > INLINE_LANGUAGE_CHECKS_CEILING -> fail(
                "Comparações `== AppLanguage.PT` em produção: $count (teto $INLINE_LANGUAGE_CHECKS_CEILING). " +
                    "Use `localized(language, pt, en)` ou um objeto de rótulos existente."
            )
            count < INLINE_LANGUAGE_CHECKS_CEILING -> fail(
                "Comparações `== AppLanguage.PT` caíram para $count. Baixe o teto de $INLINE_LANGUAGE_CHECKS_CEILING para $count."
            )
        }
    }

    /**
     * O `toString` gerado de uma `data class` imprime todo campo — token, chave de
     * API, senha — em log, relatório de bug e mensagem de exceção. Tipo com campo de
     * segredo sobrescreve o `toString` com `redactedSecret` ou deixa de ser `data class`
     * (precedente: `CursorSessionCredentials`). Campo de segredo com nome que o padrão
     * não reconhece (as chaves por fonte de `ApiKeySettings`) depende de revisão.
     */
    @Test
    fun `data class com campo de segredo redige o toString`() {
        val violations = sources.flatMap { source ->
            dataClassDeclarations(source.text)
                .filter { declaration -> declaration.properties.any { property -> SECRET_PROPERTY.matches(property) } }
                .filter { declaration -> !declaration.body.contains("override fun toString") }
                .map { declaration -> "${source.path}: ${declaration.name}" }
        }
        if (violations.isNotEmpty()) {
            fail("data class com segredo e toString gerado (use `redactedSecret`):\n" + violations.joinToString("\n"))
        }
    }

    @Test
    fun `o scanner de data class le propriedades e corpo`() {
        val source = """
            data class Plain(val accessToken: String, val count: Int)

            data class Safe(
                val apiKey: String = "{",
                var inputTokens: Long
            ) : Marker {
                override fun toString(): String = "Safe"
            }
        """.trimIndent()

        val declarations = dataClassDeclarations(source).associateBy { declaration -> declaration.name }

        assertEquals(listOf("accessToken", "count"), declarations.getValue("Plain").properties)
        assertEquals("", declarations.getValue("Plain").body)
        assertTrue(declarations.getValue("Safe").body.contains("override fun toString"))
        assertTrue(SECRET_PROPERTY.matches("accessToken"))
        assertTrue(SECRET_PROPERTY.matches("refresh_token"))
        assertFalse(SECRET_PROPERTY.matches("inputTokens"))
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

        /** `.shape = ` e `setShape(`; o argumento nomeado `shape =` do Compose não tem ponto. */
        val NATIVE_WINDOW_SHAPE = Regex("""\.shape\s*=(?!=)|setShape\s*\(""")

        /** Quem hoje chama `window.shape`: a HUD e os cantos arredondados das janelas. */
        /** O motor do serviço sem tela e o que só ele usa (#440). */
        val HEADLESS_ENGINE_FILES = setOf(
            "src/desktopMain/kotlin/com/usagemonitor/CollectorEngine.kt"
        )

        val NATIVE_WINDOW_SHAPE_OWNERS = setOf(
            "src/desktopMain/kotlin/com/usagemonitor/HudWindowEffects.kt",
            "src/desktopMain/kotlin/com/usagemonitor/presentation/ui/DesktopWindowFrame.kt"
        )

        /** `ComposeWindow(`: a construção, não o tipo num parâmetro nem a referência de método. */
        val COMPOSE_WINDOW_CONSTRUCTION = Regex("""\bComposeWindow\s*\(""")

        /** Quem escolhe a `GraphicsConfiguration` da janela da HUD. */
        val COMPOSE_WINDOW_OWNERS = setOf(
            "src/desktopMain/kotlin/com/usagemonitor/HudWindowFrame.kt"
        )

        /** `.background = `; o `Modifier.background(` do Compose é chamada, não atribuição. */
        val WINDOW_BACKGROUND_ASSIGNMENT = Regex("""\.background\s*=(?!=)""")

        val INLINE_LANGUAGE_CHECK = Regex("""==\s*AppLanguage\.PT\b""")

        const val APP_DATA_DIRECTORY_OWNER = "src/desktopMain/kotlin/com/usagemonitor/data/AppDataDirectory.kt"

        /** Literal que **começa** na raiz: `".usage-monitor/…"` ou `".usage-monitor"`. */
        val APP_DATA_DIRECTORY_LITERAL = Regex(""""\.usage-monitor(/|")""")

        /** Nome de campo que carrega segredo: `*Token`, `*_token`, `*ApiKey`, `password`, `*Secret`, `cookie`, `capSid`. */
        val SECRET_PROPERTY = Regex("""(?i)\w*token|\w*apikey|password|\w*secret|cookie|capsid""")

        /** Medido em 2026-10-14, depois de B1/B2 do plano `boas-praticas-377`. Só desce. */
        const val INLINE_LANGUAGE_CHECKS_CEILING = 710

        /** Tetos congelados em 2026-09-26. Só encolhem. */
        val FILE_CEILINGS: Map<String, Int> = emptyMap()

        /** Tetos congelados em 2026-09-26. Só encolhem. */
        val FUNCTION_CEILINGS: Map<String, Int> = emptyMap()
    }
}
