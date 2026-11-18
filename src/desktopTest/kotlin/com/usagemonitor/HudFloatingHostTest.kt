package com.usagemonitor

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudFloatingBalloon
import kotlin.test.Test
import kotlin.test.assertEquals

class HudFloatingHostTest {

    private val area = ScreenWorkArea(0.dp, 0.dp, DpSize(1000.dp, 800.dp))
    private val windowSize = DpSize(200.dp, 60.dp)
    private val notch = HudFloatCenter(500, 40)

    /** Buraco de diâmetro 60: a âncora do minimizado tem raio 30. */
    private val holeDiameter = 60.dp

    @Test
    fun `sem centro salvo nasce centrado no notch atual`() {
        val position = hudFloatingWindowPosition(null, notch, windowSize, area, anchorDiameter = 0.dp)
        assertEquals(DpOffset(400.dp, 10.dp), position)
    }

    @Test
    fun `com centro salvo fica centrado nele, nao no notch`() {
        val position = hudFloatingWindowPosition(HudFloatCenter(300, 200), notch, windowSize, area, 0.dp)
        assertEquals(DpOffset(200.dp, 170.dp), position)
    }

    /** Centro (0,0) vai a (30,30): o buraco fica de (0,0) a (60,60), encostado nas duas bordas. */
    @Test
    fun `buraco no canto superior esquerdo encosta nas duas bordas`() {
        val position = hudFloatingWindowPosition(HudFloatCenter(0, 0), notch, windowSize, area, holeDiameter)
        assertEquals(DpOffset(-70.dp, 0.dp), position)
    }

    /** Centro (1000,800) vai a (970,770): o buraco fica de (940,740) a (1000,800). */
    @Test
    fun `buraco no canto inferior direito encosta nas duas bordas`() {
        val position = hudFloatingWindowPosition(HudFloatCenter(1000, 800), notch, windowSize, area, holeDiameter)
        assertEquals(DpOffset(870.dp, 740.dp), position)
    }

    @Test
    fun `area com origem deslocada prende o buraco a partir da origem dela`() {
        val offsetArea = ScreenWorkArea(100.dp, 50.dp, DpSize(1000.dp, 800.dp))
        val position = hudFloatingWindowPosition(HudFloatCenter(110, 60), notch, windowSize, offsetArea, holeDiameter)
        assertEquals(DpOffset(30.dp, 50.dp), position)
    }

    @Test
    fun `area desconhecida nao prende a janela`() {
        val position = hudFloatingWindowPosition(
            center = HudFloatCenter(100, 100),
            notchCenter = notch,
            windowSize = DpSize(200.dp, 100.dp),
            area = ScreenWorkArea.Unknown,
            anchorDiameter = holeDiameter
        )
        assertEquals(DpOffset(0.dp, 50.dp), position)
    }

    @Test
    fun `centro de janela de largura impar faz a ida e volta exata`() {
        val oddSize = DpSize(201.dp, 60.dp)
        val position = hudFloatingWindowPosition(HudFloatCenter(500, 300), notch, oddSize, area, 0.dp)
        assertEquals(HudFloatCenter(500, 300), hudFloatCenterOf(position, oddSize))
    }

    @Test
    fun `hudFloatCenterOf devolve o centro do canto mais metade do tamanho`() {
        val center = hudFloatCenterOf(DpOffset(400.dp, 10.dp), DpSize(200.dp, 60.dp))
        assertEquals(HudFloatCenter(500, 40), center)
    }

    @Test
    fun `balao fechado abre o clicado`() {
        val next = hudFloatingBalloonToggle(null, HudFloatingBalloon.Account(1))
        assertEquals(HudFloatingBalloon.Account(1), next)
    }

    @Test
    fun `clicar no balao aberto o fecha`() {
        val next = hudFloatingBalloonToggle(HudFloatingBalloon.Account(1), HudFloatingBalloon.Account(1))
        assertEquals(null, next)
    }

    @Test
    fun `clicar em outra conta troca o balao`() {
        val next = hudFloatingBalloonToggle(HudFloatingBalloon.Account(1), HudFloatingBalloon.Account(2))
        assertEquals(HudFloatingBalloon.Account(2), next)
    }

    @Test
    fun `clicar na engrenagem troca o balao de conta pelo do app`() {
        val next = hudFloatingBalloonToggle(HudFloatingBalloon.Account(1), HudFloatingBalloon.App)
        assertEquals(HudFloatingBalloon.App, next)
    }

    @Test
    fun `clicar de novo na engrenagem fecha o balao do app`() {
        val next = hudFloatingBalloonToggle(HudFloatingBalloon.App, HudFloatingBalloon.App)
        assertEquals(null, next)
    }

    // Geometria da janela: campo 200, buraco 60, balão 300x100, alça 20, vão 8.
    // Tamanho fixo: 300*2 + 8*3 + 200 + 20 = 844 de largura; altura = max(200, 100).

    private fun geometry(
        center: HudFloatCenter? = null,
        expanded: Boolean = false,
        balloonOpen: Boolean = false,
        scale: Float = 1f
    ): HudFloatingWindowGeometry = hudFloatingWindowGeometry(
        center = center,
        notchCenter = notch,
        area = area,
        scale = scale,
        maxFieldDiameter = 200.dp,
        holeDiameter = 60.dp,
        balloonSize = DpSize(300.dp, 100.dp),
        handleSize = 20.dp,
        gap = 8.dp,
        expanded = expanded,
        balloonOpen = balloonOpen
    )

    @Test
    fun `tamanho fixo independe de expandido e do balao`() {
        assertEquals(DpSize(844.dp, 200.dp), geometry().size)
        assertEquals(DpSize(844.dp, 200.dp), geometry(expanded = true, balloonOpen = true).size)
    }

    @Test
    fun `minimizado o recorte e so o buraco`() {
        assertEquals(DpRect(392.dp, 70.dp, 452.dp, 130.dp), geometry().hitRect)
    }

    @Test
    fun `expandido com balao a esquerda o recorte cobre balao campo e botoes`() {
        val expanded = geometry(center = HudFloatCenter(700, 400), expanded = true, balloonOpen = true)
        assertEquals(HudEdge.RIGHT, expanded.balloonSide)
        assertEquals(DpRect(0.dp, 0.dp, 550.dp, 200.dp), expanded.hitRect)
    }

    @Test
    fun `lado do balao acompanha a metade da area em que o centro cai`() {
        assertEquals(HudEdge.LEFT, geometry(center = HudFloatCenter(100, 400)).balloonSide)
        assertEquals(HudEdge.RIGHT, geometry(center = HudFloatCenter(700, 400)).balloonSide)
    }

    @Test
    fun `posicao com centro salvo fica centrada nele`() {
        assertEquals(DpOffset(78.dp, 200.dp), geometry(center = HudFloatCenter(500, 300)).position)
    }

    /** Buraco (raio 30) no canto superior esquerdo: centro vai a (30,30); janela em (30-422, 30-100). */
    @Test
    fun `minimizado com buraco no canto superior esquerdo prende o buraco`() {
        assertEquals(DpOffset(-392.dp, -70.dp), geometry(center = HudFloatCenter(0, 0)).position)
    }

    /** Buraco no canto inferior direito: centro vai a (970,770); janela em (970-422, 770-100). */
    @Test
    fun `minimizado com buraco no canto inferior direito prende o buraco`() {
        assertEquals(DpOffset(548.dp, 670.dp), geometry(center = HudFloatCenter(1000, 800)).position)
    }

    /** Campo expandido (raio 100) no canto: centro vai a (100,100); o campo fica dentro da área. */
    @Test
    fun `expandido no canto superior esquerdo desloca o campo para dentro`() {
        assertEquals(DpOffset(-322.dp, 0.dp), geometry(center = HudFloatCenter(0, 0), expanded = true).position)
    }

    /** Centro gravado (0,0) fixo: expandir e recolher devolve a mesma posição do minimizado. */
    @Test
    fun `ao recolher volta ao mesmo centro gravado`() {
        val saved = HudFloatCenter(0, 0)
        val collapsedBefore = geometry(center = saved).position
        geometry(center = saved, expanded = true)
        val collapsedAfter = geometry(center = saved).position

        assertEquals(collapsedBefore, collapsedAfter)
        assertEquals(DpOffset(-392.dp, -70.dp), collapsedAfter)
    }

    @Test
    fun `escala multiplica o tamanho e o recorte fica em dp de composicao`() {
        // O recorte sai sem escala: o `HudWindowEffects` escala o retângulo ao aplicar.
        val scaled = geometry(scale = 2f)
        assertEquals(DpSize(1688.dp, 400.dp), scaled.size)
        assertEquals(DpRect(392.dp, 70.dp, 452.dp, 130.dp), scaled.hitRect)
    }

    // Borda virtual (A10a): buraco 60 numa área 1000x800 com origem 0,0.

    @Test
    fun `buraco na metade direita gruda na borda RIGHT, com a face externa do buraco como limite`() {
        val dock = hudFloatingDock(HudFloatCenter(700, 400), holeDiameter, area)
        assertEquals(HudEdge.RIGHT, dock.edge)
        assertEquals(730.dp, dock.virtualArea.x + dock.virtualArea.size.width)
    }

    @Test
    fun `buraco na metade esquerda gruda na borda LEFT, com a face externa do buraco como limite`() {
        val dock = hudFloatingDock(HudFloatCenter(300, 400), holeDiameter, area)
        assertEquals(HudEdge.LEFT, dock.edge)
        assertEquals(270.dp, dock.virtualArea.x)
        assertEquals(1000.dp, dock.virtualArea.x + dock.virtualArea.size.width)
    }

    @Test
    fun `o centro do notch, calculado de volta pela fracao, cai na altura do buraco`() {
        val dock = hudFloatingDock(HudFloatCenter(700, 400), holeDiameter, area)
        assertEquals(0.5f, dock.offsetFraction)
        val bounds = hudWindowBounds(dock.edge, dock.offsetFraction, DpSize(60.dp, 200.dp), dock.virtualArea)
        assertEquals(400.dp, bounds.y + bounds.notchCenterInWindow)
    }

    /** Buraco com centro em y=20: o centro do notch cai no limite da borda (margem + metade do notch). */
    @Test
    fun `buraco perto do topo deixa o centro do notch preso pelo limite`() {
        val dock = hudFloatingDock(HudFloatCenter(700, 20), holeDiameter, area)
        val bounds = hudWindowBounds(dock.edge, dock.offsetFraction, DpSize(60.dp, 200.dp), dock.virtualArea)
        assertEquals(HUD_SHADOW_MARGIN + 100.dp, bounds.y + bounds.notchCenterInWindow)
    }

    @Test
    fun `repouso do modo solto e o buraco quadrado, o resto do notch fica igual`() {
        val sizes = HudNotchSizes(
            collapsed = DpSize(120.dp, 40.dp),
            expanded = DpSize(300.dp, 500.dp),
            balloon = DpSize(264.dp, 200.dp),
            withHandles = DpSize(140.dp, 40.dp),
            compact = true
        )
        val floating = hudFloatingSizes(sizes, holeDiameter)
        assertEquals(DpSize(60.dp, 60.dp), floating.collapsed)
        assertEquals(sizes.expanded, floating.expanded)
        assertEquals(sizes.balloon, floating.balloon)
        assertEquals(sizes.withHandles, floating.withHandles)
        assertEquals(sizes.compact, floating.compact)
    }

    // Ajuste dinâmico nos cantos: centro do buraco ao longo do eixo; área de 0 a 800; coluna de 200.

    @Test
    fun `buraco perto do topo desloca a coluna para baixo, inteira na area`() {
        val shift = hudFloatingAlongShift(holeAlong = 40.dp, expandedAlong = 200.dp, areaStart = 0.dp, areaEnd = 800.dp)
        assertEquals(60.dp, shift)
    }

    @Test
    fun `buraco perto do fundo desloca a coluna para cima, inteira na area`() {
        val shift = hudFloatingAlongShift(holeAlong = 760.dp, expandedAlong = 200.dp, areaStart = 0.dp, areaEnd = 800.dp)
        assertEquals((-60).dp, shift)
    }

    @Test
    fun `buraco no meio deixa a coluna centrada nele`() {
        val shift = hudFloatingAlongShift(holeAlong = 400.dp, expandedAlong = 200.dp, areaStart = 0.dp, areaEnd = 800.dp)
        assertEquals(0.dp, shift)
    }

    @Test
    fun `coluna maior que a area se alinha ao inicio`() {
        val shift = hudFloatingAlongShift(holeAlong = 400.dp, expandedAlong = 900.dp, areaStart = 0.dp, areaEnd = 800.dp)
        assertEquals(50.dp, shift)
    }

    /** Buraco de 60 numa janela de 200x100, centro do notch em y=50: encostado na borda direita. */
    @Test
    fun `buraco a direita encosta na borda direita da janela`() {
        val rect = hudDetachedHoleRect(HudEdge.RIGHT, DpSize(200.dp, 100.dp), notchCenterInWindow = 50.dp, holeD = 60.dp)
        assertEquals(DpRect(140.dp, 20.dp, 200.dp, 80.dp), rect)
    }

    @Test
    fun `buraco a esquerda encosta na borda esquerda da janela`() {
        val rect = hudDetachedHoleRect(HudEdge.LEFT, DpSize(200.dp, 100.dp), notchCenterInWindow = 50.dp, holeD = 60.dp)
        assertEquals(DpRect(0.dp, 20.dp, 60.dp, 80.dp), rect)
    }

    private val monitor = ScreenWorkArea(0.dp, 0.dp, DpSize(1000.dp, 800.dp))
    private val usable = ScreenWorkArea(0.dp, 0.dp, DpSize(1000.dp, 760.dp))
    private val sizesOf = { _: HudPlacement, _: ScreenWorkArea ->
        val sizes = HudNotchSizes(collapsed = DpSize(60.dp, 60.dp), expanded = DpSize(200.dp, 300.dp))
        sizes to sizes
    }

    /** O centro do buraco é preso ao monitor inteiro (o buraco pode ficar sobre a barra de tarefas). */
    @Test
    fun `buraco do modo solto mantem o diametro e prende o centro ao monitor`() {
        val inside = hudDetachedWindow(HudFloatCenter(1000, 400), 60.dp, usable, monitor, 1f, sizesOf, dragging = false)
        val outside = hudDetachedWindow(HudFloatCenter(1200, 400), 60.dp, usable, monitor, 1f, sizesOf, dragging = false)
        assertEquals(60.dp, inside.holeRect.right - inside.holeRect.left)
        assertEquals(inside.bounds, outside.bounds)
    }
}
