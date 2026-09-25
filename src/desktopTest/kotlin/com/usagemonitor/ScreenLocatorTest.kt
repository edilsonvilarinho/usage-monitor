package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A escolha do monitor pela posição (issue #273), com monitores sintéticos: o
 * primário em (0, 0) e secundários à direita, à esquerda (x negativo) e acima.
 * Nenhum destes casos existia antes — todo teste de tela usava uma área só, na
 * origem, e foi assim que "só o monitor principal" passou despercebido.
 */
class ScreenLocatorTest {

    private fun screen(id: String, x: Int, y: Int, width: Int, height: Int, taskbar: Int = 40): ScreenInfo {
        val bounds = ScreenWorkArea(x.dp, y.dp, DpSize(width.dp, height.dp))
        val work = ScreenWorkArea(x.dp, y.dp, DpSize(width.dp, (height - taskbar).dp))
        return ScreenInfo(id, bounds, work)
    }

    private val primary = screen("\\Display1", 0, 0, 1920, 1080)
    private val right = screen("\\Display2", 1920, 0, 2560, 1440)
    private val left = screen("\\Display3", -1280, 0, 1280, 1024)
    private val above = screen("\\Display4", 0, -900, 1600, 900)
    private val screens = listOf(primary, right, left, above)

    @Test
    fun `o ponto escolhe o monitor que o contem`() {
        assertEquals(primary, screenAt(screens, 100.dp, 100.dp))
        assertEquals(right, screenAt(screens, 2000.dp, 700.dp))
        assertEquals(left, screenAt(screens, (-10).dp, 500.dp))
        assertEquals(above, screenAt(screens, 800.dp, (-1).dp))
        assertNull(screenAt(screens, 9000.dp, 9000.dp))
    }

    @Test
    fun `a janela fica no monitor com mais area dela`() {
        val size = DpSize(800.dp, 600.dp)
        assertEquals(right, screenForRect(screens, 2100.dp, 100.dp, size))
        assertEquals(left, screenForRect(screens, (-900).dp, 100.dp, size))
        // Atravessando a divisa: 700dp no primário, 100dp no da direita.
        assertEquals(primary, screenForRect(screens, 1220.dp, 100.dp, size))
        // Fora de todos: quem chama cai no primário.
        assertNull(screenForRect(screens, 20_000.dp, 20_000.dp, size))
    }

    /**
     * A área útil devolvida é a do monitor da janela: era aqui que a posição salva
     * num secundário era presa ao primário ao reabrir.
     */
    @Test
    fun `a posicao salva num secundario e encaixada nele`() {
        val size = DpSize(800.dp, 600.dp)
        val area = workAreaForPosition(2100.dp, 100.dp, size, fallback = primary.workArea, screens = screens)
        val position = fitWindowPosition(2100.dp, 100.dp, size, area)

        assertEquals(right.workArea, area)
        assertEquals(2100.dp, position.x)
        assertEquals(100.dp, position.y)
    }

    @Test
    fun `sem posicao ou fora de todos os monitores vale o primario`() {
        val size = DpSize(800.dp, 600.dp)
        assertEquals(primary.workArea, workAreaForPosition(null, null, size, primary.workArea, screens))
        assertEquals(primary.workArea, workAreaForPosition(20_000.dp, 0.dp, size, primary.workArea, screens))
    }

    @Test
    fun `o monitor gravado volta pelo id`() {
        assertEquals(right, resolveScreen(screens, "\\Display2", right.bounds))
    }

    /** O Windows renumera ao reconectar: o id aponta para outro monitor, os limites não mentem. */
    @Test
    fun `id trocado com os mesmos limites acha o monitor pelos limites`() {
        val renumbered = listOf(primary, right.copy(id = "\\Display5"), left.copy(id = "\\Display2"))

        assertEquals("\\Display5", resolveScreen(renumbered, "\\Display2", right.bounds)?.id)
    }

    @Test
    fun `monitor que sumiu nao resolve e nao inventa outro`() {
        assertNull(resolveScreen(listOf(primary), "\\Display2", right.bounds))
        assertNull(resolveScreen(listOf(primary), null, null))
    }

    /** O notch solto no secundário gruda numa borda **dele**, com a fração dele. */
    @Test
    fun `o encaixe usa as bordas do monitor em que o notch foi solto`() {
        val placement = nearestHudPlacement(centerX = 1920.dp + 640.dp, centerY = 10.dp, area = right.bounds)

        assertEquals(HudEdge.TOP, placement.edge)
        assertEquals(0.25f, placement.offsetFraction, 0.001f)
    }
}
