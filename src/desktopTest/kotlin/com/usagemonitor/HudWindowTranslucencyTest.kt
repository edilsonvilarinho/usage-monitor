package com.usagemonitor

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.sanitizeBreadcrumbMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HudWindowTranslucencyTest {

    /**
     * O caso da #426: o monitor aceita alfa por pixel, mas a configuração desta
     * janela não é a que tem alfa — o AWT a trocou ao cruzar de monitor.
     */
    @Test
    fun `a window without alpha on a monitor that supports it is repaired`() {
        assertTrue(needed(AutoStartManager.Platform.LINUX))
    }

    /**
     * Comportamento nativo medido numa plataforma nasce restrito a ela (#340).
     * No Windows a janela nasce e fica com alfa; o macOS não foi medido.
     */
    @Test
    fun `only Linux repairs the window`() {
        assertFalse(needed(AutoStartManager.Platform.WINDOWS))
        assertFalse(needed(AutoStartManager.Platform.MACOS))
        assertFalse(needed(AutoStartManager.Platform.OTHER))
    }

    /** Ambiente que não compõe alfa não tem o que recuperar: quem cuida é o recorte. */
    @Test
    fun `a monitor that refuses per-pixel alpha is left alone`() {
        assertFalse(needed(devicePerPixelTranslucent = false))
    }

    /** A janela já está com alfa: mexer nela seria um pisca à toa. */
    @Test
    fun `a window that already has alpha is left alone`() {
        assertFalse(needed(configTranslucencyCapable = true))
    }

    /** `null` é não medido, e não "medido e falso": sem medida não se mexe na janela. */
    @Test
    fun `an unmeasured answer never triggers the repair`() {
        assertFalse(needed(devicePerPixelTranslucent = null))
        assertFalse(needed(configTranslucencyCapable = null))
    }

    /** O reparo recria a janela nativa: repetir para sempre seria um pisca eterno. */
    @Test
    fun `the repair stops at the attempt ceiling`() {
        assertTrue(needed(attempts = HUD_TRANSLUCENCY_REPAIR_ATTEMPTS - 1))
        assertFalse(needed(attempts = HUD_TRANSLUCENCY_REPAIR_ATTEMPTS))
        assertFalse(needed(attempts = HUD_TRANSLUCENCY_REPAIR_ATTEMPTS + 1))
    }

    /** A linha que um relato futuro mostra: a profundidade antes e depois. */
    @Test
    fun `the message carries the depth before and after`() {
        val message = hudTranslucencyRepairMessage(repair())

        assertEquals(
            "hud-alfa :0.1 capaz=false->true profundidade=24->32 tentativa=1; restaurar=ok",
            message
        )
    }

    @Test
    fun `a refused repair says why instead of saying restored`() {
        val message = hudTranslucencyRepairMessage(
            repair(capableAfter = false, pixelSizeAfter = 24, error = "UnsupportedOperationException: no alpha")
        )

        assertTrue(message.contains("profundidade=24->24"), message)
        assertTrue(message.endsWith("falha=UnsupportedOperationException: no alpha"), message)
    }

    /** O que o AWT não respondeu vira `?`, nunca a palavra `null` colada no número. */
    @Test
    fun `an unmeasured answer is written as a question mark`() {
        val message = hudTranslucencyRepairMessage(
            repair(deviceId = null, capableBefore = null, pixelSizeBefore = null)
        )

        assertTrue(message.startsWith("hud-alfa ? capaz=?->true profundidade=?->32"), message)
    }

    @Test
    fun `the restore failure is named when the peer came back different`() {
        val message = hudTranslucencyRepairMessage(repair(restoreError = "IllegalComponentStateException"))

        assertTrue(message.endsWith("restaurar=IllegalComponentStateException"), message)
    }

    /** A fábrica da trilha corta em 200: a linha mais longa possível cabe inteira. */
    @Test
    fun `the longest message survives the log sanitizer untouched`() {
        val message = hudTranslucencyRepairMessage(
            repair(
                deviceId = "\\Display2",
                attempt = HUD_TRANSLUCENCY_REPAIR_ATTEMPTS,
                error = hudTranslucencyFailureOf(UnsupportedOperationException("a".repeat(400)))
            )
        )

        assertTrue(message.length <= Breadcrumb.MAX_MESSAGE_LENGTH, "${message.length}: $message")
        assertEquals(message, sanitizeBreadcrumbMessage(message))
    }

    /** Motivo longo é cortado, para o número que interessa não sair do log. */
    @Test
    fun `a long failure reason is truncated instead of pushing the numbers out`() {
        val reason = hudTranslucencyFailureOf(UnsupportedOperationException("a".repeat(400)))

        assertEquals(HUD_TRANSLUCENCY_FAILURE_LENGTH, reason.length)
        assertTrue(reason.startsWith("UnsupportedOperationException: aaa"), reason)
        assertTrue(reason.endsWith("..."), reason)
    }

    /** Tipo sem mensagem não vira `Tipo: null`. */
    @Test
    fun `a failure without a message is named by its type alone`() {
        assertEquals("IllegalStateException", hudTranslucencyFailureOf(IllegalStateException()))
        assertEquals("IllegalStateException", hudTranslucencyFailureOf(IllegalStateException("   ")))
        assertEquals("IllegalStateException: sem alfa", hudTranslucencyFailureOf(IllegalStateException("sem alfa")))
    }

    private fun needed(
        platform: AutoStartManager.Platform = AutoStartManager.Platform.LINUX,
        devicePerPixelTranslucent: Boolean? = true,
        configTranslucencyCapable: Boolean? = false,
        attempts: Int = 0
    ) = hudTranslucencyRepairNeeded(platform, devicePerPixelTranslucent, configTranslucencyCapable, attempts)

    private fun repair(
        deviceId: String? = ":0.1",
        capableBefore: Boolean? = false,
        capableAfter: Boolean? = true,
        pixelSizeBefore: Int? = 24,
        pixelSizeAfter: Int? = 32,
        attempt: Int = 1,
        error: String? = null,
        restoreError: String? = null
    ) = HudTranslucencyRepair(
        deviceId, capableBefore, capableAfter, pixelSizeBefore, pixelSizeAfter, attempt, error, restoreError
    )
}
