package com.usagemonitor.data

import com.usagemonitor.data.datasource.ANTIGRAVITY_CLI_EXITED
import com.usagemonitor.data.datasource.ANTIGRAVITY_CLI_OUTPUT_TOO_LARGE
import com.usagemonitor.data.datasource.ANTIGRAVITY_CLI_TIMED_OUT
import com.usagemonitor.data.datasource.AntigravityProcessResult
import com.usagemonitor.data.datasource.AntigravityProcessStarter
import com.usagemonitor.data.datasource.LocalAntigravityUsageDataSource
import com.usagemonitor.data.datasource.SystemAntigravityProcessStarter
import com.usagemonitor.data.datasource.compareVersions
import com.usagemonitor.data.datasource.findAgyExecutable
import com.usagemonitor.data.datasource.isDirectExecutable
import com.usagemonitor.data.datasource.parseAgyVersion
import com.usagemonitor.domain.repository.AntigravityUsageFailureKind
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalAntigravityUsageDataSourceTest {

    private val workDir: File = Files.createTempDirectory("agy-work").toFile()
    private val agy: File = File(workDir, "agy.exe").also { it.writeText("fake") }

    private class RecordingStarter(
        var version: String = "1.2.9\n",
        var usage: AntigravityProcessResult = AntigravityProcessResult(0, "{}", timedOut = false, outputTooLarge = false)
    ) : AntigravityProcessStarter {
        val commands = mutableListOf<List<String>>()

        override fun run(
            command: List<String>,
            workingDirectory: File,
            timeoutMillis: Long,
            maxOutputBytes: Int
        ): AntigravityProcessResult {
            commands += command
            return if (command.last() == "--version") {
                AntigravityProcessResult(0, version, timedOut = false, outputTooLarge = false)
            } else {
                usage
            }
        }
    }

    private fun dataSource(starter: AntigravityProcessStarter, executable: File? = agy) =
        LocalAntigravityUsageDataSource(
            processStarter = starter,
            executableResolver = { executable },
            workingDirectoryProvider = { workDir }
        )

    /**
     * `/usage` chega como argumento próprio, sem shell no meio: pelo Git Bash ele
     * virou caminho e foi ao modelo como prompt. `--output-format json` é o que
     * traz a prova de que nenhum turno abriu.
     */
    @Test
    fun `runs agy directly with print usage and JSON output`() = runBlocking {
        val starter = RecordingStarter(usage = AntigravityProcessResult(0, "{\"ok\":1}", false, false))

        val output = dataSource(starter).readUsageJson()

        assertEquals("{\"ok\":1}", output)
        val usageCommand = starter.commands.last()
        assertEquals(agy.absolutePath, usageCommand.first())
        assertEquals("/usage", usageCommand.last())
        assertEquals("--print", usageCommand[usageCommand.size - 2])
        assertTrue(usageCommand.containsAll(listOf("--output-format", "json", "--sandbox")))
        assertFalse(usageCommand.any { it.contains("cmd", ignoreCase = true) || it == "-c" })
    }

    @Test
    fun `the version gate runs once per executable`() = runBlocking {
        val starter = RecordingStarter()
        val source = dataSource(starter)

        source.readUsageJson()
        source.readUsageJson()

        assertEquals(1, starter.commands.count { it.last() == "--version" })
    }

    @Test
    fun `an older or unreadable version never reaches usage`() = runBlocking {
        listOf("1.2.8\n", "agy dev build\n", "").forEach { version ->
            val starter = RecordingStarter(version = version)
            val error = assertFailsWith<IllegalStateException> { dataSource(starter).readUsageJson() }
            assertEquals(AntigravityUsageFailureKind.UNVERIFIED_VERSION.safeMessage, error.message)
            assertTrue(starter.commands.none { it.last() == "/usage" })
        }
    }

    @Test
    fun `missing CLI and script shims fail before any process starts`() = runBlocking {
        val starter = RecordingStarter()

        val missing = assertFailsWith<IllegalStateException> { dataSource(starter, executable = null).readUsageJson() }
        assertEquals(AntigravityUsageFailureKind.CLI_NOT_INSTALLED.safeMessage, missing.message)

        val shim = File(workDir, "agy.cmd").also { it.writeText("@echo off") }
        val launcher = assertFailsWith<IllegalStateException> { dataSource(starter, executable = shim).readUsageJson() }
        assertEquals(AntigravityUsageFailureKind.UNSUPPORTED_LAUNCHER.safeMessage, launcher.message)

        assertTrue(starter.commands.isEmpty())
    }

    @Test
    fun `timeout, oversized output and empty output are distinct failures`() = runBlocking {
        val cases = mapOf(
            AntigravityProcessResult(null, "", timedOut = true, outputTooLarge = false) to ANTIGRAVITY_CLI_TIMED_OUT,
            AntigravityProcessResult(0, "{", timedOut = false, outputTooLarge = true) to ANTIGRAVITY_CLI_OUTPUT_TOO_LARGE,
            AntigravityProcessResult(1, "  ", timedOut = false, outputTooLarge = false) to "$ANTIGRAVITY_CLI_EXITED (exit code 1)"
        )
        cases.forEach { (result, message) ->
            val error = assertFailsWith<IllegalStateException> {
                dataSource(RecordingStarter(usage = result)).readUsageJson()
            }
            assertEquals(message, error.message)
        }
    }

    /** Exit 1 com envelope é autenticação ou entrada inválida: quem decide é o JSON. */
    @Test
    fun `a non-zero exit with an envelope is handed to the mapper`() = runBlocking {
        val envelope = """{"status":"ERROR","error":"not logged in"}"""
        val output = dataSource(RecordingStarter(usage = AntigravityProcessResult(1, envelope, false, false))).readUsageJson()
        assertEquals(envelope, output)
    }

    @Test
    fun `version parsing and comparison`() {
        assertEquals(listOf(1, 2, 9), parseAgyVersion("1.2.9\n"))
        assertEquals(listOf(1, 10, 0), parseAgyVersion("agy version 1.10.0 (abc)"))
        assertNull(parseAgyVersion("dev"))
        assertTrue(compareVersions(listOf(1, 10, 0), listOf(1, 2, 9)) > 0)
        assertTrue(compareVersions(listOf(1, 2, 8), listOf(1, 2, 9)) < 0)
        assertEquals(0, compareVersions(listOf(1, 2, 9), listOf(1, 2, 9)))
    }

    @Test
    fun `executable discovery prefers the official Windows install and rejects shims`() {
        val localAppData = Files.createTempDirectory("localappdata").toFile()
        val installed = File(localAppData, "agy/bin/agy.exe").apply { parentFile.mkdirs(); writeText("x") }
        val onPath = Files.createTempDirectory("path").toFile()
        File(onPath, "agy.exe").writeText("x")

        assertEquals(
            installed,
            findAgyExecutable(mapOf("LOCALAPPDATA" to localAppData.path, "PATH" to onPath.path), windows = true)
        )
        assertEquals(
            File(onPath, "agy.exe"),
            findAgyExecutable(mapOf("PATH" to onPath.path), windows = true)
        )
        assertNull(findAgyExecutable(mapOf("PATH" to ""), windows = true))
        assertFalse(isDirectExecutable(File("agy.cmd")))
        assertFalse(isDirectExecutable(File("agy.BAT")))
        assertTrue(isDirectExecutable(File("agy.exe")))
    }

    /** Processo real e inofensivo: o timeout tem de matar a árvore e voltar logo. */
    @Test
    fun `the real starter kills a hung process on timeout`() {
        val windows = System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)
        val command = if (windows) {
            listOf(System.getenv("ComSpec") ?: "cmd.exe", "/c", "ping -n 10 127.0.0.1 >nul")
        } else {
            listOf("sleep", "10")
        }

        val started = System.nanoTime()
        val result = SystemAntigravityProcessStarter.run(command, workDir, timeoutMillis = 400, maxOutputBytes = 1024)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue(result.timedOut)
        assertNull(result.exitCode)
        assertTrue(elapsedMillis < 6_000, "took ${elapsedMillis}ms")
    }

    @Test
    fun `the real starter caps stdout instead of truncating silently`() {
        val windows = System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)
        val command = if (windows) {
            listOf(System.getenv("ComSpec") ?: "cmd.exe", "/c", "for /L %i in (1,1,200) do @echo 0123456789012345678901234567890123456789")
        } else {
            listOf("sh", "-c", "i=0; while [ \$i -lt 200 ]; do echo 0123456789012345678901234567890123456789; i=\$((i+1)); done")
        }

        val result = SystemAntigravityProcessStarter.run(command, workDir, timeoutMillis = 10_000, maxOutputBytes = 512)

        assertFalse(result.timedOut)
        assertTrue(result.outputTooLarge)
        assertTrue(result.stdout.length <= 512)
    }
}
