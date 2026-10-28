package com.usagemonitor

import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.redactBreadcrumbIdentity
import com.usagemonitor.presentation.ui.localized
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
        LINUX_UPDATE_LOG,
        "pending-crash.json"
    )

    /**
     * Não segue `--user-data-dir` (#419): o updater do Linux grava sempre na raiz
     * padrão, que é a da instalação.
     */
    private const val LINUX_UPDATE_LOG = "linux-update.log"

    /**
     * Grava o zip em [outputDir] e devolve o arquivo. Lança se nem o zip puder ser
     * escrito — quem chama decide o código de saída.
     *
     * [installationDiagnosticsDir] só difere de [diagnosticsDir] com `--user-data-dir`.
     */
    fun export(
        diagnosticsDir: File = AppDataDirectory.file("diagnostics"),
        installationDiagnosticsDir: File = diagnosticsDir,
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
                val directory = if (name == LINUX_UPDATE_LOG) installationDiagnosticsDir else diagnosticsDir
                val file = File(directory, name)
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
     * de saída. No idioma do app (#439); quem chama escreve por [out] e [err], que tiram
     * o acento quando o console não é UTF-8 — o console do Windows decodifica com a
     * página de código dele, e acento saía corrompido (medido no #418).
     */
    fun run(
        language: AppLanguage,
        out: (String) -> Unit,
        err: (String) -> Unit,
        exportPackage: () -> File = { export(installationDiagnosticsDir = AppDataDirectory.installationFile("diagnostics")) }
    ): Int {
        return runCatching { exportPackage() }
            .fold(
                onSuccess = { zip ->
                    out(localized(language, "Diagnóstico salvo em: ${zip.absolutePath}", "Diagnostics saved to: ${zip.absolutePath}"))
                    out(
                        localized(
                            language,
                            "Anexe este arquivo à issue do GitHub. Só tem metadados: nenhum prompt, resposta ou credencial.",
                            "Attach this file to the GitHub issue. It holds metadata only: no prompt, response or credential."
                        )
                    )
                    0
                },
                onFailure = { error ->
                    val reason = "${error::class.simpleName}: ${error.message}"
                    err(localized(language, "Não foi possível gravar o pacote de diagnóstico: $reason", "Could not write the diagnostics package: $reason"))
                    1
                }
            )
    }

    private fun redactLines(content: String): String =
        content.lineSequence().joinToString("\n") { line -> redactBreadcrumbIdentity(line) }
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
    ) + screenEnvironmentLines(if (headless == false) readScreenEnvironment() else null)
    return lines.joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=${value ?: "null"}" }
}

/**
 * Um monitor no `environment.txt` (issue #430). O pacote da #416 dizia que havia
 * dois monitores só porque o `app.log` citava `:0.0` e `:0.1`: os limites tiveram
 * de ser deduzidos da posição em que o COSMIC centralizou a janela. `null` = não
 * medido.
 */
internal data class ScreenEnvironmentEntry(
    val id: String?,
    val bounds: java.awt.Rectangle?,
    val insets: java.awt.Insets?,
    /** Bits da configuração **padrão**: é a que o AWT dá à janela que cruza para este monitor. */
    val defaultPixelSize: Int?,
    /** Se o monitor oferece alguma configuração com alfa — a que a HUD precisa para nascer. */
    val translucentConfiguration: Boolean?
)

/** As linhas dos monitores, o padrão primeiro. `null` = sem sessão gráfica, ou a consulta lançou. */
internal fun screenEnvironmentLines(entries: List<ScreenEnvironmentEntry>?): List<Pair<String, String?>> {
    if (entries == null) {
        return listOf("screens" to null)
    }
    val count = listOf("screens" to entries.size.toString())
    return count + entries.mapIndexed { index, entry -> "screen.$index" to screenEnvironmentText(entry) }
}

internal fun screenEnvironmentText(entry: ScreenEnvironmentEntry): String {
    val bounds = entry.bounds?.let { r -> "${r.x},${r.y} ${r.width}x${r.height}" } ?: "?"
    val insets = entry.insets?.let { i -> "t${i.top},l${i.left},b${i.bottom},r${i.right}" } ?: "?"
    return "id=${entry.id ?: "?"} bounds=$bounds insets=$insets " +
        "default=${entry.defaultPixelSize ?: "?"}bpp alpha=${entry.translucentConfiguration ?: "?"}"
}

private fun readScreenEnvironment(): List<ScreenEnvironmentEntry>? = runCatching {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val primary = environment.defaultScreenDevice
    environment.screenDevices
        .sortedBy { device -> if (device == primary) 0 else 1 }
        .map { device ->
            val configuration = runCatching { device.defaultConfiguration }.getOrNull()
            ScreenEnvironmentEntry(
                id = runCatching { device.getIDstring() }.getOrNull(),
                bounds = runCatching { configuration?.bounds }.getOrNull(),
                insets = configuration?.let { gc -> runCatching { java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc) }.getOrNull() },
                defaultPixelSize = runCatching { configuration?.colorModel?.pixelSize }.getOrNull(),
                translucentConfiguration = runCatching { device.configurations.any { gc -> gc.isTranslucencyCapable } }.getOrNull()
            )
        }
}.getOrNull()
