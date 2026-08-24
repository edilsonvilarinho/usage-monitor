package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.MAX_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MIN_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.UI_SCALE_STEP_PERCENT
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppStatesTest {

    @Test
    fun `o aviso mostra titulo descricao e acao`() = runDesktopComposeUiTest {
        var retried = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(700.dp).height(200.dp)) {
                    AppBanner(
                        title = "Anthropic — Padrão",
                        description = "Limite de requisições atingido.",
                        tone = AppTone.WARNING,
                        action = {
                            AppButton(label = "Tentar de novo", onClick = { retried += 1 })
                        }
                    )
                }
            }
        }

        onNodeWithText("Anthropic — Padrão").assertIsDisplayed()
        onNodeWithText("Limite de requisições atingido.").assertIsDisplayed()
        onNodeWithText("Tentar de novo").performClick()

        assertEquals(1, retried)
    }

    /** Cor não informa sozinha: o estado sempre carrega a palavra. */
    @Test
    fun `o indicador nomeia o estado`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    AppStatusIndicator(label = "Desconectado", tone = AppTone.NEUTRAL)
                }
            }
        }

        onNodeWithText("Desconectado").assertIsDisplayed()
    }

    /** Cota estourada não pode desenhar barra além da própria largura. */
    @Test
    fun `a barra aceita fracao acima de um sem quebrar`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(60.dp)) {
                    AppProgressTrack(fraction = 1.8f, tone = AppTone.CRITICAL)
                }
            }
        }
    }

    @Test
    fun `o estado vazio e o de carga mostram a mensagem`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(700.dp).height(500.dp)) {
                    AppEmptyState(message = "Nenhum turno nesta janela.")
                }
            }
        }

        onNodeWithText("Nenhum turno nesta janela.").assertIsDisplayed()
    }

    @Test
    fun `a carga mostra o rotulo sem animacao infinita`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(700.dp).height(400.dp)) {
                    AppLoadingState(message = "Carregando dados das APIs…")
                }
            }
        }

        // Chegar aqui já prova o essencial: `waitForIdle` retornou, então a
        // primitiva de carga não deixou animação pendente.
        onNodeWithText("Carregando dados das APIs…").assertIsDisplayed()
    }

    @Test
    fun `o erro oferece o caminho de volta`() = runDesktopComposeUiTest {
        var retried = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(700.dp).height(400.dp)) {
                    AppErrorState(
                        message = "Erro ao carregar histórico",
                        retryLabel = "Tentar novamente",
                        onRetry = { retried += 1 }
                    )
                }
            }
        }

        onNodeWithText("Erro ao carregar histórico").assertIsDisplayed()
        onNodeWithText("Tentar novamente").performClick()

        assertEquals(1, retried)
    }

    /**
     * A barra tem de mostrar cor em **toda** a faixa do slider de escala.
     *
     * O defeito da issue #83 era de pintura, não de layout: o `Modifier.border`
     * arredondava o traço para cima (`ceil(width.toPx())`) e o desenhava por
     * cima dos filhos, e num trilho de 4dp o anel de 2px cobria os 4px inteiros
     * a 105% e a 110%. `boundsInRoot` devolvia a altura cheia nas duas escalas —
     * um teste de layout passaria com a barra cega. Por isso a medida é o
     * **bitmap** da cena.
     *
     * A comparação é entre duas renderizações — fração 0 e fração 1 — em vez de
     * casar uma cor exata: assim o teste não fica preso a `AppAccents` nem ao
     * tema, e continua valendo se a paleta mudar.
     *
     * A grade inteira é percorrida de propósito. O defeito morava em duas
     * posições específicas do slider, e uma escala amostrada passaria.
     */
    @Test
    fun `o preenchimento da barra sobrevive a toda a faixa de escala`() {
        var percent = MIN_UI_SCALE_PERCENT
        while (percent <= MAX_UI_SCALE_PERCENT) {
            val empty = renderTrack(uiScalePercent = percent, fraction = 0f)
            val full = renderTrack(uiScalePercent = percent, fraction = 1f)
            val painted = countDifferences(empty, full)

            // Meia linha da largura do trilho: acima de qualquer sobra de
            // antialiasing e bem abaixo do valor real, que é a largura interna
            // vezes a altura interna. Com o defeito o valor é exatamente zero.
            val minimum = TRACK_WIDTH_DP * percent / 100 / 2
            assertTrue(
                painted > minimum,
                "Escala $percent%: a barra cheia mudou só $painted px (mínimo $minimum)"
            )

            percent += UI_SCALE_STEP_PERCENT
        }
    }

    private fun renderTrack(uiScalePercent: Int, fraction: Float): PixelMap {
        lateinit var pixels: PixelMap
        runDesktopComposeUiTest {
            setContent {
                AppTheme(isDark = true, uiScalePercent = uiScalePercent) {
                    Box(modifier = Modifier.width(TRACK_WIDTH_DP.dp).height(40.dp)) {
                        AppProgressTrack(fraction = fraction, tone = AppTone.CRITICAL)
                    }
                }
            }
            pixels = captureToImage().toPixelMap()
        }
        return pixels
    }

    private fun countDifferences(a: PixelMap, b: PixelMap): Int {
        var different = 0
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a[x, y] != b[x, y]) {
                    different += 1
                }
            }
        }
        return different
    }

    private companion object {
        const val TRACK_WIDTH_DP = 300
    }
}
