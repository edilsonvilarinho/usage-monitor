package com.usagemonitor

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class CapturingRecorder : BreadcrumbRecorder {
    val steps = mutableListOf<Pair<BreadcrumbCategory, String>>()

    override fun record(category: BreadcrumbCategory, message: String) {
        steps += category to message
    }

    override fun read(limit: Int): List<Breadcrumb> = emptyList()
}

class CrashHandlerTest {

    @Test
    fun `a crash writes a breadcrumb with the class, the message and the stack top`() {
        withTempFile { marker ->
            val recorder = CapturingRecorder()
            val handler = CrashHandler(breadcrumbs = recorder, markerFile = marker)

            handler.uncaughtException(Thread.currentThread(), thrown())

            val step = recorder.steps.single()
            assertEquals(BreadcrumbCategory.CRASH, step.first)
            assertTrue(step.second.contains("IllegalStateException"), step.second)
            assertTrue(step.second.contains("índice indisponível"), step.second)
            assertTrue(step.second.contains("CrashHandlerTest"), step.second)
        }
    }

    @Test
    fun `the marker records what the failure was`() {
        withTempFile { marker ->
            val handler = CrashHandler(
                breadcrumbs = CapturingRecorder(),
                markerFile = marker,
                nowMillis = { 1_700_000_000_000L }
            )

            handler.uncaughtException(Thread.currentThread(), thrown())

            val content = marker.readText()
            assertTrue(content.contains("\"ts\":\"2023-11-14T22:13:20Z\""), content)
            assertTrue(content.contains("\"exception\":\"IllegalStateException\""), content)
            assertTrue(content.contains("\"message\":\"índice indisponível\""), content)
            assertTrue(content.contains("\"stackTop\":["), content)
        }
    }

    /**
     * Sem o repasse, uma queda que hoje aparece no console passaria a não
     * aparecer em lugar nenhum: o app teria trocado um diagnóstico por outro em
     * vez de somar os dois.
     */
    @Test
    fun `the previous handler still receives the exception`() {
        withTempFile { marker ->
            val received = mutableListOf<Throwable>()
            val original = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, error -> received += error }
            try {
                val handler = CrashHandler(breadcrumbs = CapturingRecorder(), markerFile = marker)
                handler.install()

                val error = thrown()
                handler.uncaughtException(Thread.currentThread(), error)

                assertEquals(listOf(error), received)
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(original)
            }
        }
    }

    /**
     * O handler roda com a JVM de saída: uma falha ao gravar o marcador não pode
     * impedir o repasse, que é o que ainda pode registrar a queda.
     */
    @Test
    fun `a marker that cannot be written does not stop the chain`() {
        withTempFile { marker ->
            marker.parentFile?.mkdirs()
            val blocked = File(marker.parentFile, "bloqueado")
            blocked.writeText("nao sou um diretorio")

            val received = mutableListOf<Throwable>()
            val original = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, error -> received += error }
            try {
                val handler = CrashHandler(
                    breadcrumbs = CapturingRecorder(),
                    markerFile = File(blocked, "pending-crash.json")
                )
                handler.install()

                val error = thrown()
                handler.uncaughtException(Thread.currentThread(), error)

                assertEquals(listOf(error), received)
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(original)
            }
        }
    }

    /**
     * A captura é *best-effort* e passa por um capturer injetado justamente
     * porque `java.awt.Robot` não roda em CI headless.
     */
    @Test
    fun `a captured window is written next to the marker`() {
        withTempFile { marker ->
            val png = byteArrayOf(1, 2, 3)
            val screenshot = File(marker.parentFile, "pending-crash.png")
            val handler = CrashHandler(
                breadcrumbs = CapturingRecorder(),
                markerFile = marker,
                screenshots = { png },
                screenshotFile = screenshot
            )

            handler.uncaughtException(Thread.currentThread(), thrown())

            assertTrue(screenshot.exists())
            assertEquals(png.toList(), screenshot.readBytes().toList())
        }
    }

    /**
     * Sem captura, a imagem de uma queda anterior não pode sobrar: ela mostraria
     * uma tela que não é a do defeito que está sendo reportado.
     */
    @Test
    fun `a stale screenshot from an earlier crash is removed`() {
        withTempFile { marker ->
            marker.parentFile?.mkdirs()
            val screenshot = File(marker.parentFile, "pending-crash.png")
            screenshot.writeBytes(byteArrayOf(9, 9))

            CrashHandler(
                breadcrumbs = CapturingRecorder(),
                markerFile = marker,
                screenshots = { null },
                screenshotFile = screenshot
            ).uncaughtException(Thread.currentThread(), thrown())

            assertTrue(!screenshot.exists())
        }
    }

    @Test
    fun `the marker written by a crash is readable at the next startup`() {
        withTempFile { marker ->
            CrashHandler(
                breadcrumbs = CapturingRecorder(),
                markerFile = marker,
                nowMillis = { 1_700_000_000_000L }
            ).uncaughtException(Thread.currentThread(), thrown())

            val read = readPendingCrashMarker(markerFile = marker)

            assertEquals("IllegalStateException", read?.exception)
            assertEquals("índice indisponível", read?.message)
            assertEquals("2023-11-14T22:13:20Z", read?.ts)
        }
    }

    @Test
    fun `no marker means no pending crash`() {
        withTempFile { marker ->
            assertEquals(null, readPendingCrashMarker(markerFile = marker))
        }
    }

    /**
     * Arquivo truncado por um desligamento abrupto é o caso em que esta leitura
     * mais precisa funcionar: devolve nulo, não lança.
     */
    @Test
    fun `an unreadable marker is treated as no crash`() {
        withTempFile { marker ->
            marker.parentFile?.mkdirs()
            marker.writeText("{\"ts\":\"2023-11-14T22")

            assertEquals(null, readPendingCrashMarker(markerFile = marker))
        }
    }

    /**
     * A leitura **não apaga**. Apagar aqui perderia a queda se o app fosse
     * fechado antes de a tela aparecer — que é exatamente o que acontece quando
     * ele volta quebrado.
     */
    @Test
    fun `reading the marker leaves it on disk`() {
        withTempFile { marker ->
            CrashHandler(breadcrumbs = CapturingRecorder(), markerFile = marker)
                .uncaughtException(Thread.currentThread(), thrown())

            readPendingCrashMarker(markerFile = marker)

            assertTrue(marker.exists())
        }
    }

    private fun thrown(): Throwable {
        return runCatching { throw IllegalStateException("índice indisponível") }
            .exceptionOrNull()!!
    }

    private fun withTempFile(block: (File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("crash-handler-test").toFile()
        try {
            block(File(tempDir, "diagnostics/pending-crash.json"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
