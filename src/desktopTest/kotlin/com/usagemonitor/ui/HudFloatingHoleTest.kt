package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.presentation.ui.HudFloatingHole
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import kotlin.test.Test

/** Buraco flutuante do modo expandido (A07a): descrição semântica, atualização e ociosidade sem movimento. */
@OptIn(ExperimentalTestApi::class)
class HudFloatingHoleTest {

    @Test
    fun `o buraco expoe a descricao semantica recebida`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme {
                HudFloatingHole(params = HudOrbitParams(), update = null, description = "Buraco negro, 3 minimizadas")
            }
        }

        onAllNodesWithContentDescription("Buraco negro, 3 minimizadas").assertCountEquals(1)
    }

    @Test
    fun `com atualizacao pendente a descricao carrega a frase dela`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme {
                HudFloatingHole(
                    params = HudOrbitParams(),
                    update = HudUpdateIndicator(tone = AppTone.INFO, description = "Atualização pronta para reiniciar"),
                    description = "Buraco negro"
                )
            }
        }

        onAllNodesWithContentDescription("Atualização pronta para reiniciar", substring = true).assertCountEquals(1)
    }

    @Test
    fun `com politica Static a tela fica ociosa e o waitForIdle retorna`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(motion = AppMotionPolicy.Static) {
                HudFloatingHole(params = HudOrbitParams(), update = null, description = "Buraco negro")
            }
        }

        waitForIdle()
        onAllNodesWithContentDescription("Buraco negro").assertCountEquals(1)
    }

    /**
     * Com animação permitida, o laço de quadros (`rememberHudFloatingHoleClock(live = true)`) roda.
     * Sem `waitForIdle`: animação contínua não fica ociosa. O valor do relógio não é observável daqui;
     * o teste prova que o buraco segue presente com o laço ativo.
     */
    @Test
    fun `com animacao permitida o buraco segue presente com o laco de quadros ativo`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(motion = AppMotionPolicy(reduced = false, continuous = true)) {
                HudFloatingHole(params = HudOrbitParams(), update = null, description = "Buraco negro")
            }
        }

        onAllNodesWithContentDescription("Buraco negro").assertCountEquals(1)
    }
}
