package com.usagemonitor.data.datasource

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.usagemonitor.data.mapper.AntigravityUsageParser
import com.usagemonitor.domain.entity.ReportedModelQuota
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import kotlin.concurrent.thread

internal interface AntigravityPtySession {
    fun sendUsageCommand()
    fun readOutput(): String
    fun isAlive(): Boolean
    fun exitCodeOrNull(): Int?
    fun terminateProcessTree()
    fun outputCaptureSummary(): String? = null
}

internal data class AntigravityPtyOutputSummary(
    val capturedBytes: Int,
    val receivedBytes: Long
) {
    val wasTruncated: Boolean get() = receivedBytes > capturedBytes
}

internal fun interface AntigravityPtySessionFactory {
    fun start(executable: File, workingDirectory: File): AntigravityPtySession
}

internal fun interface AntigravityUsageCommandRunner {
    suspend fun readUsagePanel(): String
}

/** Lê o painel oficial /usage sem prompt de modelo, login automatizado ou RPC da IDE. */
internal class LocalAntigravityUsageDataSource(
    private val runner: AntigravityUsageCommandRunner = PtyAntigravityUsageCommandRunner()
) : AntigravityUsageDataSource {
    override suspend fun readUsage(): List<ReportedModelQuota> {
        return AntigravityUsageParser.parse(runner.readUsagePanel())
    }
}

internal class PtyAntigravityUsageCommandRunner(
    private val sessionFactory: AntigravityPtySessionFactory = NativeAntigravityPtySessionFactory(),
    private val executableResolver: () -> File? = ::findAgyExecutable,
    private val workingDirectoryProvider: () -> File = { File(System.getProperty("user.home") ?: ".").absoluteFile },
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val startupDelayMillis: Long = DEFAULT_STARTUP_DELAY_MILLIS,
    private val settleDelayMillis: Long = 350L
) : AntigravityUsageCommandRunner {
    override suspend fun readUsagePanel(): String = withContext(Dispatchers.IO) {
        val executable = executableResolver()
            ?: throw IllegalStateException("Antigravity CLI is not installed or is not on PATH")
        val home = workingDirectoryProvider()
        val session = sessionFactory.start(executable, home)
        try {
            delay(startupDelayMillis)
            val startupOutput = session.readOutput()
            if (isAuthenticationPrompt(startupOutput)) {
                throw IllegalStateException("Antigravity CLI authentication is unavailable")
            }
            if (!session.isAlive()) {
                throw IllegalStateException("Antigravity CLI exited before the usage command was sent")
            }
            session.sendUsageCommand()
            val panelDeadline = System.nanoTime() + timeoutMillis * 1_000_000
            var sawPanel = false

            while (System.nanoTime() < panelDeadline) {
                val output = session.readOutput()
                if (isAuthenticationPrompt(output)) {
                    throw IllegalStateException("Antigravity CLI authentication is unavailable")
                }
                if (AntigravityUsageParser.hasRecognizedPanelHeader(output)) {
                    sawPanel = true
                    val parsed = runCatching { AntigravityUsageParser.parse(output) }.getOrNull()
                    if (!parsed.isNullOrEmpty()) {
                        delay(settleDelayMillis)
                        return@withContext session.readOutput()
                    }
                }
                if (!session.isAlive()) {
                    val status = session.exitCodeOrNull()
                    throw IllegalStateException(
                        if (status == 0 && sawPanel) "Antigravity usage panel returned no quota values"
                        else "Antigravity CLI exited before the usage panel was available"
                    )
                }
                delay(POLL_INTERVAL_MILLIS)
            }

            throw IllegalStateException(
                if (sawPanel) "Antigravity usage panel format is unrecognized"
                else "Antigravity CLI did not show the official /usage panel before timeout" +
                    (session.outputCaptureSummary()?.let { summary -> " ($summary)" } ?: "")
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            session.terminateProcessTree()
        }
    }

    private fun isAuthenticationPrompt(output: String): Boolean {
        val normalized = output.lowercase()
        return "sign in to continue" in normalized ||
            "login required" in normalized ||
            "authentication required" in normalized ||
            "session expired" in normalized ||
            "not authenticated" in normalized
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
        const val DEFAULT_STARTUP_DELAY_MILLIS = 900L
        const val POLL_INTERVAL_MILLIS = 100L
    }
}

private fun findAgyExecutable(): File? {
    val pathValue = System.getenv("PATH").orEmpty()
    val windows = System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)
    val executableName = if (windows) "agy.exe" else "agy"
    return pathValue.split(File.pathSeparator)
        .asSequence()
        .filter(String::isNotBlank)
        .map { directory -> File(directory, executableName) }
        .firstOrNull { file -> file.isFile && (windows || file.canExecute()) }
}

private class NativeAntigravityPtySessionFactory : AntigravityPtySessionFactory {
    override fun start(executable: File, workingDirectory: File): AntigravityPtySession {
        val environment = LinkedHashMap(System.getenv())
        environment.putIfAbsent("TERM", "xterm-256color")
        environment["COLUMNS"] = "120"
        environment["LINES"] = "40"

        val process = PtyProcessBuilder(arrayOf(executable.absolutePath))
            .setEnvironment(environment)
            .setDirectory(workingDirectory.absolutePath)
            .start()
        return NativeAntigravityPtySession(process)
    }
}

private class NativeAntigravityPtySession(
    private val process: PtyProcess
) : AntigravityPtySession {
    private val output = BoundedPtyOutput(MAX_CAPTURE_BYTES)
    private val inputStream = process.inputStream
    private val reader = thread(name = "antigravity-usage-pty-reader", isDaemon = true) {
        val buffer = ByteArray(1024)
        try {
            while (true) {
                val count = inputStream.read(buffer)
                if (count < 0) break
                output.append(buffer, count)
            }
        } catch (_: Throwable) {
            // Fechamento normal quando o timeout ou cancelamento mata a árvore.
        }
    }

    override fun sendUsageCommand() {
        val stream = process.outputStream
        stream.write("/usage".toByteArray(StandardCharsets.UTF_8))
        stream.write(process.enterKeyCode.toInt())
        stream.flush()
    }

    override fun readOutput(): String = output.snapshot()

    override fun isAlive(): Boolean = process.isAlive

    override fun exitCodeOrNull(): Int? = runCatching { process.exitValue() }.getOrNull()

    override fun outputCaptureSummary(): String {
        val summary = output.summary()
        return "terminal bytes captured=${summary.capturedBytes}, received=${summary.receivedBytes}, truncated=${summary.wasTruncated}"
    }

    override fun terminateProcessTree() {
        runCatching {
            val descendants = process.toHandle().descendants().toList().asReversed()
            descendants.forEach { child -> runCatching { child.destroyForcibly() } }
        }
        runCatching { process.destroyForcibly() }
        runCatching { process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
        runCatching { process.inputStream.close() }
        runCatching { process.outputStream.close() }
        reader.join(500L)
    }

    private companion object {
        const val MAX_CAPTURE_BYTES = 64 * 1024
    }
}

private class BoundedPtyOutput(private val maxBytes: Int) {
    private val bytes = ByteArrayOutputStream(maxBytes)
    private var receivedBytes = 0L

    @Synchronized
    fun append(chunk: ByteArray, count: Int) {
        val acceptedCount = count.coerceIn(0, chunk.size)
        receivedBytes = if (Long.MAX_VALUE - receivedBytes < acceptedCount) Long.MAX_VALUE else receivedBytes + acceptedCount
        val remaining = maxBytes - bytes.size()
        if (remaining > 0) bytes.write(chunk, 0, minOf(acceptedCount, remaining))
    }

    @Synchronized
    fun snapshot(): String = bytes.toString(StandardCharsets.UTF_8.name())

    @Synchronized
    fun summary(): AntigravityPtyOutputSummary = AntigravityPtyOutputSummary(bytes.size(), receivedBytes)
}
