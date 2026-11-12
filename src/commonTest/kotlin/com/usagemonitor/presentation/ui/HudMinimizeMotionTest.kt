package com.usagemonitor.presentation.ui

import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Transição de minimizar a conta na HUD (#576, A05): anel 64 → 28 dp em 520 ms. */
class HudMinimizeMotionTest {
    private val alphaTolerance = 1e-3f
    private val dpTolerance = 0.01f

    @Test
    fun `a duracao e o token de 520 ms e zera com animacao reduzida`() {
        assertEquals(520, AppGargantuaTokens.minimizeMillis)
        assertEquals(520, hudMinimizeMillis(motionReduced = false))
        assertEquals(0, hudMinimizeMillis(motionReduced = true))
    }

    @Test
    fun `as pontas sao o anel cheio e o minimizado, com texto e linha de foco trocados`() {
        val full = hudMinimizeFrame(0f)
        val minimized = hudMinimizeFrame(1f)
        assertEquals(64.dp, full.ringSize)
        assertEquals(28.dp, minimized.ringSize)
        assertEquals(AppGargantuaTokens.size, full.ringSize)
        assertEquals(AppGargantuaTokens.minimizedSize, minimized.ringSize)
        assertEquals(0f, full.focusLineAlpha)
        assertEquals(1f, minimized.focusLineAlpha)
    }

    @Test
    fun `o anel e monotono, sem overshoot, e a curva nao e linear`() {
        var previous = AppGargantuaTokens.size.value
        for (step in 0..100) {
            val ring = hudMinimizeFrame(step / 100f).ringSize.value
            assertTrue(ring <= previous + 1e-4f, "o anel cresceu ao encolher em $step%: $ring")
            assertTrue(ring >= 28f - 1e-4f && ring <= 64f + 1e-4f, "anel fora de 28..64 em $step%: $ring")
            previous = ring
        }
        assertEquals(28f, previous, dpTolerance)
        assertTrue(hudMinimizeFrame(0.25f).ringSize.value > 64f - 0.25f * 36f, "ease-in ausente: começa linear")
        assertEquals(hudMinimizeFrame(0f), hudMinimizeFrame(-0.5f))
        assertEquals(hudMinimizeFrame(1f), hudMinimizeFrame(1.5f))
    }

    @Test
    fun `no meio da transicao o anel fica entre os dois tamanhos`() {
        assertEquals(46f, hudMinimizeFrame(0.5f).ringSize.value, dpTolerance)
    }

    @Test
    fun `a linha de foco sobe de 0 a 1 sem sair da faixa`() {
        var previous = 0f
        for (step in 0..100) {
            val alpha = hudMinimizeFrame(step / 100f).focusLineAlpha
            assertTrue(alpha in 0f..1f && alpha >= previous - 1e-4f, "alfa da linha de foco fora da faixa em $step%: $alpha")
            previous = alpha
        }
        assertEquals(1f, previous, alphaTolerance)
    }

    @Test
    fun `ao encolher a janela mantem o tamanho maior ate o fim da transicao`() {
        val from = AppGargantuaTokens.size
        val to = AppGargantuaTokens.minimizedSize
        for (progress in listOf(0f, 0.3f, 0.99f)) {
            assertEquals(from, hudMinimizeWindowSize(from = from, to = to, progress = progress), "encolhendo em $progress")
        }
        assertEquals(to, hudMinimizeWindowSize(from = from, to = to, progress = 1f))
    }

    @Test
    fun `ao crescer a janela ja tem o tamanho maior desde o primeiro quadro`() {
        val from = AppGargantuaTokens.minimizedSize
        val to = AppGargantuaTokens.size
        for (progress in listOf(0f, 0.3f, 0.99f)) {
            assertEquals(to, hudMinimizeWindowSize(from = from, to = to, progress = progress), "crescendo em $progress")
        }
        assertEquals(to, hudMinimizeWindowSize(from = from, to = to, progress = 1f))
    }

    @Test
    fun `com animacao reduzida o progresso ja vai ao fim, sem quadro intermediario`() {
        assertEquals(1f, hudMinimizeProgress(elapsedMillis = 0f, motionReduced = true))
        assertEquals(1f, hudMinimizeProgress(elapsedMillis = 100f, motionReduced = true))
        assertEquals(hudMinimizeFrame(1f), hudMinimizeFrame(hudMinimizeProgress(0f, motionReduced = true)))
        assertEquals(0f, hudMinimizeProgress(elapsedMillis = 0f, motionReduced = false))
        assertEquals(0.5f, hudMinimizeProgress(elapsedMillis = 260f, motionReduced = false), alphaTolerance)
        assertEquals(1f, hudMinimizeProgress(elapsedMillis = 520f, motionReduced = false))
        assertEquals(1f, hudMinimizeProgress(elapsedMillis = 900f, motionReduced = false))
    }
}
