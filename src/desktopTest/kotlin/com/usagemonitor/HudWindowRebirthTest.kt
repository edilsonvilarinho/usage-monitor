package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.data.diagnostics.AppLogFile
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O renascimento da janela da HUD quando o alfa cai (#426) e a adoção do
 * monitor onde o compositor pôs a janela, só no COSMIC (#430).
 *
 * Os monitores são os do relator da #416, deduzidos de onde o COSMIC
 * centralizou a janela: dois de 1920 px lado a lado.
 */
class HudWindowRebirthTest {

    private val primary = screen(":0.0", x = 0)
    private val secondary = screen(":0.1", x = 1920)
    private val screens = listOf(primary, secondary)

    /** O ponto exato em que o COSMIC pôs a janela no pacote da #416. */
    private val onSecondary = java.awt.Rectangle(2651, 163, 459, 368)

    /**
     * Comportamento nativo medido numa plataforma nasce restrito a ela (#340):
     * o defeito foi visto no COSMIC, e no KWin a posição pedida é respeitada.
     */
    @Test
    fun `only COSMIC on Linux follows the compositor`() {
        val linux = AutoStartManager.Platform.LINUX
        assertTrue(hudFollowsCompositorScreen(linux, "COSMIC"))
        assertTrue(hudFollowsCompositorScreen(linux, "pop:COSMIC"))
        assertTrue(hudFollowsCompositorScreen(linux, "cosmic"))
        for (desktop in listOf("KDE", "GNOME", "ubuntu:GNOME", "Pantheon", "X-Cinnamon", "", null)) {
            assertFalse(hudFollowsCompositorScreen(linux, desktop), "$desktop")
        }
        for (platform in listOf(AutoStartManager.Platform.WINDOWS, AutoStartManager.Platform.MACOS, AutoStartManager.Platform.OTHER)) {
            assertFalse(hudFollowsCompositorScreen(platform, "COSMIC"), "$platform")
        }
    }

    @Test
    fun `the rebirth screen is where most of the window is`() {
        assertEquals(secondary, hudRebirthScreen(true, ":0.0", onSecondary, screens))
        // Atravessando a divisa, vale o monitor com a maior parte dela.
        assertEquals(secondary, hudRebirthScreen(true, ":0.0", java.awt.Rectangle(1800, 0, 459, 368), screens))
        assertEquals(primary, hudRebirthScreen(true, ":0.1", java.awt.Rectangle(1600, 0, 459, 368), screens))
    }

    @Test
    fun `the rebirth screen keeps the configured one in every other case`() {
        assertNull(hudRebirthScreen(false, ":0.0", onSecondary, screens), "fora do COSMIC")
        assertNull(hudRebirthScreen(true, ":0.1", onSecondary, screens), "já é o configurado")
        assertNull(hudRebirthScreen(true, ":0.0", null, screens), "posição não medida")
        assertNull(hudRebirthScreen(true, ":0.0", java.awt.Rectangle(9000, 9000, 10, 10), screens), "fora de todos")
        assertNull(hudRebirthScreen(true, ":0.0", onSecondary, emptyList()), "sem monitores")
    }

    /**
     * Fora do COSMIC o renascimento é o da #426: nunca adota, gasta o
     * orçamento e, agora, diz que ele acabou.
     */
    @Test
    fun `outside COSMIC the rebirth never adopts and the exhausted budget is logged once`() = withLog { log, lines ->
        val rebirth = HudWindowRebirth(explicit = true, followsCompositor = false, appLog = log)
        val adopted = mutableListOf<ScreenInfo>()

        runBlocking {
            repeat(HUD_ALPHA_REBUILD_LIMIT + 2) {
                rebirth.onAlphaLost(lost(onSecondary), dragging = false, configuredScreenId = ":0.0", screens = { screens }) { screen ->
                    adopted += screen
                }
            }
        }

        assertEquals(HUD_ALPHA_REBUILD_LIMIT, rebirth.generation)
        assertTrue(adopted.isEmpty())
        val messages = lines()
        assertEquals(HUD_ALPHA_REBUILD_LIMIT, messages.count { line -> "hud-alfa renascer" in line })
        assertEquals(1, messages.count { line -> "hud-alfa sem orçamento" in line })
        assertTrue(messages.none { line -> "adotar" in line })
    }

    /** O caso do relator: a janela está no secundário, a configuração era do primário. */
    @Test
    fun `on COSMIC the rebirth adopts the screen the window is on`() = withLog { log, lines ->
        val rebirth = HudWindowRebirth(explicit = true, followsCompositor = true, appLog = log)
        val adopted = mutableListOf<ScreenInfo>()

        runBlocking {
            rebirth.onAlphaLost(lost(onSecondary), dragging = false, configuredScreenId = ":0.0", screens = { screens }) { screen ->
                adopted += screen
            }
        }

        assertEquals(listOf(secondary), adopted)
        assertEquals(1, rebirth.generation)
        assertEquals(
            listOf("hud-alfa adotar :0.0->:0.1 (a janela está em 2651,163) profundidade=24bpp tentativa=1"),
            lines()
        )
    }

    /**
     * A troca de monitor que a própria adoção causou não zera o orçamento: um
     * compositor que alternasse os monitores viraria um pisca eterno.
     */
    @Test
    fun `adopting does not refill the budget, so alternating screens still stops`() = withLog { log, _ ->
        val rebirth = HudWindowRebirth(explicit = true, followsCompositor = true, appLog = log)
        var configured = ":0.0"

        runBlocking {
            repeat(HUD_ALPHA_REBUILD_LIMIT + 3) {
                val elsewhere = if (configured == ":0.0") onSecondary else java.awt.Rectangle(731, 163, 459, 368)
                rebirth.onAlphaLost(lost(elsewhere), dragging = false, configuredScreenId = configured, screens = { screens }) { screen ->
                    configured = screen.id
                    rebirth.onScreenChanged(screen.id)
                }
            }
        }

        assertEquals(HUD_ALPHA_REBUILD_LIMIT, rebirth.generation)
    }

    /** Soltar a barra noutro monitor é escolha do usuário: o orçamento volta inteiro. */
    @Test
    fun `a screen change from outside the adoption refills the budget`() = withLog { log, _ ->
        val rebirth = HudWindowRebirth(explicit = true, followsCompositor = false, appLog = log)

        runBlocking {
            repeat(HUD_ALPHA_REBUILD_LIMIT) { rebirth.onAlphaLost(lost(onSecondary), dragging = false) }
            rebirth.onScreenChanged(":0.1")
            rebirth.onAlphaLost(lost(onSecondary), dragging = false)
        }

        assertEquals(HUD_ALPHA_REBUILD_LIMIT + 1, rebirth.generation)
    }

    @Test
    fun `no rebirth during a drag, nor outside Linux`() = withLog { log, lines ->
        val linux = HudWindowRebirth(explicit = true, followsCompositor = true, appLog = log)
        val windows = HudWindowRebirth(explicit = false, followsCompositor = false, appLog = log)

        runBlocking {
            linux.onAlphaLost(lost(onSecondary), dragging = true, configuredScreenId = ":0.0", screens = { screens })
            windows.onAlphaLost(lost(onSecondary), dragging = false, configuredScreenId = ":0.0", screens = { screens })
        }

        assertEquals(0, linux.generation)
        assertEquals(0, windows.generation)
        assertTrue(lines().isEmpty())
    }

    @Test
    fun `the adoption line survives the log sanitizer untouched`() {
        val message = hudAlphaAdoptionMessage(
            lost(java.awt.Rectangle(-12345, -12345, 459, 368)),
            fromScreenId = "\\Display12",
            toScreenId = "\\Display13",
            attempt = HUD_ALPHA_REBUILD_LIMIT
        )

        assertTrue(message.length <= com.usagemonitor.domain.entity.Breadcrumb.MAX_MESSAGE_LENGTH, message)
        assertEquals(message, com.usagemonitor.domain.entity.sanitizeBreadcrumbMessage(message))
    }

    private fun lost(bounds: java.awt.Rectangle) = HudTranslucencyState(
        deviceId = ":0.0",
        perPixelTranslucent = true,
        translucencyCapable = false,
        pixelSize = 24,
        bounds = bounds
    )

    private fun screen(id: String, x: Int): ScreenInfo {
        val area = ScreenWorkArea(x = x.dp, y = 0.dp, size = DpSize(1920.dp, 1080.dp))
        return ScreenInfo(id = id, bounds = area, workArea = area)
    }

    /** Log num arquivo temporário: o teste não escreve no `app.log` de quem o roda. */
    private fun withLog(block: (AppLogFile, () -> List<String>) -> Unit) {
        val file = File.createTempFile("hud-rebirth-test", ".log")
        try {
            file.writeText("")
            val messages = {
                file.readLines()
                    .filter { line -> line.isNotBlank() }
                    .map { line -> line.substringAfter("\"message\":\"").substringBefore("\"") }
            }
            block(AppLogFile(logFile = file), messages)
        } finally {
            file.delete()
        }
    }
}
