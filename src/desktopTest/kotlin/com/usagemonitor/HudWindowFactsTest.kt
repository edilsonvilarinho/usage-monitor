package com.usagemonitor

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.sanitizeBreadcrumbMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudWindowFactsTest {

    /** O caso da #416: a janela não ficou onde foi pedida e o alfa é a pergunta. */
    @Test
    fun `the message says when the window is not where it was asked`() {
        val message = hudWindowFactsMessage(facts(actualX = 1185, actualY = 265))

        assertEquals(
            "hud pedido 600,0 740x590 obtido 1185,265 740x590 (diferente); " +
                "alfa px=true uni=true recorte=true; render=OPENGL",
            message
        )
    }

    @Test
    fun `an unmeasured answer is written as null, not false`() {
        val message = hudWindowFactsMessage(facts(perPixelTranslucent = null, renderApi = null))

        assertTrue(message.contains("(igual)"), message)
        assertTrue(message.contains("alfa px=null"), message)
        assertTrue(message.contains("render=null"), message)
    }

    /** A fábrica da trilha corta em 200: a linha mais longa possível cabe inteira. */
    @Test
    fun `the longest message survives the log sanitizer untouched`() {
        val message = hudWindowFactsMessage(
            facts(
                requestedX = -10_000, requestedY = -10_000, requestedWidth = 10_000, requestedHeight = 10_000,
                actualX = -10_000, actualY = -10_001, actualWidth = 10_000, actualHeight = 10_000,
                perPixelTranslucent = false, uniformTranslucent = false, perPixelTransparent = false,
                renderApi = "DIRECT3D"
            )
        )

        assertTrue(message.length <= Breadcrumb.MAX_MESSAGE_LENGTH, "${message.length}: $message")
        assertEquals(message, sanitizeBreadcrumbMessage(message))
    }

    private fun facts(
        requestedX: Int = 600,
        requestedY: Int = 0,
        requestedWidth: Int = 740,
        requestedHeight: Int = 590,
        actualX: Int = requestedX,
        actualY: Int = requestedY,
        actualWidth: Int = requestedWidth,
        actualHeight: Int = requestedHeight,
        perPixelTranslucent: Boolean? = true,
        uniformTranslucent: Boolean? = true,
        perPixelTransparent: Boolean? = true,
        renderApi: String? = "OPENGL"
    ) = HudWindowFacts(
        requestedX, requestedY, requestedWidth, requestedHeight,
        actualX, actualY, actualWidth, actualHeight,
        perPixelTranslucent, uniformTranslucent, perPixelTransparent, renderApi
    )
}
