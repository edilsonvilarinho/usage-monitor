package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.graphics.Color
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A04 · #602: céu novo — 120 estrelas numa camada e 600 grãos, deriva horizontal de 1 dp/s, sem queda, sem rastro, sem brilho em cruz. */
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

    private fun grains() = hudStarfieldGrainFrame(width, height, wells, forbidden)

    private fun distance(x: Float, y: Float, well: HudStarfieldWell): Float {
        val dx = x - well.centerX
        val dy = y - well.centerY
        return sqrt(dx * dx + dy * dy)
    }

    private fun inside(box: HudStarfieldBox, x: Float, y: Float): Boolean =
        x >= box.left && x <= box.right && y >= box.top && y <= box.bottom

    /** Longe de todo poço: fora da influência (3 R), então sem deflexão. */
    private fun farFrom(x: Float, y: Float): Boolean = wells.all { distance(x, y, it) >= 3f * it.radius }

    @Test
    fun `o campo tem 120 estrelas numa camada so, com cerca de 28 por cento grandes`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(120, stars.size, "estrelas")
        val big = stars.count { it.radius >= 1.6f }
        assertTrue(big in 24..43, "estrelas grandes $big fora de 20 a 36 por cento de 120")
    }

    @Test
    fun `mesma semente gera o mesmo campo e outra semente gera outro`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(120, stars.size)
        assertEquals(stars, hudStarfieldStars(width, height, wells, forbidden))
        assertTrue(stars != hudStarfieldStars(width, height, wells, forbidden, seed = 7), "semente 7 deu o mesmo campo")
    }

    @Test
    fun `o laco e de 240 000 ms e a cintilacao em t e em t mais 240 000 ms e igual`() {
        assertEquals(240_000L, HUD_STARFIELD_PERIOD_MS, "periodo do laco")
        for (timeMs in listOf(0L, 1_000L, 37_123L, 82_499L, 200_000L)) {
            val first = frame(timeMs).map { it.twinkle }
            val second = frame(timeMs + loopMs).map { it.twinkle }
            assertEquals(first, second, "laco quebrado em t=$timeMs")
        }
    }

    @Test
    fun `o y0 sorteado cabe no corpo, de 0 a altura`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        for (star in stars) {
            assertTrue(star.y >= 0f && star.y < height, "y0 ${star.y} fora de [0, $height)")
        }
    }

    @Test
    fun `nenhuma estrela nasce dentro de disco de poco nem de caixa proibida`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        assertEquals(120, stars.size)
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
        val strip = listOf(HudStarfieldBox(left = 50f, top = 200f, right = 110f, bottom = 260f))
        val frames = sampleTimes.map { hudStarfieldFrame(it, width, height, emptyList(), strip) }
        val hidden = frames.sumOf { quadro -> quadro.count { it.alpha == 0f } }
        assertTrue(hidden > 0, "nenhuma estrela sumiu ao cruzar a faixa proibida")
    }

    @Test
    fun `sem queda vertical, a y de cada estrela longe dos pocos nao muda no tempo`() {
        val a = frame(20_000L)
        val b = frame(47_000L)
        assertEquals(120, a.size, "uma entrada por estrela em t=20 s")
        assertEquals(120, b.size, "uma entrada por estrela em t=47 s")
        var checked = 0
        for (i in a.indices) {
            if (!farFrom(a[i].x, a[i].y) || !farFrom(b[i].x, b[i].y)) continue
            checked++
            assertEquals(a[i].y, b[i].y, 1e-3f, "estrela $i caiu: y ${a[i].y} -> ${b[i].y}")
        }
        assertTrue(checked > 0, "nenhuma estrela longe dos pocos para checar")
    }

    @Test
    fun `cada estrela longe dos pocos anda 1 dp por segundo na horizontal`() {
        val t0 = 20_000L
        val a = frame(t0)
        val b = frame(t0 + 1_000L)
        var checked = 0
        for (i in a.indices) {
            if (!farFrom(a[i].x, a[i].y) || !farFrom(b[i].x, b[i].y)) continue
            checked++
            val moved = ((b[i].x - a[i].x) % width + width) % width
            assertTrue(abs(moved - 1f) <= 0.05f, "estrela $i andou $moved dp em 1 s, esperado 1")
        }
        assertTrue(checked > 0, "nenhuma estrela longe dos pocos para checar")
    }

    @Test
    fun `a deriva da borda direita volta pela esquerda, sem sair de 0 a largura`() {
        var wraps = 0
        var previous = frame(0L)
        for (second in 1..240) {
            val current = frame(second * 1_000L)
            for (i in current.indices) {
                if (!farFrom(current[i].x, current[i].y) || !farFrom(previous[i].x, previous[i].y)) continue
                assertTrue(current[i].x >= 0f && current[i].x <= width, "x ${current[i].x} fora de [0, $width) em t=${second}s")
                if (previous[i].x - current[i].x > width / 2f) wraps++
            }
            previous = current
        }
        assertTrue(wraps > 0, "nenhuma estrela deu volta na largura em 240 s")
    }

    @Test
    fun `grao visivel nao cai em caixa proibida e tem alfa de ate 0,5`() {
        val grains = grains()
        assertTrue(grains.any { it.alpha > 0f }, "nenhum grao visivel")
        for (grain in grains.filter { it.alpha > 0f }) {
            for (box in forbidden) {
                assertTrue(!inside(box, grain.x, grain.y), "grao visivel na caixa: ${grain.x},${grain.y}")
            }
            assertTrue(grain.alpha <= 0.5f + 1e-4f, "alfa de grao ${grain.alpha} acima de 0,5")
        }
    }

    @Test
    fun `o campo de graos tem 600 entradas e raio de 0,5 dp`() {
        val grains = grains()
        assertEquals(600, grains.size, "graos")
        assertTrue(grains.all { it.radius == 0.5f }, "raio de grao diferente de 0,5 dp")
    }

    @Test
    fun `a poeira usa o token dust nos dois temas, com alfa 1 no escuro e 1,2 no claro`() {
        assertEquals(AppGargantuaTokens.dust, hudStarfieldDustInk(dark = true).color, "poeira escura")
        assertEquals(AppGargantuaTokens.dust, hudStarfieldDustInk(dark = false).color, "poeira clara")
        assertEquals(1f, hudStarfieldDustInk(dark = true).alpha, 1e-4f, "alfa escuro da poeira")
        assertEquals(1.2f, hudStarfieldDustInk(dark = false).alpha, 1e-4f, "alfa claro da poeira")
    }

    @Test
    fun `cintilacao com periodo que divide 240 s, fator de 0,8 mais 0,2 seno e alfa acompanha`() {
        val stars = hudStarfieldStars(width, height, wells, forbidden)
        for (star in stars) {
            assertTrue(star.twinklePeriodMs in listOf(1_500L, 1_600L, 2_000L, 2_400L), "periodo ${star.twinklePeriodMs}")
            assertEquals(0L, loopMs % star.twinklePeriodMs, "periodo nao divide o laco")
        }
        var checked = 0
        for (timeMs in listOf(20_000L, 21_375L, 33_000L)) {
            val points = frame(timeMs)
            for (i in stars.indices) {
                val star = stars[i]
                val point = points[i]
                assertTrue(abs(point.twinkle - 0.8f) <= 0.2f + 1e-4f, "cintilacao fora de [0,6; 1]: ${point.twinkle}")
                val expected = 0.8f + 0.2f * sin(2.0 * PI * timeMs / star.twinklePeriodMs + star.twinklePhase).toFloat()
                assertEquals(expected, point.twinkle, 1e-3f, "fase da cintilacao em t=$timeMs")
                if (farFrom(point.x, point.y) && point.y in 40f..620f && point.alpha > 0f) {
                    checked++
                    assertEquals(star.alpha * 0.2f * point.twinkle, point.alpha, 1e-4f, "alfa com cintilacao em t=$timeMs")
                }
            }
        }
        assertTrue(checked > 0, "nenhum ponto longe e no meio para checar o alfa")
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
    fun `alfa de desenho no claro multiplica a luz e a atenuacao e no escuro fica o alfa da estrela`() {
        val boxes = listOf(HudStarfieldBox(left = 10f, top = 100f, right = 50f, bottom = 130f))
        val near = HudStarfieldPoint(x = 30f, y = 135f, radius = 1.5f, alpha = 0.9f, fade = 1f, twinkle = 1f)
        val far = near.copy(y = 300f)
        assertEquals(0.9f, hudStarfieldDrawAlpha(dark = true, point = near, forbidden = boxes), 1e-4f, "escuro perto de caixa")
        assertEquals(0.9f * 0.55f * 0.4f, hudStarfieldDrawAlpha(dark = false, point = near, forbidden = boxes), 1e-4f, "claro perto de caixa")
        assertEquals(0.9f * 0.55f, hudStarfieldDrawAlpha(dark = false, point = far, forbidden = boxes), 1e-4f, "claro longe de caixa")
    }

    @Test
    fun `tema escuro usa a cor quente ou o ouro com alfa e escala cheios`() {
        for (gold in listOf(false, true)) {
            val ink = hudStarfieldInk(dark = true, gold = gold)
            assertEquals(if (gold) AppGargantuaTokens.gold else AppGargantuaTokens.hot, ink.color, "cor escura gold=$gold")
            assertEquals(1f, ink.alpha, "alfa escuro gold=$gold")
            assertEquals(1f, ink.radiusScale, "escala escura gold=$gold")
        }
    }

    @Test
    fun `tema claro usa a tinta do fg com alfa 0,55 e raio vezes 1,5, dourada incluida`() {
        for (gold in listOf(false, true)) {
            val ink = hudStarfieldInk(dark = false, gold = gold)
            assertEquals(inkLight, ink.color, "tinta clara gold=$gold")
            assertEquals(0.55f, ink.alpha, 1e-4f, "alfa claro gold=$gold")
            assertEquals(1.5f, ink.radiusScale, "escala clara gold=$gold")
        }
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
