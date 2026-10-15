package com.usagemonitor

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.sanitizeBreadcrumbMessage
import com.usagemonitor.presentation.ui.windowDragMessage
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowDragProbeTest {

    /** O caso da #416: o app pediu, a janela ficou onde estava. */
    @Test
    fun `a window that stayed put is reported as not moved`() {
        assertEquals(
            "arrasto hud: início 600,0 pedido 900,300 obtido 600,0 (não moveu)",
            windowDragMessage("hud", Point(600, 0), Point(900, 300), Point(600, 0))
        )
    }

    @Test
    fun `a window that followed the request is reported as moved`() {
        val message = windowDragMessage("Configurações", Point(100, 100), Point(400, 250), Point(400, 250))

        assertTrue(message.endsWith("(moveu)"), message)
    }

    @Test
    fun `a window that moved elsewhere is told apart from one that followed`() {
        val message = windowDragMessage("hud", Point(100, 100), Point(400, 250), Point(380, 250))

        assertTrue(message.endsWith("(moveu diferente)"), message)
    }

    @Test
    fun `an unread position is written as null and the outcome as unknown`() {
        val message = windowDragMessage("hud", null, Point(1, 2), null)

        assertEquals("arrasto hud: início null pedido 1,2 obtido null (?)", message)
    }

    @Test
    fun `the longest message survives the log sanitizer untouched`() {
        val far = Point(-100_000, -100_000)
        val message = windowDragMessage("Novidades da versão", far, Point(100_000, 100_000), Point(-100_000, -99_999))

        assertTrue(message.length <= Breadcrumb.MAX_MESSAGE_LENGTH, "${message.length}: $message")
        assertEquals(message, sanitizeBreadcrumbMessage(message))
    }
}
