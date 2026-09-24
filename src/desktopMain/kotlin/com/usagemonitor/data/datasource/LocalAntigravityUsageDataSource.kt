package com.usagemonitor.data.datasource

import com.usagemonitor.domain.repository.AntigravityUsageFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Lê as cotas por `agy --output-format json --print /usage`, que o próprio CLI
 * responde sem abrir turno de modelo (doc oficial do modo headless; medição no
 * plano `docs/planos/integracoes-267-ajustes-execucao.md`).
 *
 * **O argumento chega por lista, nunca por shell.** Pelo Git Bash, `/usage` vira
 * `C:/Program Files/Git/usage` e vai ao modelo como prompt — foi medido. Pelo mesmo
 * motivo shims `.cmd`/`.bat`, que o Windows executa pelo `cmd.exe`, são recusados.
 *
 * Não há PTY: o pipe comum devolve o envelope completo, e a TUI interativa que a
 * versão anterior tentava dirigir nunca chegava a abrir o painel.
 */
internal class LocalAntigravityUsageDataSource(
    private val processStarter: AntigravityProcessStarter = SystemAntigravityProcessStarter,
    private val executableResolver: () -> File? = ::findAgyExecutable,
    private val workingDirectoryProvider: () -> File = ::antigravityWorkingDirectory,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val versionTimeoutMillis: Long = DEFAULT_VERSION_TIMEOUT_MILLIS
) : AntigravityUsageDataSource {

    private var verifiedVersionKey: String? = null

    override suspend fun readUsageJson(): String = withContext(Dispatchers.IO) {
        val executable = executableResolver()
            ?: throw IllegalStateException(AntigravityUsageFailureKind.CLI_NOT_INSTALLED.safeMessage)
        if (!isDirectExecutable(executable)) {
            throw IllegalStateException(AntigravityUsageFailureKind.UNSUPPORTED_LAUNCHER.safeMessage)
        }
        ensureVerifiedVersion(executable)

        val workingDirectory = workingDirectoryProvider()
        workingDirectory.mkdirs()
        val result = processStarter.run(
            command = listOf(executable.absolutePath) + USAGE_ARGUMENTS,
            workingDirectory = workingDirectory,
            timeoutMillis = timeoutMillis,
            maxOutputBytes = MAX_OUTPUT_BYTES
        )
        when {
            result.timedOut -> throw IllegalStateException(ANTIGRAVITY_CLI_TIMED_OUT)
            result.outputTooLarge -> throw IllegalStateException(ANTIGRAVITY_CLI_OUTPUT_TOO_LARGE)
            // Exit 1 é o que a doc descreve para autenticação ou entrada inválida;
            // o envelope JSON costuma vir mesmo assim e é ele que diz qual dos dois.
            result.stdout.isBlank() -> throw IllegalStateException(
                "$ANTIGRAVITY_CLI_EXITED (exit code ${result.exitCode})"
            )
        }
        result.stdout
    }

    /**
     * O portão de versão roda uma vez por executável: a chave é caminho + tamanho +
     * data de modificação, então uma atualização do CLI volta a ser conferida.
     */
    private fun ensureVerifiedVersion(executable: File) {
        val key = "${executable.absolutePath}|${executable.length()}|${executable.lastModified()}"
        if (verifiedVersionKey == key) return

        val result = processStarter.run(
            command = listOf(executable.absolutePath, "--version"),
            workingDirectory = workingDirectoryProvider().also(File::mkdirs),
            timeoutMillis = versionTimeoutMillis,
            maxOutputBytes = MAX_VERSION_OUTPUT_BYTES
        )
        val version = parseAgyVersion(result.stdout)
        if (result.timedOut || version == null || compareVersions(version, MIN_VERIFIED_VERSION) < 0) {
            throw IllegalStateException(AntigravityUsageFailureKind.UNVERIFIED_VERSION.safeMessage)
        }
        verifiedVersionKey = key
    }

    internal companion object {
        /** A versão contra a qual o envelope e a ausência de turno foram medidos. */
        val MIN_VERIFIED_VERSION = listOf(1, 2, 9)

        val USAGE_ARGUMENTS = listOf(
            "--sandbox",
            "--print-timeout", "30s",
            "--output-format", "json",
            "--print", "/usage"
        )

        const val DEFAULT_TIMEOUT_MILLIS = 45_000L
        const val DEFAULT_VERSION_TIMEOUT_MILLIS = 10_000L
        const val MAX_OUTPUT_BYTES = 64 * 1024
        const val MAX_VERSION_OUTPUT_BYTES = 1024
    }
}

internal data class AntigravityProcessResult(
    val exitCode: Int?,
    val stdout: String,
    val timedOut: Boolean,
    val outputTooLarge: Boolean
)

internal fun interface AntigravityProcessStarter {
    fun run(
        command: List<String>,
        workingDirectory: File,
        timeoutMillis: Long,
        maxOutputBytes: Int
    ): AntigravityProcessResult
}

/**
 * Processo real, sem shell. stdin fechado — o CLI não tem a quem perguntar nada —,
 * stderr drenado e descartado (nunca logado), e a árvore inteira morta no timeout.
 */
internal object SystemAntigravityProcessStarter : AntigravityProcessStarter {
    override fun run(
        command: List<String>,
        workingDirectory: File,
        timeoutMillis: Long,
        maxOutputBytes: Int
    ): AntigravityProcessResult {
        val process = ProcessBuilder(command)
            .directory(workingDirectory)
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .start()
        val stdout = BoundedCapture(maxOutputBytes)
        val stdoutReader = drain(process.inputStream, stdout)
        val stderrReader = drain(process.errorStream, null)

        val finished = try {
            process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            terminateProcessTree(process)
            Thread.currentThread().interrupt()
            throw interrupted
        }
        if (!finished) {
            terminateProcessTree(process)
        }
        stdoutReader.join(READER_JOIN_MILLIS)
        stderrReader.join(READER_JOIN_MILLIS)

        return AntigravityProcessResult(
            exitCode = if (finished) process.exitValue() else null,
            stdout = stdout.text(),
            timedOut = !finished,
            outputTooLarge = stdout.overflowed
        )
    }

    private fun drain(stream: InputStream, capture: BoundedCapture?): Thread =
        thread(name = "antigravity-usage-reader", isDaemon = true) {
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    capture?.append(buffer, count)
                }
            } catch (_: Throwable) {
                // O fechamento pelo timeout encerra a leitura; nada a relatar.
            }
        }

    private fun terminateProcessTree(process: Process) {
        runCatching {
            process.toHandle().descendants().toList().asReversed().forEach { child ->
                runCatching { child.destroyForcibly() }
            }
        }
        runCatching { process.destroyForcibly() }
        runCatching { process.waitFor(2, TimeUnit.SECONDS) }
    }

    private fun nullDevice(): File =
        File(if (isWindows()) "NUL" else "/dev/null")

    private const val READER_JOIN_MILLIS = 2_000L
}

/**
 * Guarda até [maxBytes] e marca o excedente. Saída acima do teto é **falha**, não
 * truncamento: JSON cortado ao meio não tem o que parsear, e a leitura parcial de
 * um envelope seria dado inventado.
 */
private class BoundedCapture(private val maxBytes: Int) {
    private val bytes = ByteArrayOutputStream()

    @Volatile
    var overflowed: Boolean = false
        private set

    @Synchronized
    fun append(chunk: ByteArray, count: Int) {
        val remaining = maxBytes - bytes.size()
        if (count > remaining) overflowed = true
        if (remaining > 0) bytes.write(chunk, 0, minOf(count, remaining))
    }

    @Synchronized
    fun text(): String = bytes.toString(StandardCharsets.UTF_8.name())
}

internal fun parseAgyVersion(output: String): List<Int>? {
    val match = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(output) ?: return null
    return match.groupValues.drop(1).map(String::toInt)
}

internal fun compareVersions(left: List<Int>, right: List<Int>): Int {
    for (index in 0 until maxOf(left.size, right.size)) {
        val difference = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
        if (difference != 0) return difference
    }
    return 0
}

/** Só executável que o SO inicia direto; `.cmd`/`.bat` passariam pelo `cmd.exe`. */
internal fun isDirectExecutable(executable: File): Boolean {
    val name = executable.name.lowercase()
    return !(name.endsWith(".cmd") || name.endsWith(".bat") || name.endsWith(".ps1"))
}

/**
 * `%LOCALAPPDATA%\agy\bin\agy.exe` primeiro — é onde o instalador oficial põe o CLI
 * no Windows, e onde o Codenotch também o procura —, depois o `PATH`.
 */
internal fun findAgyExecutable(
    environment: Map<String, String> = System.getenv(),
    windows: Boolean = isWindows()
): File? {
    if (windows) {
        environment["LOCALAPPDATA"]?.takeIf(String::isNotBlank)?.let { localAppData ->
            val installed = File(localAppData, "agy/bin/agy.exe")
            if (installed.isFile) return installed
        }
    }
    val executableName = if (windows) "agy.exe" else "agy"
    return environment["PATH"].orEmpty()
        .split(File.pathSeparator)
        .asSequence()
        .filter(String::isNotBlank)
        .map { directory -> File(directory, executableName) }
        .firstOrNull { file -> file.isFile && (windows || file.canExecute()) }
}

/**
 * Diretório vazio e dedicado: rodar em `user.home` convidaria o CLI a tratar a pasta
 * pessoal como projeto, com o que isso tiver de detecção ou pergunta de confiança.
 */
private fun antigravityWorkingDirectory(): File =
    File(System.getProperty("user.home") ?: ".", ".usage-monitor/antigravity-work").absoluteFile

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)

internal const val ANTIGRAVITY_CLI_TIMED_OUT = "Antigravity CLI /usage timed out"
internal const val ANTIGRAVITY_CLI_OUTPUT_TOO_LARGE = "Antigravity CLI /usage output exceeded the size limit"
internal const val ANTIGRAVITY_CLI_EXITED = "Antigravity CLI /usage exited without output"
