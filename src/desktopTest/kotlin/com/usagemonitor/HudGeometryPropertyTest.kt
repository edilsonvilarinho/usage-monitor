package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Propriedades de geometria da HUD (docs/hud-notch.md). Semente fixa: local e CI rodam as mesmas entradas. */
class HudGeometryPropertyTest {

    @Test
    fun forAllRepeatsTheSameInputsForTheSameSeed() {
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()
        forAll(seed = 42L, iterations = 5, generator = { it.nextInt() }, assertion = { first.add(it) })
        forAll(seed = 42L, iterations = 5, generator = { it.nextInt() }, assertion = { second.add(it) })
        assertTrue(first.size == 5, "forAll deve chamar a asserção 5 vezes, chamou ${first.size}")
        assertEquals(first, second, "mesma semente deve dar a mesma sequência de entradas")
    }

    @Test
    fun forAllFailureNamesSeedIterationAndInput() {
        val error = assertFailsWith<AssertionError> {
            forAll(seed = 7L, iterations = 10, generator = { it.nextInt(0, 100) }, assertion = { value ->
                assertTrue(value < 0, "valor $value")
            })
        }
        val message = error.message.orEmpty()
        assertTrue(message.contains("semente=7"), "mensagem sem semente: $message")
        assertTrue(message.contains("iteração="), "mensagem sem iteração: $message")
        assertTrue(message.contains("entrada="), "mensagem sem entrada: $message")
    }

    /**
     * docs/hud-notch.md (notch parado na área útil): encaixada e parada, a janela fica dentro da área útil.
     * Fora da condição (sombra não cabe): ex. borda=RIGHT, área=1534.39 x 343.99 dp, janela 348 dp > 344; pulado e contado.
     */
    @Test
    fun dockedWindowStaysInsideWorkArea() {
        val count = forAllHudWhen(SEED, ITERATIONS, { s -> dockedFits(s) }) { s ->
            val window = hudDockedWindowBounds(s.edge, s.offsetFraction, sizesOf(s), s.areaDp)
            assertInside("docs/hud-notch.md (notch parado na área útil): janela encaixada fora da área útil", window, s.areaDp)
        }
        assertSkipWithinLimit("docs/hud-notch.md (notch parado na área útil)", count)
    }

    /**
     * docs/hud-notch.md (janela de origem e tamanho fixos ao abrir): abrir não move a janela. A função não recebe estado aberto/fechado:
     * a prova é a independência de `collapsed`, o único tamanho que difere entre os dois estados.
     */
    @Test
    fun windowOriginAndSizeSameOpenAndClosed() {
        forAllHud { s ->
            val sizes = sizesOf(s)
            val open = hudDockedWindowBounds(s.edge, s.offsetFraction, sizes, s.areaDp)
            val closed = hudDockedWindowBounds(
                s.edge, s.offsetFraction, sizes.copy(collapsed = DpSize(1.dp, 1.dp)), s.areaDp
            )
            assertEquals(open, closed, "docs/hud-notch.md (janela de origem e tamanho fixos ao abrir): origem ou tamanho depende do estado fechado")
        }
    }

    /**
     * docs/hud-notch.md (alças nunca fora da tela): as alças cabem na tela, centradas no notch.
     * Fora da condição (sombra não cabe): ex. borda=BOTTOM, área=921.82 dp, alcance 940 + 32 dp; pulado e contado.
     */
    @Test
    fun handlesInsideScreen() {
        val count = forAllHudWhen(SEED, ITERATIONS, { s -> handlesFit(s) }) { s ->
            val sizes = sizesOf(s)
            val window = hudDockedWindowBounds(s.edge, s.offsetFraction, sizes, s.areaDp)
            val horizontal = s.edge.isHorizontal
            val reach = (if (horizontal) sizes.withHandles.width else sizes.withHandles.height).value
            val center = (if (horizontal) window.x else window.y).value + window.notchCenterInWindow.value
            val start = (if (horizontal) s.areaDp.x else s.areaDp.y).value
            val length = (if (horizontal) s.areaDp.size.width else s.areaDp.size.height).value
            assertTrue(
                center - reach / 2 >= start - EPS && center + reach / 2 <= start + length + EPS,
                "docs/hud-notch.md (alças nunca fora da tela): alças fora da tela: centro=$center alcance=$reach área=[$start, ${start + length}]"
            )
        }
        assertSkipWithinLimit("docs/hud-notch.md (alças nunca fora da tela)", count)
    }

    /** Janela encaixada com a sombra cabe na área: ao longo da borda `expanded` + 2 margens, na outra `expanded` + 1 (borda colada, sem sombra). */
    private fun dockedFits(s: HudScenario): Boolean {
        val expanded = sizesOf(s).expanded
        val margin = HUD_SHADOW_MARGIN
        val along = if (s.edge.isHorizontal) expanded.width else expanded.height
        val across = if (s.edge.isHorizontal) expanded.height else expanded.width
        val areaAlong = if (s.edge.isHorizontal) s.areaDp.size.width else s.areaDp.size.height
        val areaAcross = if (s.edge.isHorizontal) s.areaDp.size.height else s.areaDp.size.width
        return along + margin * 2 <= areaAlong && across + margin <= areaAcross
    }

    /** Alças com a sombra cabem ao longo da borda: `withHandles` + 2 margens, como hudWindowBounds reserva. */
    private fun handlesFit(s: HudScenario): Boolean {
        val withHandles = sizesOf(s).withHandles
        val along = if (s.edge.isHorizontal) withHandles.width else withHandles.height
        val areaAlong = if (s.edge.isHorizontal) s.areaDp.size.width else s.areaDp.size.height
        return along + HUD_SHADOW_MARGIN * 2 <= areaAlong
    }

    /** docs/hud-notch.md (regra da faixa compacta): a faixa vira compacta exatamente quando há contas e a completa passa do teto. */
    @Test
    fun compactIffFullStripExceedsMax() {
        forAllHud { s ->
            val maxAlong = hudMaxAlong(s.edge, s.area, s.scale)
            val accounts = applyHudAutoMinimize(s.accounts, s.edge, FALLBACK_LABEL, maxAlong)
            val full = hudNotchSizes(accounts, s.edge, FALLBACK_LABEL, s.hasUpdateIndicator, Dp.Infinity, s.hasUpdateAction, s.area.size.height / s.scale)
            val fullAlong = if (s.edge.isHorizontal) full.collapsed.width else full.collapsed.height
            val expected = accounts.isNotEmpty() && fullAlong > maxAlong
            assertEquals(
                expected, sizesOf(s).compact,
                "docs/hud-notch.md (regra da faixa compacta): compacta fora da regra: completa=$fullAlong teto=$maxAlong"
            )
        }
    }

    /** docs/hud-notch.md (janela flutuante sempre na área útil): a janela flutuante fica dentro da área útil. */
    @Test
    fun floatingWindowInsideWorkArea() {
        forAll(SEED, ITERATIONS, ::generateFloatingScenario) { s ->
            val window = hudFloatingBounds(
                s.center, s.notchCenter, s.windowWidthPx, s.windowHeightPx, s.workArea, s.anchorRadiusPx
            ).window
            val area = s.workArea
            assertTrue(
                window.xPx >= area.xPx && window.xPx + window.widthPx <= area.xPx + area.widthPx &&
                    window.yPx >= area.yPx && window.yPx + window.heightPx <= area.yPx + area.heightPx,
                "docs/hud-notch.md (janela flutuante sempre na área útil): janela flutuante fora da área útil: $window área=$area"
            )
        }
    }

    /** Sem linha no doc: todo tamanho e posição da geometria são finitos e não negativos. */
    @Test
    fun sizesAreFiniteAndNeverNegative() {
        forAllHud { s ->
            val sizes = sizesOf(s)
            listOf(sizes.collapsed, sizes.expanded, sizes.balloon, sizes.withHandles).forEach { size ->
                assertFiniteNonNegative("tamanho de notch", size.width, size.height)
            }
            listOf(
                hudDockedWindowBounds(s.edge, s.offsetFraction, sizes, s.areaDp),
                hudDragWindowBounds(s.edge, s.offsetFraction, sizes, s.areaDp)
            ).forEach { window ->
                assertFinite("janela x", window.x)
                assertFinite("janela y", window.y)
                assertFiniteNonNegative("janela", window.size.width, window.size.height)
                assertFinite("centro do notch na janela", window.notchCenterInWindow)
                assertTrue(window.notchCenterInWindow.value >= 0f, "centro do notch negativo: ${window.notchCenterInWindow}")
            }
        }
    }

    /** Sem linha no doc: mesma entrada, mesma saída. */
    @Test
    fun geometryIsDeterministic() {
        forAllHud { s ->
            assertEquals(sizesOf(s), sizesOf(s), "hudNotchSizes não determinístico")
            val sizes = sizesOf(s)
            assertEquals(
                hudDockedWindowBounds(s.edge, s.offsetFraction, sizes, s.areaDp),
                hudDockedWindowBounds(s.edge, s.offsetFraction, sizes, s.areaDp),
                "hudDockedWindowBounds não determinístico"
            )
        }
    }


    private fun forAllHud(assertion: (HudScenario) -> Unit) {
        forAllHudShrinking(SEED, ITERATIONS, assertion)
    }

    /** Mesma cadeia do produto (HudWindow.kt): auto-recolher antes e `hudWindowSizes` depois. */
    private fun sizesOf(s: HudScenario): HudNotchSizes {
        val accounts = applyHudAutoMinimize(s.accounts, s.edge, FALLBACK_LABEL, hudMaxAlong(s.edge, s.area, s.scale))
        return hudWindowSizes(
            accounts = accounts,
            placement = HudPlacement(s.edge, s.offsetFraction),
            screenArea = s.area,
            scale = s.scale,
            fallbackLabel = FALLBACK_LABEL,
            hasUpdateIndicator = s.hasUpdateIndicator,
            hasUpdateAction = s.hasUpdateAction
        ).first
    }

    private fun assertInside(what: String, window: HudWindowBounds, area: ScreenWorkArea) {
        val left = window.x.value
        val top = window.y.value
        val right = left + window.size.width.value
        val bottom = top + window.size.height.value
        val areaLeft = area.x.value
        val areaTop = area.y.value
        val areaRight = areaLeft + area.size.width.value
        val areaBottom = areaTop + area.size.height.value
        assertTrue(
            left >= areaLeft - EPS && right <= areaRight + EPS && top >= areaTop - EPS && bottom <= areaBottom + EPS,
            "$what: janela=($left,$top)-($right,$bottom) área=($areaLeft,$areaTop)-($areaRight,$areaBottom)"
        )
    }

    private fun assertFinite(what: String, value: Dp) {
        assertTrue(value.value.isFinite(), "$what não finito: $value")
    }

    private fun assertFiniteNonNegative(what: String, width: Dp, height: Dp) {
        assertTrue(width.value.isFinite() && width.value >= 0f, "$what largura inválida: $width")
        assertTrue(height.value.isFinite() && height.value >= 0f, "$what altura inválida: $height")
    }

    private companion object {
        const val SEED = 650L
        const val ITERATIONS = 500
        const val EPS = 0.01f
        const val FALLBACK_LABEL = "Anthropic"
    }
}
