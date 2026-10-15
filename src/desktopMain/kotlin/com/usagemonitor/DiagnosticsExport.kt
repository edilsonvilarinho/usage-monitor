package com.usagemonitor

import com.usagemonitor.domain.entity.redactBreadcrumbIdentity
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Clock

/**
 * `usage-monitor --diagnostics`: empacota o diagnóstico **sem abrir janela** (issue #416).
 *
 * O relatório de bug é aberto pela interface; quando a interface é o defeito, o
 * relato chega com uma captura de tela e nada mais. Este caminho roda antes do
 * Compose, da instância única e de qualquer janela — funciona com o app aberto e
 * travado, ou que nem sobe.
 *
 * **Lista do que entra, não do que sai** ([EXPORTED_FILES]): um registro novo na
 * pasta não vaza por esquecimento. Ficam fora os recorders de créditos da
 * Anthropic e do Codex (corpo de resposta, ligados por variável de ambiente) e a
 * captura de tela da última queda (pode mostrar qualquer coisa da tela). Cada
 * linha de cada arquivo passa por [redactBreadcrumbIdentity] — os registros já
 * gravam redigido, mas o `linux-update.log` recebe a saída de ferramentas do
 * sistema, que pode trazer caminho do usuário.
 *
 * Junto vai `environment.txt`, medido na hora: sessão gráfica, suporte a janela
 * translúcida e a bandeja. São as perguntas da #416, respondidas sem janela.
 */
internal object DiagnosticsExport {
    const val ARGUMENT = "--diagnostics"

    /** O que entra no pacote, além dos arquivos girados do `app.log`. */
    val EXPORTED_FILES = listOf(
        "app.log",
        "app.1.log",
        "app.2.log",
        "startup.jsonl",
        "hud-window.jsonl",
        "breadcrumbs.jsonl",
        "linux-update.log",
        "pending-crash.json"
    )

    /**
     * Grava o zip em [outputDir] e devolve o arquivo. Lança se nem o zip puder ser
     * escrito — quem chama decide o código de saída.
     */
    fun export(
        diagnosticsDir: File = defaultDiagnosticsDir(),
        outputDir: File = File(System.getProperty("user.home")),
        environmentReport: () -> String = ::currentEnvironmentReport,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): File {
        val stamp = kotlin.time.Instant.fromEpochMilliseconds(nowMillis).toString()
            .replace(":", "")
            .substringBefore('.')
            .removeSuffix("Z")
        val zipFile = File(outputDir, "usage-monitor-diagnostics-$stamp.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("environment.txt"))
            zip.write(redactLines(runCatching(environmentReport).getOrElse { error -> "falha ao medir: ${error::class.simpleName}" }).toByteArray())
            zip.closeEntry()
            for (name in EXPORTED_FILES) {
                val file = File(diagnosticsDir, name)
                if (!file.isFile) {
                    continue
                }
                val content = runCatching { file.readText() }.getOrNull() ?: continue
                zip.putNextEntry(ZipEntry(name))
                zip.write(redactLines(content).toByteArray())
                zip.closeEntry()
            }
        }
        return zipFile
    }

    /**
     * Ponto de entrada do argumento: imprime onde o pacote ficou e devolve o código
     * de saída. Texto em inglês e ASCII: o console do Windows decodifica a saída
     * com a página de código dele, e acento saía corrompido (medido com
     * `gradlew.bat run --args=--diagnostics`).
     */
    fun run(): Int {
        return runCatching { export() }
            .fold(
                onSuccess = { zip ->
                    println("Diagnostics saved to: ${zip.absolutePath}")
                    println("Attach this file to the GitHub issue. It holds metadata only: no prompt, response or credential.")
                    0
                },
                onFailure = { error ->
                    System.err.println("Could not write the diagnostics package: ${error::class.simpleName}: ${error.message}")
                    1
                }
            )
    }

    private fun redactLines(content: String): String =
        content.lineSequence().joinToString("\n") { line -> redactBreadcrumbIdentity(line) }

    private fun defaultDiagnosticsDir(): File {
        val homeDir = System.getProperty("user.home")
            ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
        return File(homeDir, ".usage-monitor/diagnostics")
    }
}

/**
 * O ambiente medido na hora da exportação. `null` = não medido (a pergunta
 * lançou), nunca "medido e falso". Sem hostname nem usuário do sistema.
 */
internal fun currentEnvironmentReport(): String {
    val headless = runCatching { GraphicsEnvironment.isHeadless() }.getOrNull()
    val device = if (headless == false) {
        runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice }.getOrNull()
    } else {
        null
    }
    fun supports(kind: GraphicsDevice.WindowTranslucency): Boolean? =
        device?.let { screen -> runCatching { screen.isWindowTranslucencySupported(kind) }.getOrNull() }
    val lines = listOf(
        "version" to CURRENT_APP_VERSION,
        "os.name" to System.getProperty("os.name"),
        "os.version" to System.getProperty("os.version"),
        "os.arch" to System.getProperty("os.arch"),
        "java.version" to System.getProperty("java.version"),
        "java.vendor" to System.getProperty("java.vendor"),
        "XDG_SESSION_TYPE" to System.getenv("XDG_SESSION_TYPE"),
        "XDG_CURRENT_DESKTOP" to System.getenv("XDG_CURRENT_DESKTOP"),
        "WAYLAND_DISPLAY set" to (System.getenv("WAYLAND_DISPLAY") != null).toString(),
        "DISPLAY set" to (System.getenv("DISPLAY") != null).toString(),
        "skiko.renderApi" to System.getProperty("skiko.renderApi"),
        "awt.headless" to headless?.toString(),
        "translucency.perpixel" to supports(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)?.toString(),
        "translucency.uniform" to supports(GraphicsDevice.WindowTranslucency.TRANSLUCENT)?.toString(),
        "translucency.shaped" to supports(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)?.toString(),
        "tray.supported" to (if (headless == false) runCatching { SystemTray.isSupported() }.getOrNull()?.toString() else null),
        "alwaysOnTop.supported" to (if (headless == false) runCatching { java.awt.Toolkit.getDefaultToolkit().isAlwaysOnTopSupported }.getOrNull()?.toString() else null)
    )
    return lines.joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=${value ?: "null"}" }
}
