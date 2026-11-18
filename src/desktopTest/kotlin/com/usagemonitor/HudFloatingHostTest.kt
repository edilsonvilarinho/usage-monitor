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

    @Test
    fun `sem centro salvo nasce centrado no notch atual`() {
        val position = hudFloatingWindowPosition(null, notch, windowSize, area)
        assertEquals(DpOffset(400.dp, 10.dp), position)
    }

    @Test
    fun `com centro salvo fica centrado nele, nao no notch`() {
        val position = hudFloatingWindowPosition(HudFloatCenter(300, 200), notch, windowSize, area)
        assertEquals(DpOffset(200.dp, 170.dp), position)
    }

    @Test
    fun `centro perto do canto e prendido dentro da area`() {
        val position = hudFloatingWindowPosition(HudFloatCenter(10, 10), notch, windowSize, area)
        assertEquals(DpOffset(0.dp, 0.dp), position)
    }

    @Test
    fun `area com origem deslocada prende na origem dela`() {
        val offsetArea = ScreenWorkArea(100.dp, 50.dp, DpSize(1000.dp, 800.dp))
        val position = hudFloatingWindowPosition(HudFloatCenter(110, 60), notch, windowSize, offsetArea)
        assertEquals(DpOffset(100.dp, 50.dp), position)
    }

    @Test
    fun `area desconhecida nao prende a janela`() {
        val position = hudFloatingWindowPosition(
            center = HudFloatCenter(100, 100),
            notchCenter = notch,
            windowSize = DpSize(200.dp, 100.dp),
            area = ScreenWorkArea.Unknown
        )
        assertEquals(DpOffset(0.dp, 50.dp), position)
    }

    @Test
    fun `centro de janela de largura impar faz a ida e volta exata`() {
        val oddSize = DpSize(201.dp, 60.dp)
        val position = hudFloatingWindowPosition(HudFloatCenter(500, 300), notch, oddSize, area)
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

    @Test
    fun `escala multiplica o tamanho e o recorte fica em dp de composicao`() {
        // O recorte sai sem escala: o `HudWindowEffects` escala o retângulo ao aplicar.
        val scaled = geometry(scale = 2f)
        assertEquals(DpSize(1688.dp, 400.dp), scaled.size)
        assertEquals(DpRect(392.dp, 70.dp, 452.dp, 130.dp), scaled.hitRect)
    }
}
