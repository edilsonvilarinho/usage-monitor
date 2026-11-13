package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.graphics.Color
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A02 · #590: campo lento da AW1 — três camadas, laço de 240 s, cintilação, brilho em cruz e atenuação no claro. */
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
    private val loopMs = 240_000L
    private val sampleTimes = (0L until loopMs step 1_375L).toList()
    private val inkLight = Color(0xFF171414)

    private fun frame(timeMs: Long) = hudStarfieldFrame(timeMs, width, height, wells, forbidden)

    private fun speedOf(layer: HudStarfieldLayer): Float = when (layer) {
        HudStarfieldLayer.NEAR -> 3f
        HudStarfieldLayer.MID -> 1.5f
        HudStarfieldLayer.FAR -> 0.5f
    }

    private fun tileOf(layer: HudStarfieldLayer): Float = speedOf(layer) * 240f

    private fun distance(x: Float, y: Float, well: HudStarfieldWell): Float {
        val dx = x - well.centerX
        val dy = y - well.centerY
        return sqrt(dx * dx + dy * dy)
    }

    private fun inside(box: HudStarfieldBox, x: Float, y: Float): Boolean =
        x >= box.left && x <= box.right && y >= box.top && y <= box.bottom

    @Test
    fun `o campo tem 156 estrelas, 60 perto, 56 no meio e 40 longe`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(156, stars.size)
        assertEquals(60, stars.count { it.layer == HudStarfieldLayer.NEAR })
        assertEquals(56, stars.count { it.layer == HudStarfieldLayer.MID })
        assertEquals(40, stars.count { it.layer == HudStarfieldLayer.FAR })
    }

    @Test
    fun `mesma semente gera o mesmo campo e outra semente gera outro`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(156, stars.size)
        assertEquals(stars, hudStarfieldStars(width, height, wells, forbidden))
        assertTrue(stars != hudStarfieldStars(width, height, wells, forbidden, seed = 7), "semente 7 deu o mesmo campo")
    }

    @Test
    fun `o laco e de 240 000 ms e o quadro em t e em t mais 240 000 ms e igual`() {
        assertEquals(240_000L, HUD_STARFIELD_PERIOD_MS, "periodo do laco")
        for (timeMs in listOf(0L, 1_000L, 37_123L, 82_499L, 200_000L)) {
            assertEquals(frame(timeMs), frame(timeMs + loopMs), "laco quebrado em t=$timeMs")
        }
    }

    @Test
    fun `o y0 sorteado cabe em min(tile, altura) de cada camada`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        for (star in stars) {
            val limit = min(tileOf(star.layer), height)
            assertTrue(star.y >= 0f && star.y < limit, "y0 ${star.y} fora de [0, $limit) na camada ${star.layer}")
        }
    }

    @Test
    fun `nenhuma estrela nasce dentro de disco de poco nem de caixa proibida`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(156, stars.size)
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
        val hidden = frames.sumOf { quadro -> quadro.count { it.alpha == 0f } }
        assertTrue(hidden > 0, "nenhuma estrela sumiu ao cruzar a faixa proibida")
    }

    @Test
    fun `o deslocamento em 1 s longe de poco e 3, 1,5 e 0,5 dp por camada`() {
        val t0 = 20_000L
        for (star in hudStarfieldStars(width, height, wells, forbidden)) {
            val tile = tileOf(star.layer)
            val expected = speedOf(star.layer)
            val moved = hudStarfieldDriftY(star, t0 + 1_000L) - hudStarfieldDriftY(star, t0)
            val wrapped = ((moved % tile) + tile) % tile
            assertTrue(abs(wrapped - expected) <= 0.05f, "deslocamento $wrapped dp na camada ${star.layer}, esperado $expected")
        }
    }

    @Test
    fun `longe de pocos cada copia cai na deriva, sem desvio nem rastro`() {
        val timeMs = 20_000L
        val points = frame(timeMs)
        var checked = 0
        for (star in hudStarfieldStars(width, height, wells, forbidden)) {
            val tile = tileOf(star.layer)
            val drift = hudStarfieldDriftY(star, timeMs)
            val copies = (0..6).map { drift + it * tile }.filter { it < height }.sorted()
            val far = copies.all { y -> wells.all { distance(star.x, y, it) >= 3f * it.radius } }
            if (!far) continue
            checked++
            val own = points.filter { it.x == star.x && it.layer == star.layer }.sortedBy { it.y }
            assertEquals(copies.size, own.size, "copias da estrela em x=${star.x}")
            for ((expectedY, point) in copies.zip(own)) {
                assertTrue(abs(point.y - expectedY) < 1e-3f, "copia fora da deriva: ${point.y} x $expectedY")
                assertEquals(0f, point.trailX, "rastro longe do poco")
                assertEquals(0f, point.trailY, "rastro longe do poco")
            }
        }
        assertTrue(checked > 0, "nenhuma estrela longe dos pocos para checar")
    }

    @Test
    fun `perto de um poco a distancia ao centro e no minimo R e o rastro tem de 4 a 6 px`() {
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
    fun `cintilacao com periodo de 3 a 6 s que divide 240 s e alfa dentro de 15 por cento`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        for (star in stars) {
            assertTrue(star.twinklePeriodMs in listOf(3_000L, 4_000L, 5_000L, 6_000L), "periodo ${star.twinklePeriodMs}")
            assertEquals(0L, loopMs % star.twinklePeriodMs, "periodo nao divide o laco")
        }
        var checked = 0
        for (timeMs in listOf(20_000L, 21_375L, 33_000L)) {
            val points = frame(timeMs)
            for (star in stars) {
                for (point in points.filter { it.x == star.x && it.layer == star.layer }) {
                    assertTrue(abs(point.twinkle - 1f) <= 0.15f + 1e-4f, "cintilacao fora de 15%: ${point.twinkle}")
                    val expected = 1f + 0.15f * sin(2.0 * PI * timeMs / star.twinklePeriodMs + star.twinklePhase).toFloat()
                    assertEquals(expected, point.twinkle, 1e-3f, "fase da cintilacao em t=$timeMs")
                    val far = wells.all { distance(point.x, point.y, it) >= 3f * it.radius }
                    val middle = point.y in 40f..620f
                    if (far && middle && point.alpha > 0f) {
                        checked++
                        assertEquals(min(1f, star.alpha * point.twinkle), point.alpha, 1e-4f, "alfa com cintilacao em t=$timeMs")
                    }
                }
            }
        }
        assertTrue(checked > 0, "nenhum ponto longe e no meio para checar o alfa")
    }

    @Test
    fun `exatamente seis estrelas com brilho em cruz, as seis maiores de perto`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        val glints = stars.filter { it.glint }
        assertEquals(6, glints.size, "brilhos em cruz")
        assertTrue(glints.all { it.layer == HudStarfieldLayer.NEAR }, "brilho fora das estrelas de perto")
        val topRadii = stars.filter { it.layer == HudStarfieldLayer.NEAR }.map { it.radius }.sortedDescending().take(6)
        assertEquals(topRadii, glints.map { it.radius }.sortedDescending(), "brilho nas seis maiores de perto")
    }

    @Test
    fun `atenuacao do claro e x0,4 a ate 8 dp de caixa proibida e so no claro`() {
        val boxes = listOf(HudStarfieldBox(left = 10f, top = 100f, right = 50f, bottom = 130f))
        assertEquals(0.4f, hudStarfieldTextAttenuation(dark = false, x = 30f, y = 135f, forbidden = boxes), "5 dp abaixo da caixa")
        assertEquals(0.4f, hudStarfieldTextAttenuation(dark = false, x = 56f, y = 105f, forbidden = boxes), "6 dp a direita da caixa")
        assertEquals(0.4f, hudStarfieldTextAttenuation(dark = false, x = 54f, y = 134f, forbidden = boxes), "diagonal a 5,7 dp")
        assertEquals(1f, hudStarfieldTextAttenuation(dark = false, x = 30f, y = 139f, forbidden = boxes), "9 dp abaixo: fora do alcance")
        assertEquals(1f, hudStarfieldTextAttenuation(dark = false, x = 60f, y = 140f, forbidden = boxes), "diagonal a 14 dp")
        assertEquals(1f, hudStarfieldTextAttenuation(dark = true, x = 30f, y = 135f, forbidden = boxes), "escuro nao atenua")
    }

    @Test
    fun `alfa de desenho no claro multiplica a atenuacao e no escuro fica o alfa da estrela`() {
        val boxes = listOf(HudStarfieldBox(left = 10f, top = 100f, right = 50f, bottom = 130f))
        val near = HudStarfieldPoint(
            x = 30f, y = 135f, radius = 1.5f, alpha = 0.9f, layer = HudStarfieldLayer.NEAR,
            trailX = 0f, trailY = 0f, fade = 1f, twinkle = 1f
        )
        val far = near.copy(y = 300f)
        assertEquals(0.9f, hudStarfieldDrawAlpha(dark = true, point = near, forbidden = boxes), 1e-4f, "escuro perto de caixa")
        assertEquals(0.95f * 0.4f, hudStarfieldDrawAlpha(dark = false, point = near, forbidden = boxes), 1e-4f, "claro perto de caixa")
        assertEquals(0.95f, hudStarfieldDrawAlpha(dark = false, point = far, forbidden = boxes), 1e-4f, "claro longe de caixa")
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
