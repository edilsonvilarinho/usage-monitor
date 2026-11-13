package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.graphics.Color
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A02 · #586: quadro puro do campo estelar, sem Compose. */
class HudStarfieldTest {
    private val width = 110f
    private val height = 660f
    private val wells = listOf(
        HudStarfieldWell(centerX = 55f, centerY = 80f, radius = 24f),
        HudStarfieldWell(centerX = 55f, centerY = 300f, radius = 14f),
        HudStarfieldWell(centerX = 40f, centerY = 520f, radius = 20f)
    )
    private val forbidden = listOf(
        HudStarfieldBox(left = 4f, top = 40f, right = 106f, bottom = 66f),
        HudStarfieldBox(left = 4f, top = 340f, right = 90f, bottom = 370f)
    )
    private val sampleTimes = (0L until HUD_STARFIELD_PERIOD_MS step 1_375L).toList()
    private val inkLight = Color(0xFF171414)

    private fun frame(timeMs: Long) = hudStarfieldFrame(timeMs, width, height, wells, forbidden)

    private fun distance(x: Float, y: Float, well: HudStarfieldWell): Float {
        val dx = x - well.centerX
        val dy = y - well.centerY
        return sqrt(dx * dx + dy * dy)
    }

    private fun inside(box: HudStarfieldBox, x: Float, y: Float): Boolean =
        x >= box.left && x <= box.right && y >= box.top && y <= box.bottom

    @Test
    fun `o campo tem 140 estrelas, 30 perto, 45 no meio e 65 longe`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(140, stars.size)
        assertEquals(30, stars.count { it.layer == HudStarfieldLayer.NEAR })
        assertEquals(45, stars.count { it.layer == HudStarfieldLayer.MID })
        assertEquals(65, stars.count { it.layer == HudStarfieldLayer.FAR })
    }

    @Test
    fun `mesma semente gera o mesmo campo e outra semente gera outro`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(140, stars.size)
        assertEquals(stars, hudStarfieldStars(width, height, wells, forbidden))
        assertTrue(stars != hudStarfieldStars(width, height, wells, forbidden, seed = 7), "semente 7 deu o mesmo campo")
    }

    @Test
    fun `o quadro em t e em t mais 82500 ms e igual`() {
        assertEquals(140, frame(0L).size)
        for (timeMs in listOf(0L, 1_000L, 37_123L, 82_499L)) {
            assertEquals(frame(timeMs), frame(timeMs + HUD_STARFIELD_PERIOD_MS), "laco quebrado em t=$timeMs")
        }
    }

    @Test
    fun `nenhuma estrela nasce dentro de disco de poco nem de caixa proibida`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(140, stars.size)
        for (star in stars) {
            for (well in wells) {
                assertTrue(distance(star.x, star.y, well) >= well.radius + 3f, "estrela nasceu no disco: ${star.x},${star.y}")
            }
            for (box in forbidden) {
                assertTrue(!inside(box, star.x, star.y), "estrela nasceu na caixa: ${star.x},${star.y}")
            }
        }
    }

    @Test
    fun `em qualquer quadro nenhuma estrela visivel fica dentro de disco nem de caixa`() {
        assertEquals(140, frame(0L).size)
        for (timeMs in sampleTimes) {
            for (point in frame(timeMs).filter { it.alpha > 0f }) {
                for (well in wells) {
                    assertTrue(
                        distance(point.x, point.y, well) >= well.radius,
                        "t=$timeMs estrela dentro do disco: ${point.x},${point.y}"
                    )
                }
                for (box in forbidden) {
                    assertTrue(!inside(box, point.x, point.y), "t=$timeMs estrela visivel na caixa: ${point.x},${point.y}")
                }
            }
        }
    }

    @Test
    fun `estrela que cai numa caixa proibida some com alfa zero`() {
        val strip = listOf(HudStarfieldBox(left = 0f, top = 200f, right = 110f, bottom = 260f))
        val frames = sampleTimes.map { hudStarfieldFrame(it, width, height, emptyList(), strip) }
        assertEquals(140, frames.first().size)
        val hidden = frames.sumOf { quadro -> quadro.count { it.alpha == 0f } }
        assertTrue(hidden > 0, "nenhuma estrela sumiu ao cruzar a faixa proibida")
    }

    @Test
    fun `longe de pocos o rastro e zero e a posicao e a deriva reta`() {
        val timeMs = 20_000L
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        val points = frame(timeMs)
        assertEquals(140, points.size)
        val phase = timeMs.toFloat() / HUD_STARFIELD_PERIOD_MS
        for ((index, star) in stars.withIndex()) {
            val speed = when (star.layer) {
                HudStarfieldLayer.NEAR -> 4
                HudStarfieldLayer.MID -> 2
                HudStarfieldLayer.FAR -> 1
            }
            val rawY = star.y + speed * height * phase
            val driftY = ((rawY % height) + height) % height
            val far = wells.all { distance(star.x, driftY, it) >= 3f * it.radius }
            if (!far) continue
            val point = points[index]
            assertEquals(0f, point.trailX, "rastro fora do poco em $index")
            assertEquals(0f, point.trailY, "rastro fora do poco em $index")
            assertTrue(kotlin.math.abs(point.x - star.x) < 1e-3f, "x desviado longe do poco em $index")
            assertTrue(kotlin.math.abs(point.y - driftY) < 1e-3f, "y fora da deriva reta em $index")
        }
    }

    @Test
    fun `perto de um poco a distancia ao centro e no minimo R e o rastro tem de 4 a 6 px`() {
        assertEquals(140, frame(0L).size)
        var withTrail = 0
        for (timeMs in sampleTimes) {
            for (point in frame(timeMs)) {
                val trail = sqrt(point.trailX * point.trailX + point.trailY * point.trailY)
                if (trail == 0f) continue
                assertTrue(point.layer != HudStarfieldLayer.FAR, "longe com rastro em t=$timeMs")
                assertTrue(trail in 4f..6f, "rastro de $trail px em t=$timeMs")
                withTrail++
            }
        }
        assertTrue(withTrail > 0, "nenhum rastro em nenhum quadro")
    }

    @Test
    fun `tema escuro usa a cor quente com alfa e escala cheios em todas as camadas`() {
        for (layer in listOf(HudStarfieldLayer.NEAR, HudStarfieldLayer.MID, HudStarfieldLayer.FAR)) {
            val ink = hudStarfieldInk(dark = true, layer = layer, gold = false)
            assertEquals(AppGargantuaTokens.hot, ink.color, "cor escura na camada $layer")
            assertEquals(1f, ink.alpha, "alfa escuro na camada $layer")
            assertEquals(1f, ink.radiusScale, "escala escura na camada $layer")
        }
    }

    @Test
    fun `tema escuro dourado usa o ouro do token`() {
        val ink = hudStarfieldInk(dark = true, layer = HudStarfieldLayer.NEAR, gold = true)
        assertEquals(AppGargantuaTokens.gold, ink.color, "estrela dourada escura")
    }

    @Test
    fun `tema claro usa a tinta do fg com alfa da L1 por camada e raio vezes 1,5`() {
        val expected = listOf(
            HudStarfieldLayer.NEAR to 0.95f,
            HudStarfieldLayer.MID to 0.7f,
            HudStarfieldLayer.FAR to 0.45f
        )
        for ((layer, alpha) in expected) {
            val ink = hudStarfieldInk(dark = false, layer = layer, gold = false)
            assertEquals(inkLight, ink.color, "tinta clara na camada $layer")
            assertEquals(alpha, ink.alpha, "alfa claro na camada $layer")
            assertEquals(1.5f, ink.radiusScale, "escala clara na camada $layer")
        }
    }

    @Test
    fun `tema claro dourado usa a mesma tinta do fg`() {
        val ink = hudStarfieldInk(dark = false, layer = HudStarfieldLayer.NEAR, gold = true)
        assertEquals(inkLight, ink.color, "estrela dourada clara")
    }

    @Test
    fun `caixa menos anel nao cobre o anel e cobre o resto da caixa`() {
        val item = HudStarfieldBox(left = 0f, top = 0f, right = 100f, bottom = 40f)
        val ring = HudStarfieldBox(left = 30f, top = 5f, right = 70f, bottom = 35f)
        val pieces = hudStarfieldRemainder(item, ring)
        for (i in 0 until 20) {
            for (j in 0 until 8) {
                val x = 2.5f + 5f * i
                val y = 2.5f + 5f * j
                val covered = pieces.any { inside(it, x, y) }
                if (inside(ring, x, y)) {
                    assertTrue(!covered, "anel sem proibido em ($x, $y)")
                } else {
                    assertTrue(covered, "resto da caixa proibido em ($x, $y)")
                }
            }
        }
    }
}
