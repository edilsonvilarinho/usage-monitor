package com.usagemonitor

import com.usagemonitor.data.diagnostics.AppLogFile
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.sanitizeBreadcrumbMessage
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudWindowFactsTest {

    /** O caso da #416: a janela não ficou onde foi pedida e o alfa é a pergunta. */
    @Test
    fun `the message says when the window is not where it was asked`() {
        val message = hudWindowFactsMessage(facts(actualX = 1185, actualY = 265, deviceId = ":0.0"))

        assertEquals(
            "hud pedido 600,0 740x590 obtido 1185,265 740x590 (diferente); " +
                "alfa px=true uni=true recorte=true; render=OPENGL; " +
                "device=:0.0",
            message
        )
    }

    @Test
    fun `an unmeasured answer is written as null, not false`() {
        val message = hudWindowFactsMessage(facts(perPixelTranslucent = null, renderApi = null, deviceId = null))

        assertTrue(message.contains("(igual)"), message)
        assertTrue(message.contains("alfa px=null"), message)
        assertTrue(message.contains("render=null"), message)
        assertTrue(message.contains("device=?"), message)
    }

    /** A fábrica da trilha corta em 200: a linha mais longa possível cabe inteira. */
    @Test
    fun `the longest message survives the log sanitizer untouched`() {
        val message = hudWindowFactsMessage(
            facts(
                requestedX = -10_000, requestedY = -10_000, requestedWidth = 10_000, requestedHeight = 10_000,
                actualX = -10_000, actualY = -10_001, actualWidth = 10_000, actualHeight = 10_000,
                perPixelTranslucent = false, uniformTranslucent = false, perPixelTransparent = false,
                renderApi = "DIRECT3D",
                deviceId = ":0.0"
            )
        )

        assertTrue(message.length <= Breadcrumb.MAX_MESSAGE_LENGTH, "${message.length}: $message")
        assertEquals(message, sanitizeBreadcrumbMessage(message))
    }

    /**
     * A linha de troca de monitor: cabe no mesmo limite de 200 caracteres mesmo
     * com ids de device longos. É a garantia de o `app.log` aceitar a linha sem
     * cortar.
     */
    @Test
    fun `the device change message survives the log sanitizer untouched`() {
        val message = hudDeviceChangeMessage(
            deviceChangeFacts(
                prevId = ":0.0",
                newId = ":0.1",
                x = -9_999, y = -9_999, width = 9_999, height = 9_999,
                perPixelTranslucent = false, uniformTranslucent = false, perPixelTransparent = false,
                renderApi = "DIRECT3D",
                reapplyError = "IllegalStateException: o contexto ficou preso",
                visibilityRemapped = true,
                backgroundAlpha = 0,
                isOpaque = false,
                bufferRecreated = true
            )
        )

        assertTrue(message.length <= Breadcrumb.MAX_MESSAGE_LENGTH, "${message.length}: $message")
        assertEquals(message, sanitizeBreadcrumbMessage(message))
    }

    @Test
    fun `the device change message distinguishes reapplied from failed`() {
        val success = hudDeviceChangeMessage(
            deviceChangeFacts(prevId = ":0.0", newId = ":0.1", reapplied = true)
        )
        val failure = hudDeviceChangeMessage(
            deviceChangeFacts(
                prevId = ":0.0",
                newId = ":0.1",
                reapplied = false,
                reapplyError = "IllegalStateException: x"
            )
        )

        assertTrue(success.contains("reaplicar=ok"), success)
        assertTrue(failure.contains("reaplicar=falha=IllegalStateException: x"), failure)
    }

    @Test
    fun `the device change message says when the remap was needed and whether it worked`() {
        val remapped = hudDeviceChangeMessage(
            deviceChangeFacts(prevId = ":0.0", newId = ":0.1", visibilityRemapped = true)
        )
        val skipped = hudDeviceChangeMessage(
            deviceChangeFacts(prevId = ":0.0", newId = ":0.1", visibilityRemapped = false)
        )
        val remapFailed = hudDeviceChangeMessage(
            deviceChangeFacts(
                prevId = ":0.0",
                newId = ":0.1",
                visibilityRemapped = false,
                remapError = "IllegalStateException: x"
            )
        )

        assertTrue(remapped.contains("remapear=ok"), remapped)
        assertTrue(skipped.contains("remapear=pulado"), skipped)
        assertTrue(remapFailed.contains("remapear=falha=IllegalStateException: x"), remapFailed)
    }

    @Test
    fun `the device change message carries the peer state after reapply`() {
        val message = hudDeviceChangeMessage(
            deviceChangeFacts(
                prevId = ":0.0",
                newId = ":0.1",
                reapplied = true,
                visibilityRemapped = true,
                backgroundAlpha = 0,
                isOpaque = false
            )
        )

        assertTrue(message.contains("fundo=0"), message)
        assertTrue(message.contains("opaco=false"), message)
    }

    @Test
    fun `the device change message says when the buffer was recreated`() {
        val recriado = hudDeviceChangeMessage(
            deviceChangeFacts(prevId = ":0.0", newId = ":0.1", bufferRecreated = true)
        )
        val falhou = hudDeviceChangeMessage(
            deviceChangeFacts(
                prevId = ":0.0",
                newId = ":0.1",
                bufferRecreated = false,
                bufferRecreateError = "IllegalStateException: x"
            )
        )

        assertTrue(recriado.contains("buffer=recriado"), recriado)
        assertTrue(falhou.contains("buffer=falha=IllegalStateException: x"), falhou)
    }

    /**
     * A linha de troca de device entra no `app.log` com `source="hud-device"`,
     * para o pacote `--diagnostics` recolher a evidência do secundário sem
     * precisar de uma nova tela.
     */
    @Test
    fun `recordHudDeviceChange writes the source as hud-device`() {
        withTempLog { log ->
            runBlocking {
                recordHudDeviceChange(
                    window = StubWindow,
                    facts = deviceChangeFacts(prevId = ":0.0", newId = ":0.1"),
                    appLog = log
                )
            }

            val line = log.files().single().readLines().single()
            assertTrue(line.contains("\"source\":\"hud-device\""), line)
            assertTrue(line.contains("hud-device :0.0->:0.1"), line)
        }
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
        deviceId: String? = ":0.0"
    ) = HudWindowFacts(
        requestedX, requestedY, requestedWidth, requestedHeight,
        actualX, actualY, actualWidth, actualHeight,
        perPixelTranslucent, uniformTranslucent, perPixelTransparent, renderApi, deviceId
    )

    private fun deviceChangeFacts(
        prevId: String = ":0.0",
        newId: String = ":0.1",
        x: Int = 2674,
        y: Int = 163,
        width: Int = 413,
        height: Int = 368,
        perPixelTranslucent: Boolean? = true,
        uniformTranslucent: Boolean? = true,
        perPixelTransparent: Boolean? = true,
        renderApi: String? = "OPENGL",
        reapplied: Boolean = true,
        reapplyError: String? = null,
        visibilityRemapped: Boolean = false,
        remapError: String? = null,
        backgroundAlpha: Int? = 0,
        isOpaque: Boolean? = false,
        isDisplayable: Boolean? = true,
        isShowing: Boolean? = true,
        bufferRecreated: Boolean = false,
        bufferRecreateError: String? = null
    ) = HudDeviceChangeFacts(
        prevId, newId, x, y, width, height,
        perPixelTranslucent, uniformTranslucent, perPixelTransparent, renderApi,
        reapplied, reapplyError,
        visibilityRemapped, remapError, backgroundAlpha, isOpaque,
        isDisplayable, isShowing, bufferRecreated, bufferRecreateError
    )

    private fun withTempLog(block: (AppLogFile) -> Unit) {
        val dir = kotlin.io.path.createTempDirectory("hud-facts-log").toFile()
        try {
            block(
                AppLogFile(
                    logFile = File(dir, "app.log"),
                    nowMillis = { 1_700_000_000_000L },
                    pid = 42,
                    version = "9.9.9"
                )
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}

/** Janela AWT só para satisfazer a assinatura; `recordHudDeviceChange` não lê dela. */
private object StubWindow : java.awt.Window(java.awt.Frame())
