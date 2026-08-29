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
