package com.usagemonitor

import com.usagemonitor.data.diagnostics.AppLogFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StderrCaptureTest {

    @Test
    fun `installed capture keeps the original output and logs each line`() {
        withLog { log, file ->
            val previous = System.err
            val original = ByteArrayOutputStream()
            System.setErr(PrintStream(original, true))
            try {
                StderrCapture(log).install()
                System.err.println("Skiko: fallback para SOFTWARE")
                System.err.print("sem quebra ")
                System.err.println("ainda a mesma linha")
            } finally {
                System.setErr(previous)
            }

            assertTrue(original.toString().contains("Skiko: fallback para SOFTWARE"))
            val lines = file.readLines()
            assertEquals(2, lines.size, lines.joinToString("\n"))
            assertTrue(lines[0].contains("\"source\":\"stderr\""), lines[0])
            assertTrue(lines[0].contains("Skiko: fallback para SOFTWARE"), lines[0])
            assertTrue(lines[1].contains("sem quebra ainda a mesma linha"), lines[1])
        }
    }

    @Test
    fun `stack frames are left out, the header line stays`() {
        withLog { log, file ->
            val capture = StderrCapture(log)

            capture.onLine("Exception in thread \"x\" java.lang.IllegalStateException: boom")
            capture.onLine("\tat com.usagemonitor.Foo.bar(Foo.kt:10)")
            capture.onLine("\t... 12 more")
            capture.onLine("Caused by: java.io.IOException: disco")
            capture.onLine("")

            val lines = file.readLines()
            assertEquals(1, lines.size, lines.joinToString("\n"))
            assertTrue(lines.single().contains("IllegalStateException: boom"), lines.single())
        }
    }

    @Test
    fun `lines past the per minute ceiling are counted, not written`() {
        withLog { log, file ->
            var now = 0L
            val capture = StderrCapture(log, maxLinesPerMinute = 3, nowMillis = { now })

            repeat(10) { index -> capture.onLine("aviso $index") }
            now = 61_000L
            capture.onLine("depois do minuto")

            val lines = file.readLines()
            assertEquals(5, lines.size, lines.joinToString("\n"))
            assertTrue(lines[3].contains("7 linhas do stderr descartadas"), lines[3])
            assertTrue(lines[4].contains("depois do minuto"), lines[4])
            assertFalse(lines.any { line -> line.contains("aviso 3") })
        }
    }

    private fun withLog(block: (AppLogFile, File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("stderr-capture-test").toFile()
        try {
            val file = File(tempDir, "diagnostics/app.log")
            block(AppLogFile(logFile = file), file)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
