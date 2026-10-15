package com.usagemonitor

import com.usagemonitor.data.diagnostics.AppLogFile
import java.awt.EventQueue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdtWatchdogTest {

    @Test
    fun `a ping not served past the threshold is reported once`() {
        withLog { log, file ->
            var now = 0L
            val pending = mutableListOf<Runnable>()
            val watchdog = EdtWatchdog(log, stallThresholdMillis = 5_000, pollIntervalMillis = 1_000, nowMillis = { now }, post = { pending += it })

            // Ping postado em t=0 e nunca atendido; o watchdog anda de segundo em segundo.
            for (second in 0..8) {
                now = second * 1_000L
                watchdog.tick()
            }

            val lines = file.readLines()
            assertEquals(1, lines.size, lines.joinToString("\n"))
            assertTrue(lines.single().contains("\"level\":\"warn\""), lines.single())
            assertTrue(lines.single().contains("EDT sem responder há 5000 ms"), lines.single())
            assertEquals(1, pending.size)
        }
    }

    @Test
    fun `the recovery is logged with how long the stall lasted`() {
        withLog { log, file ->
            var now = 0L
            val pending = mutableListOf<Runnable>()
            val watchdog = EdtWatchdog(log, stallThresholdMillis = 5_000, pollIntervalMillis = 1_000, nowMillis = { now }, post = { pending += it })
            for (second in 0..6) {
                now = second * 1_000L
                watchdog.tick()
            }

            now = 7_500L
            pending.single().run()

            val lines = file.readLines()
            assertEquals(2, lines.size, lines.joinToString("\n"))
            assertTrue(lines.last().contains("EDT voltou a responder após 7500 ms"), lines.last())
        }
    }

    @Test
    fun `a served ping within the threshold writes nothing`() {
        withLog { log, file ->
            var now = 0L
            val watchdog = EdtWatchdog(log, nowMillis = { now }, post = { it.run() })

            for (second in 0..20) {
                now = second * 1_000L
                watchdog.tick()
            }

            assertTrue(!file.exists() || file.readLines().isEmpty())
        }
    }

    /** Tampa fechada: o watchdog também dormiu, então não é o EDT que travou. */
    @Test
    fun `a gap in the watchdog itself restarts the ping instead of reporting`() {
        withLog { log, file ->
            var now = 0L
            val watchdog = EdtWatchdog(log, stallThresholdMillis = 5_000, pollIntervalMillis = 1_000, nowMillis = { now }, post = { })

            watchdog.tick()
            now = 1_000L
            watchdog.tick()
            now = 600_000L
            watchdog.tick()
            now = 601_000L
            watchdog.tick()

            assertTrue(!file.exists() || file.readLines().isEmpty())
        }
    }

    /** Com o EDT real preso num `sleep`, a pilha gravada aponta para ele. */
    @Test
    fun `a real blocked event thread is reported with its stack`() {
        withLog { log, file ->
            val watchdog = EdtWatchdog(log, stallThresholdMillis = 300, pollIntervalMillis = 50)
            val released = CountDownLatch(1)
            // Primeiro ping atendido: o watchdog passa a conhecer a thread do EDT.
            watchdog.tick()
            EventQueue.invokeAndWait { }

            EventQueue.invokeLater { Thread.sleep(900); released.countDown() }
            val deadline = System.currentTimeMillis() + 3_000
            while (System.currentTimeMillis() < deadline && released.count > 0) {
                watchdog.tick()
                Thread.sleep(50)
            }
            assertTrue(released.await(3, TimeUnit.SECONDS))
            // Um tique depois de o EDT voltar, para o ping pendente ser atendido.
            EventQueue.invokeAndWait { }

            val stall = file.readLines().first { line -> line.contains("EDT sem responder") }
            assertTrue(stall.contains("AWT-EventQueue"), stall)
            assertTrue(stall.contains("Thread.sleep"), stall)
            assertTrue(stall.contains("EdtStallSnapshot"), stall)
        }
    }

    private fun withLog(block: (AppLogFile, File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("edt-watchdog-test").toFile()
        try {
            val file = File(tempDir, "diagnostics/app.log")
            block(AppLogFile(logFile = file), file)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
