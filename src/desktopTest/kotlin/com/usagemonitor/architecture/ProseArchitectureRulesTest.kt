package com.usagemonitor.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regras do `CLAUDE.md` que um teste afirma (issue #646). Cada padrão tem um
 * exemplo que reprova e um que passa; a falha diz o que usar no lugar.
 */
class ProseArchitectureRulesTest {

    @Test
    fun `dialogo interno e AppDialog e nunca AlertDialog`() {
        failIfAny(
            "AlertDialog fora do padrão (use AppDialog, o diálogo interno de presentation/ui):",
            violatingPaths { _, code -> usesAlertDialog(code) }
        )
    }

    @Test
    fun `o padrao do AlertDialog pega o uso e ignora o comentario`() {
        assertTrue(usesAlertDialog(codeOf("AlertDialog(onDismissRequest = {})")))
        assertFalse(usesAlertDialog(codeOf("AppDialog(onDismissRequest = {})")))
        assertFalse(usesAlertDialog(codeOf("// nunca AlertDialog")))
    }

    @Test
    fun `raio de borda tem teto 10 e dp literal so no tema`() {
        failIfAny(
            "RoundedCornerShape com dp literal fora de AppTheme.kt, ou raio acima de 10 (use um token de AppTheme):",
            violatingPaths { path, code -> radiusLiteralViolation(path, code) }
        )
    }

    @Test
    fun `o padrao do raio pega literal fora do tema e acima do teto`() {
        assertTrue(radiusLiteralViolation("src/x/AppControls.kt", "Surface(shape = RoundedCornerShape(6.dp))"))
        assertTrue(radiusLiteralViolation("src/x/AppTheme.kt", "val big = RoundedCornerShape(12.dp)"))
        assertFalse(radiusLiteralViolation("src/x/AppTheme.kt", "val small = RoundedCornerShape(8.dp)"))
        assertFalse(radiusLiteralViolation("src/x/AppControls.kt", "RoundedCornerShape(SWITCH_HEIGHT / 2)"))
    }

    @Test
    fun `fonte do classpath e nunca composeResources`() {
        failIfAny(
            "composeResources proibido (carregue a fonte do classpath, como AppFonts.kt):",
            violatingPaths { _, code -> usesComposeResources(code) }
        )
    }

    @Test
    fun `o padrao do composeResources pega o uso e ignora a fonte do classpath`() {
        assertTrue(usesComposeResources("import org.jetbrains.compose.resources.painterResource"))
        assertFalse(usesComposeResources(codeOf("// Não passa por `composeResources`")))
        assertFalse(usesComposeResources(codeOf("val dir = \"compose.application.resources.dir\"")))
    }

    @Test
    fun `escopo tem dono e nunca GlobalScope`() {
        failIfAny(
            "GlobalScope proibido (use um escopo com dono e SupervisorJob):",
            violatingPaths { _, code -> usesGlobalScope(code) }
        )
    }

    @Test
    fun `o padrao do GlobalScope pega o uso e ignora o escopo com dono`() {
        assertTrue(usesGlobalScope("GlobalScope.launch { work() }"))
        assertFalse(usesGlobalScope("val scope = CoroutineScope(SupervisorJob())"))
    }

    @Test
    fun `fuso de reset nunca vem do sistema`() {
        failIfAny(
            "currentSystemDefault() proibido (use TimeZone.of(\"America/Sao_Paulo\"), o fuso de reset):",
            violatingPaths { _, code -> usesCurrentSystemDefault(code) }
        )
    }

    @Test
    fun `o padrao do fuso pega o sistema e ignora o fuso fixo`() {
        assertTrue(usesCurrentSystemDefault("val zone = TimeZone.currentSystemDefault()"))
        assertFalse(usesCurrentSystemDefault("val zone = TimeZone.of(SAO_PAULO)"))
    }

    @Test
    fun `confianca no certificado so no SystemTrustStore`() {
        failIfAny(
            "X509TrustManager/HostnameVerifier fora de SystemTrustStore.kt (nunca confiar mesmo assim no certificado):",
            violatingPaths { path, code -> File(path).name != "SystemTrustStore.kt" && usesTrustOverride(code) }
        )
    }

    @Test
    fun `o padrao de confianca pega o gerenciador e ignora o uso comum`() {
        assertTrue(usesTrustOverride("object : X509TrustManager { }"))
        assertTrue(usesTrustOverride("val verifier = HostnameVerifier { _, _ -> true }"))
        assertFalse(usesTrustOverride("val store = SystemTrustStore.load()"))
    }

    @Test
    fun `animacao infinita so nos arquivos que ja a usam atras da politica`() {
        failIfAny(
            "Animação infinita fora da lista (só atrás de AppMotionPolicy.continuous; arquivo novo entra na lista deste teste):",
            violatingPaths { path, code -> !isOwner(path, INFINITE_ANIMATION_OWNERS) && usesInfiniteAnimation(code) }
        )
    }

    @Test
    fun `o padrao da animacao infinita pega o transition e ignora a tween`() {
        assertTrue(usesInfiniteAnimation("val t = rememberInfiniteTransition()"))
        assertFalse(usesInfiniteAnimation("animateFloatAsState(targetValue = 1f, animationSpec = tween(300))"))
    }

    @Test
    fun `chave de fonte nunca vem de variavel de ambiente`() {
        val paths = commentFreeByPath
            .filter { (_, code) -> readsKeyFromEnvironment(code) }
            .keys
            .toList()
        failIfAny(
            "getenv com nome de fonte que tem chave (use ~/.usage-monitor/api-keys.json pelo LocalApiKeyDataSource):",
            paths
        )
    }

    @Test
    fun `o padrao do getenv pega a chave de fonte e ignora a variavel comum e o comentario`() {
        assertTrue(readsKeyFromEnvironment("System.getenv(\"MINIMAX_API_KEY\")"))
        assertTrue(readsKeyFromEnvironment("System.getenv(\"OPENROUTER_API_KEY\")"))
        assertFalse(readsKeyFromEnvironment("System.getenv(\"XDG_CONFIG_HOME\")"))
        assertFalse(readsKeyFromEnvironment(commentFree("// System.getenv(\"DEEPSEEK_API_KEY\")")))
    }

    @Test
    fun `monitor nunca sai de defaultScreenDevice fora dos tres donos`() {
        failIfAny(
            "defaultScreenDevice fora de ScreenLocator, DiagnosticsExport e WindowOpacityPreferences (use workAreaForPosition):",
            violatingPaths { path, code -> !isOwner(path, DEFAULT_SCREEN_OWNERS) && usesDefaultScreenDevice(code) }
        )
    }

    @Test
    fun `o padrao do defaultScreenDevice pega a leitura e ignora o nome em outro identificador`() {
        assertTrue(usesDefaultScreenDevice("val primary = environment.defaultScreenDevice"))
        assertFalse(usesDefaultScreenDevice("val maximumWindowBounds = environment.maximumWindowBounds"))
    }

    @Test
    fun `identificador de CLAUDE md existe no codigo`() {
        val missing = missingIdentifiers(claudeIdentifiers(claudeMarkdown), corpus)
        failIfAny("CLAUDE.md cita nome que não existe no código (remova a menção ou troque pelo nome atual):", missing)
    }

    @Test
    fun `identificador ausente do codigo reprova e o presente passa`() {
        assertEquals(listOf("NaoExiste"), missingIdentifiers(listOf("NaoExiste", "AppDialog"), listOf("class AppDialog")))
    }

    @Test
    fun `extrator pega so nome com cara de identificador`() {
        assertEquals(
            listOf("AppDialog", "runCatching"),
            claudeIdentifiers("use `AppDialog`, `gradlew.bat`, `runCatching()` e `-Pcoverage`")
        )
    }

    @Test
    fun `link relativo de CLAUDE md aponta para arquivo que existe`() {
        val broken = brokenRelativeLinks("[a](docs/ok.md) [b](docs/nao.md) [c](docs/ok.md#ancora) [d](https://x.dev/y) [e](#topo)") { path ->
            path == "docs/ok.md"
        }
        assertEquals(listOf("docs/nao.md"), broken)
    }

    @Test
    fun `link relativo quebrado de CLAUDE md e reprovado`() {
        val broken = brokenRelativeLinks(claudeMarkdown) { path -> File(path).exists() }
        failIfAny("CLAUDE.md tem link relativo quebrado (corrija o caminho ou remova o link):", broken)
    }

    private fun failIfAny(message: String, found: List<String>) {
        if (found.isNotEmpty()) {
            fail(message + "\n" + found.joinToString("\n"))
        }
    }

    /** Usa o texto sem comentário e sem string já calculado uma vez no companion. */
    private fun violatingPaths(rule: (path: String, code: String) -> Boolean): List<String> =
        codeByPath
            .filter { (path, code) -> rule(path, code) }
            .keys
            .toList()

    private fun codeOf(text: String): String = stripCommentsAndStrings(text)

    /** Normaliza o separador para o caminho casar com a lista, no Windows ou no POSIX. */
    private fun isOwner(path: String, owners: Set<String>): Boolean {
        val normalized = path.replace("\\", "/")
        return owners.any { owner -> normalized.endsWith(owner) }
    }

    private fun usesAlertDialog(code: String): Boolean = ALERT_DIALOG.containsMatchIn(code)

    /** Raio com dp literal fora de AppTheme.kt reprova; dentro dele, reprova acima de 10. */
    private fun radiusLiteralViolation(path: String, code: String): Boolean =
        RADIUS_LITERAL.findAll(code).any { match ->
            val value = match.groupValues[1].toDouble()
            File(path).name != "AppTheme.kt" || value > RADIUS_CEILING_DP
        }

    private fun usesComposeResources(code: String): Boolean = COMPOSE_RESOURCES.containsMatchIn(code)

    private fun usesGlobalScope(code: String): Boolean = GLOBAL_SCOPE.containsMatchIn(code)

    private fun usesCurrentSystemDefault(code: String): Boolean = CURRENT_SYSTEM_DEFAULT.containsMatchIn(code)

    private fun usesTrustOverride(code: String): Boolean = TRUST_OVERRIDE.containsMatchIn(code)

    private fun usesInfiniteAnimation(code: String): Boolean = INFINITE_ANIMATION.containsMatchIn(code)

    private fun readsKeyFromEnvironment(commentFreeCode: String): Boolean = ENV_KEY_SOURCE.containsMatchIn(commentFreeCode)

    private fun usesDefaultScreenDevice(code: String): Boolean = DEFAULT_SCREEN.containsMatchIn(code)

    /**
     * Leitura única por JVM: o JUnit cria uma instância por teste, então fontes, texto
     * e corpus ficam no companion, e os testes de padrão não leem disco.
     */
    private companion object {
        val sources by lazy { productionSources() }

        /** Código de produção sem comentário nem string, uma passada por arquivo. */
        val codeByPath: Map<String, String> by lazy {
            sources.associate { source -> source.path to stripCommentsAndStrings(source.text) }
        }

        /** Código sem comentário, com strings: o nome da variável de ambiente está num literal. */
        val commentFreeByPath: Map<String, String> by lazy {
            sources.associate { source -> source.path to commentFree(source.text) }
        }

        /** Corpus de R10: texto e configuração dos caminhos do CLAUDE.md, fora de docs e de worktrees. */
        val corpus: List<String> by lazy { codeCorpus(File(".")) }

        val claudeMarkdown: String by lazy { File("CLAUDE.md").readText() }

        val ALERT_DIALOG = Regex("""\bAlertDialog\b""")
        val RADIUS_LITERAL = Regex("""RoundedCornerShape\s*\([^)]*?(\d+(?:\.\d+)?)\.dp""")
        const val RADIUS_CEILING_DP = 10.0
        val COMPOSE_RESOURCES = Regex("""org\.jetbrains\.compose\.resources|\bcomposeResources\b""")
        val GLOBAL_SCOPE = Regex("""\bGlobalScope\b""")
        val CURRENT_SYSTEM_DEFAULT = Regex("""\bcurrentSystemDefault\s*\(""")
        val TRUST_OVERRIDE = Regex("""\b(X509TrustManager|HostnameVerifier)\b""")
        val INFINITE_ANIMATION = Regex("""\b(rememberInfiniteTransition|InfiniteTransition|infiniteRepeatable|InfiniteRepeatable)""")
        val ENV_KEY_SOURCE = Regex("""getenv\s*\([^)]*(?i:minimax|deepseek|opencode|openrouter)""")
        val DEFAULT_SCREEN = Regex("""\bdefaultScreenDevice\b""")
        val CORPUS_PREFIXES = listOf("src/", "server/src/", "tools/", ".claude/", ".github/")
        val CONFIG_EXTENSIONS = listOf(".kts", ".properties", ".toml")
        val TEXT_EXTENSIONS = listOf(
            ".kt", ".java", ".ts", ".tsx", ".js", ".mjs", ".cjs", ".json", ".yml", ".yaml", ".xml",
            ".sh", ".ps1", ".bat", ".html", ".css", ".gradle"
        )
        val EXCLUDED_DIRS = setOf(".git", ".gradle", "node_modules", "build", "dist", "coverage", "worktrees")

        val INFINITE_ANIMATION_OWNERS = setOf(
            "src/commonMain/kotlin/com/usagemonitor/presentation/ui/components/ApiUsageCard.kt",
            "src/commonMain/kotlin/com/usagemonitor/presentation/ui/components/ApiUsageCardHeader.kt",
            "src/commonMain/kotlin/com/usagemonitor/presentation/ui/components/AppGargantuaRing.kt",
            "src/commonMain/kotlin/com/usagemonitor/presentation/ui/components/AppUsageRing.kt",
            "src/commonMain/kotlin/com/usagemonitor/presentation/ui/components/GargantuaHorizonBody.kt",
            "src/commonMain/kotlin/com/usagemonitor/presentation/ui/components/SessionPulseIndicator.kt"
        )
        val DEFAULT_SCREEN_OWNERS = setOf(
            "src/desktopMain/kotlin/com/usagemonitor/ScreenLocator.kt",
            "src/desktopMain/kotlin/com/usagemonitor/DiagnosticsExport.kt",
            "src/desktopMain/kotlin/com/usagemonitor/WindowOpacityPreferences.kt"
        )

        /** Remove comentários e mantém strings: o nome da variável de ambiente está num literal. */
        fun commentFree(source: String): String {
            val out = StringBuilder(source.length)
            var index = 0
            while (index < source.length) {
                if (source.startsWith("//", index)) {
                    val found = source.indexOf("\n", index)
                    index = if (found < 0) source.length else found
                } else if (source.startsWith("/*", index)) {
                    val close = source.indexOf("*/", index + 2)
                    val end = if (close < 0) source.length else close + 2
                    repeat(source.substring(index, end).split("\n").size - 1) { out.append("\n") }
                    index = end
                } else if (source.startsWith("\"", index)) {
                    var cursor = index + 1
                    while (cursor < source.length && !source.startsWith("\"", cursor) && !source.startsWith("\n", cursor)) {
                        cursor += if (source.startsWith("\\", cursor)) 2 else 1
                    }
                    val end = minOf(cursor + 1, source.length)
                    out.append(source, index, end)
                    index = end
                } else {
                    out.append(source[index])
                    index++
                }
            }
            return out.toString()
        }

        fun codeCorpus(root: File): List<String> {
            val base = root.canonicalFile
            return base.walkTopDown()
                .onEnter { dir -> dir.name !in EXCLUDED_DIRS }
                .filter { file -> file.isFile && isCorpusFile(file.canonicalFile.relativeTo(base).invariantSeparatorsPath) }
                .map { file -> file.readText() }
                .toList()
        }

        /** Config em qualquer pasta, ou texto dentro dos prefixos do CLAUDE.md. */
        fun isCorpusFile(relative: String): Boolean {
            val configFile = CONFIG_EXTENSIONS.any { ext -> relative.endsWith(ext) }
            val textInScope = CORPUS_PREFIXES.any { prefix -> relative.startsWith(prefix) } &&
                TEXT_EXTENSIONS.any { ext -> relative.endsWith(ext) }
            return configFile || textInScope
        }
    }
}

/** Nomes entre crases do Markdown com cara de identificador (sem parênteses), sem repetição. */
internal fun claudeIdentifiers(markdown: String): List<String> =
    Regex("`([^`\\n]+)`").findAll(markdown)
        .map { match -> match.groupValues[1] }
        .filter { name -> Regex("^[A-Za-z_][A-Za-z0-9_]{4,}(\\(\\))?$").matches(name) }
        .map { name -> name.removeSuffix("()") }
        .distinct()
        .toList()

/** Identificadores que não aparecem como palavra em nenhum texto do corpus. */
internal fun missingIdentifiers(identifiers: List<String>, corpus: List<String>): List<String> {
    val tokens = corpus.flatMap { text -> Regex("\\w+").findAll(text).map { match -> match.value } }.toSet()
    return identifiers.filter { identifier -> identifier !in tokens }
}

/** Alvos relativos de link que `exists` não encontra; URL, âncora pura e mailto são ignorados. */
internal fun brokenRelativeLinks(markdown: String, exists: (String) -> Boolean): List<String> =
    Regex("""\]\(([^)\s]+)\)""").findAll(markdown)
        .map { match -> match.groupValues[1] }
        .filter { target -> !target.contains("://") && !target.startsWith("#") && !target.startsWith("mailto:") }
        .map { target -> target.substringBefore("#") }
        .filter { path -> path.isNotEmpty() && !exists(path) }
        .toList()
