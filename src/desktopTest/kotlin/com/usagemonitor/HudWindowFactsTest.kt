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
                "alfa px=true uni=true recorte=true; render=OPENGL; device=:0.1 32bpp",
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

    /** Monitor não medido vira `?`, e não a palavra `null` colada no id. */
    @Test
    fun `an unknown monitor is written as a question mark`() {
        val message = hudWindowFactsMessage(facts(deviceId = null, pixelSize = null))

        assertTrue(message.endsWith("device=? ?bpp"), message)
    }

    /**
     * A fábrica da trilha corta em 200: a linha mais longa possível cabe inteira.
     * O id do Windows (`\Display2`) entra aqui de propósito — com **duas** barras
     * ele casaria com o caminho UNC do saneamento e viraria `<caminho>`.
     */
    @Test
    fun `the longest message survives the log sanitizer untouched`() {
        val message = hudWindowFactsMessage(
            facts(
                requestedX = -10_000, requestedY = -10_000, requestedWidth = 10_000, requestedHeight = 10_000,
                actualX = -10_000, actualY = -10_001, actualWidth = 10_000, actualHeight = 10_000,
                perPixelTranslucent = false, uniformTranslucent = false, perPixelTransparent = false,
                renderApi = "DIRECT3D", deviceId = "\\Display2", pixelSize = 24
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
        renderApi: String? = "OPENGL",
        deviceId: String? = ":0.1",
        pixelSize: Int? = 32
    ) = HudWindowFacts(
        requestedX, requestedY, requestedWidth, requestedHeight,
        actualX, actualY, actualWidth, actualHeight,
        perPixelTranslucent, uniformTranslucent, perPixelTransparent, renderApi, deviceId, pixelSize
    )

    /**
     * O COSMIC ignora a posição pedida (#430): a sonda registra quando a janela
     * aparece fora do último pedido, uma linha por posição nova, com teto.
     */
    @Test
    fun `the system move probe reports only what the app did not ask for`() {
        val lines = mutableListOf<String>()
        val probe = HudSystemMoveProbe(sink = { line -> lines += line })
        val size = java.awt.Dimension(459, 368)

        probe.moved(java.awt.Point(731, 163), size, dragging = false) // nada pedido ainda
        probe.requested(java.awt.Point(404, 0))
        probe.moved(java.awt.Point(404, 0), size, dragging = false) // o pedido, respeitado
        probe.moved(java.awt.Point(731, 163), size, dragging = false) // o compositor escolheu
        probe.moved(java.awt.Point(731, 163), size, dragging = false) // repetida
        probe.moved(java.awt.Point(900, 10), size, dragging = true) // arrasto: é do app

        assertEquals(listOf("hud movida pelo sistema: pedido 404,0 obtido 731,163 459x368"), lines)
    }

    @Test
    fun `the system move probe stops at its ceiling`() {
        val lines = mutableListOf<String>()
        val probe = HudSystemMoveProbe(sink = { line -> lines += line })
        probe.requested(java.awt.Point(0, 0))

        repeat(HUD_SYSTEM_MOVE_REPORT_LIMIT + 5) { index ->
            probe.moved(java.awt.Point(index + 1, 1), java.awt.Dimension(10, 10), dragging = false)
        }

        assertEquals(HUD_SYSTEM_MOVE_REPORT_LIMIT, lines.size)
    }

    @Test
    fun `the system move line survives the log sanitizer untouched`() {
        val message = hudSystemMoveMessage(java.awt.Point(-1920, -1440), java.awt.Point(-2651, -1163), java.awt.Dimension(3440, 1440))

        assertTrue(message.length <= Breadcrumb.MAX_MESSAGE_LENGTH, message)
        assertEquals(message, sanitizeBreadcrumbMessage(message))
    }
}
