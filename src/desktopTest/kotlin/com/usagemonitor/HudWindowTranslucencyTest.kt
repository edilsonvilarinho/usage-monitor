package com.usagemonitor

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.sanitizeBreadcrumbMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O alfa da janela da HUD: a leitura, as mensagens de log e a plataforma que
 * escolhe a própria `GraphicsConfiguration` (#426).
 *
 * Não há teste de reparo porque não há reparo: devolver o alfa a uma janela já
 * aberta obriga o AWT a recriar a janela nativa, e isso para o desenho do
 * Compose — a barra ficava correta e congelada. A janela nasce certa e, quando
 * o AWT troca a configuração dela no meio de um arrasto, ela renasce.
 */
class HudWindowTranslucencyTest {

    /**
     * Comportamento nativo medido numa plataforma nasce restrito a ela (#340).
     * A perda de alfa ao cruzar de monitor foi medida no Linux; no Windows e no
     * macOS a janela segue pelo `Window` de alto nível do Compose.
     */
    @Test
    fun `only Linux picks the window graphics configuration`() {
        assertTrue(hudPicksGraphicsConfiguration(AutoStartManager.Platform.LINUX))
        assertFalse(hudPicksGraphicsConfiguration(AutoStartManager.Platform.WINDOWS))
        assertFalse(hudPicksGraphicsConfiguration(AutoStartManager.Platform.MACOS))
        assertFalse(hudPicksGraphicsConfiguration(AutoStartManager.Platform.OTHER))
    }

    /** A primeira configuração que aceita alfa ganha; sem nenhuma, a padrão. */
    @Test
    fun `the first configuration that accepts alpha wins`() {
        assertEquals(32, firstTranslucencyCapable(listOf(24, 32, 30), { it == 32 }, fallback = 24))
        assertEquals(24, firstTranslucencyCapable(listOf(24, 16), { it == 32 }, fallback = 24))
        assertEquals(24, firstTranslucencyCapable(emptyList(), { it == 32 }, fallback = 24))
    }

    /** `null` é não medido e vale como **tem** alfa: recortar sem medida seria pior. */
    @Test
    fun `an unmeasured answer counts as having alpha`() {
        assertTrue(hudWindowHasAlpha(state(translucencyCapable = null)))
        assertTrue(hudWindowHasAlpha(state(translucencyCapable = true)))
        assertFalse(hudWindowHasAlpha(state(translucencyCapable = false)))
    }

    /** A linha do instante em que o alfa cai, que é o que um relato precisa ver. */
    @Test
    fun `the state message names the monitor, the capability and the depth`() {
        assertEquals(
            "hud-alfa estado :0.1 capaz=false monitor=true profundidade=24bpp em 100,200 592x436",
            hudTranslucencyStateMessage(state(translucencyCapable = false, pixelSize = 24))
        )
    }

    @Test
    fun `an unmeasured answer is written as a question mark`() {
        val message = hudTranslucencyStateMessage(
            state(
                deviceId = null, perPixelTranslucent = null, translucencyCapable = null,
                pixelSize = null, bounds = null
            )
        )

        assertEquals("hud-alfa estado ? capaz=? monitor=? profundidade=?bpp em ?", message)
    }

    @Test
    fun `the rebuild message carries the attempt that motivated it`() {
        assertEquals(
            "hud-alfa renascer :0.0 capaz=false profundidade=24bpp tentativa=2",
            hudAlphaRebuildMessage(state(deviceId = ":0.0", translucencyCapable = false, pixelSize = 24), attempt = 2)
        )
    }

    /**
     * O renascimento é a correção quando o AWT troca a configuração num
     * arrasto. Num ambiente que simplesmente não compõe alfa ele nunca voltaria,
     * e repetir seria um pisca eterno — depois do orçamento quem assume é o
     * recorte.
     */
    @Test
    fun `the rebuild budget is small enough not to become a loop`() {
        assertTrue(HUD_ALPHA_REBUILD_LIMIT in 1..5)
    }

    /**
     * O esgotamento só se percebia pela ausência de uma quarta tentativa no
     * `app.log` do relator da #416 (#430).
     */
    @Test
    fun `the exhausted budget has a line of its own`() {
        assertEquals(
            "hud-alfa sem orçamento :0.1 profundidade=24bpp tentativas=3; recorte de emergência assume",
            hudAlphaBudgetExhaustedMessage(state(translucencyCapable = false, pixelSize = 24), attempts = 3)
        )
    }

    /** A fábrica da trilha corta em 200: as linhas cabem inteiras. */
    @Test
    fun `every message survives the log sanitizer untouched`() {
        val longest = state(deviceId = "\\Display2", perPixelTranslucent = false, pixelSize = 24)
        for (message in listOf(
            hudTranslucencyStateMessage(longest),
            hudAlphaRebuildMessage(longest, attempt = HUD_ALPHA_REBUILD_LIMIT),
            hudAlphaBudgetExhaustedMessage(longest, attempts = HUD_ALPHA_REBUILD_LIMIT)
        )) {
            assertTrue(message.length <= Breadcrumb.MAX_MESSAGE_LENGTH, "${message.length}: $message")
            assertEquals(message, sanitizeBreadcrumbMessage(message))
        }
    }

    private fun state(
        deviceId: String? = ":0.1",
        perPixelTranslucent: Boolean? = true,
        translucencyCapable: Boolean? = true,
        pixelSize: Int? = 32,
        bounds: java.awt.Rectangle? = java.awt.Rectangle(100, 200, 592, 436)
    ) = HudTranslucencyState(deviceId, perPixelTranslucent, translucencyCapable, pixelSize, bounds)
}
