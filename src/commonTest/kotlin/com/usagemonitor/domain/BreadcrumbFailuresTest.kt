package com.usagemonitor.domain

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.presentation.viewmodel.recordFailure
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BreadcrumbFailuresTest {

    @Test
    fun `throwable failure keeps operation type and sanitized detail`() {
        val recorder = CapturingRecorder()

        recorder.recordFailure(
            "salvar relatório",
            IllegalStateException("Falha em C:\\Users\\Alice\\report.json; token=private-value")
        )

        val event = recorder.events.single()
        assertEquals(BreadcrumbCategory.ERROR, event.category)
        assertTrue(event.message.contains("salvar relatório falhou"), event.message)
        assertTrue(event.message.contains("IllegalStateException"), event.message)
        assertTrue(event.message.contains("<caminho>/report.json"), event.message)
        assertFalse(event.message.contains("Alice"), event.message)
        assertFalse(event.message.contains("private-value"), event.message)
        assertTrue(event.message.length <= Breadcrumb.MAX_MESSAGE_LENGTH)
    }

    @Test
    fun `structured failure preserves status and omits response body`() {
        val recorder = CapturingRecorder()

        recorder.recordFailure(
            "ativar inicialização",
            "reg.exe retornou código 5: HTTP 403 Forbidden: {\"token\":\"secret\"}"
        )

        val message = recorder.events.single().message
        assertTrue(message.contains("código 5"), message)
        assertTrue(message.contains("HTTP 403"), message)
        assertTrue(message.contains("corpo omitido"), message)
        assertFalse(message.contains("secret"), message)
    }

    @Test
    fun `cancellation is rethrown and is not recorded as an error`() {
        val recorder = CapturingRecorder()

        assertFailsWith<CancellationException> {
            recorder.recordFailure("salvar relatório", CancellationException("cancelado"))
        }

        assertTrue(recorder.events.isEmpty())
    }

    private class CapturingRecorder : BreadcrumbRecorder {
        val events = mutableListOf<Breadcrumb>()

        override fun record(category: BreadcrumbCategory, message: String) {
            events += Breadcrumb.of(
                at = Instant.fromEpochMilliseconds(events.size.toLong()),
                category = category,
                message = message
            )
        }

        override fun read(limit: Int): List<Breadcrumb> = events.takeLast(limit)
    }
}
