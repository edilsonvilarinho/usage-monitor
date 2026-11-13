package com.usagemonitor.presentation.ui.components

import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** #585 · lua em órbita: a troca de emoji da conta, quadro a quadro. */
class AccountMoonSwapTest {
    @Test
    fun `a troca dura 420 ms e a lua velha sai ate 210 ms`() {
        assertEquals(420, AppGargantuaTokens.moonSwapMillis)
        assertEquals(210, AppGargantuaTokens.moonSwapOutMillis)
    }

    @Test
    fun `no inicio a velha esta inteira na orbita e a nova ainda nao nasceu`() {
        val frame = accountMoonSwapFrame(0f)
        assertEquals(1f, frame.outgoingAlpha, "alpha da velha no início")
        assertEquals(0f, frame.outgoingDegrees, "ângulo da velha no início")
        assertEquals(0f, frame.incomingAlpha, "alpha da nova no início")
        assertEquals(0.7f, frame.incomingScale, "escala da nova no início")
    }

    @Test
    fun `no fim da saida a velha girou 20 graus e sumiu, a nova ainda nao entrou`() {
        val frame = accountMoonSwapFrame(210f)
        assertEquals(-20f, frame.outgoingDegrees, "ângulo da velha na saída")
        assertEquals(0f, frame.outgoingAlpha, "alpha da velha na saída")
        assertEquals(0f, frame.incomingAlpha, "alpha da nova na saída")
    }

    @Test
    fun `no fim da troca a nova esta inteira e a velha nao sobra`() {
        val frame = accountMoonSwapFrame(420f)
        assertEquals(1f, frame.incomingAlpha, "alpha da nova no fim")
        assertEquals(1f, frame.incomingScale, "escala da nova no fim")
        assertEquals(0f, frame.outgoingAlpha, "alpha da velha no fim")
    }

    @Test
    fun `a troca nao passa do lugar nem volta em toda a duracao`() {
        var previousDegrees = 0f
        var previousOutgoingAlpha = 1f
        var previousIncomingAlpha = 0f
        var previousScale = 0.7f
        for (step in 0..84) {
            val elapsed = step * 5f
            val frame = accountMoonSwapFrame(elapsed)
            assertTrue(frame.outgoingDegrees in -20f..0f && frame.outgoingDegrees <= previousDegrees, "ângulo da velha em $elapsed ms: ${frame.outgoingDegrees}")
            assertTrue(frame.outgoingAlpha in 0f..1f && frame.outgoingAlpha <= previousOutgoingAlpha, "alpha da velha em $elapsed ms: ${frame.outgoingAlpha}")
            assertTrue(frame.incomingAlpha in 0f..1f && frame.incomingAlpha >= previousIncomingAlpha, "alpha da nova em $elapsed ms: ${frame.incomingAlpha}")
            assertTrue(frame.incomingScale in 0.7f..1f && frame.incomingScale >= previousScale, "escala da nova em $elapsed ms: ${frame.incomingScale}")
            previousDegrees = frame.outgoingDegrees
            previousOutgoingAlpha = frame.outgoingAlpha
            previousIncomingAlpha = frame.incomingAlpha
            previousScale = frame.incomingScale
        }
    }

    @Test
    fun `depois do fim fica no quadro final`() {
        assertEquals(accountMoonSwapFrame(420f), accountMoonSwapFrame(600f))
    }
}
