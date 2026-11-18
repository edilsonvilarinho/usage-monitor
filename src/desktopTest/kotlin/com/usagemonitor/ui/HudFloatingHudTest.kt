package com.usagemonitor.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_BALLOON_WIDTH
import com.usagemonitor.HUD_HANDLE_GAP
import com.usagemonitor.HUD_HANDLE_SIZE
import com.usagemonitor.HudEdge
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.hudFloatingColumnWindowSize
import com.usagemonitor.presentation.ui.HUD_BALLOON_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_FLOATING_HOLE_DRAG_TAG
import com.usagemonitor.presentation.ui.HUD_FLOAT_HANDLE_TAG
import com.usagemonitor.presentation.ui.HUD_GEAR_HANDLE_TAG
import com.usagemonitor.presentation.ui.HUD_RING_ITEM_TEST_TAG
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudFloatingBalloon
import com.usagemonitor.presentation.ui.HudFloatingHud
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.verticalCollapsed
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Composable da janela flutuante (A11c-2): o buraco fica sempre visível; expandido mostra a coluna de anéis,
 * as alças (engrenagem e encaixar) e o balão alinhado ao anel em foco; hover num anel chama o índice dele;
 * arrastar o buraco chama início e fim do arrasto.
 */
@OptIn(ExperimentalTestApi::class)
class HudFloatingHudTest {

    @Test
    fun `minimizado mostra o buraco e nenhum anel`() = hudTest {
        render(expanded = false)
        onAllNodesWithContentDescription("Black hole", substring = true).assertCountEquals(1)
        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG).assertCountEquals(0)
        onAllNodesWithTag(HUD_GEAR_HANDLE_TAG).assertCountEquals(0)
    }

    @Test
    fun `expandido mostra os aneis da coluna, a engrenagem e Encaixar na borda`() = hudTest {
        render(expanded = true, language = AppLanguage.PT)
        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG).assertCountEquals(7)
        onAllNodesWithTag(HUD_GEAR_HANDLE_TAG).assertCountEquals(1)
        onAllNodesWithContentDescription("Encaixar na borda").assertCountEquals(1)
    }

    @Test
    fun `hover num anel chama onAccountClick com o indice dele`() = hudTest {
        val hovered = mutableListOf<Int>()
        render(expanded = true, onAccountClick = { hovered += it })
        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG)[2].performMouseInput { enter(center) }
        // O hover de `hoverable` é entregue num frame posterior ao gesto; sem esperar o ocioso, a asserção lê a lista antes.
        waitForIdle()
        assertEquals(listOf(2), hovered)
    }

    @Test
    fun `balao de conta mostra o conteudo do indice pedido e so ele`() = hudTest {
        render(expanded = true, balloon = HudFloatingBalloon.Account(2))
        onAllNodesWithText("balloon-account-2").assertCountEquals(1)
        onAllNodesWithText("balloon-account-0").assertCountEquals(0)
    }

    @Test
    fun `balao da engrenagem mostra o appBalloon e nao o de conta`() = hudTest {
        render(expanded = true, balloon = HudFloatingBalloon.App)
        onAllNodesWithText("balloon-app").assertCountEquals(1)
        onAllNodesWithText("balloon-account-2").assertCountEquals(0)
    }

    @Test
    fun `balao da engrenagem sai numa moldura de HudBalloon`() = hudTest {
        render(expanded = true, balloon = HudFloatingBalloon.App)
        onAllNodesWithTag(HUD_BALLOON_TEST_TAG).assertCountEquals(1)
    }

    @Test
    fun `atualizacao pendente entra na descricao da engrenagem`() = hudTest {
        render(expanded = true, update = HudUpdateIndicator(tone = AppTone.INFO, description = UPDATE_PHRASE))
        onNodeWithTag(HUD_GEAR_HANDLE_TAG).assertContentDescriptionContains(UPDATE_PHRASE, substring = true)
    }

    @Test
    fun `clicar no botao de encaixar chama onToggleFloating`() = hudTest {
        var toggles = 0
        render(expanded = true, onToggleFloating = { toggles++ })
        onNodeWithTag(HUD_FLOAT_HANDLE_TAG).performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `arrastar o buraco chama onDragStart e onDragEnd uma vez`() = hudTest {
        var starts = 0
        var ends = 0
        render(expanded = false, onDragStart = { starts++ }, onDragEnd = { ends++ })
        onNodeWithTag(HUD_FLOATING_HOLE_DRAG_TAG).performTouchInput {
            down(center)
            moveBy(Offset(-200f, 0f))
            up()
        }
        assertEquals(1, starts)
        assertEquals(1, ends)
    }

    private companion object {
        const val UPDATE_PHRASE = "Atualização pronta para reiniciar"
        const val FALLBACK_LABEL = "Sem conta"

        val accounts: List<HudAccount> = listOf("Alfa", "Beta", "Gama", "Delta", "Epsilon", "Zeta", "Eta")
            .mapIndexed { index, name -> account(index, "Conta $name") }

        /** Janela do teste: largura e altura da coluna de contas de exemplo, com balão de 240 dp de altura. */
        val WINDOW: DpSize = hudFloatingColumnWindowSize(
            columnSize = verticalCollapsed(accounts, FALLBACK_LABEL, compact = false),
            balloonSize = DpSize(HUD_BALLOON_WIDTH, 240.dp),
            holeDiameter = HudOrbitParams().holeD.dp,
            handleSize = HUD_HANDLE_SIZE,
            gap = HUD_HANDLE_GAP
        )

        /**
         * Roda o teste com a raiz do tamanho de [WINDOW]. Sem isso a raiz cai no 768 px padrão do teste e o
         * `maxHeight` do composable fica menor que a janela real (1748 px), o que desloca a coluna e os botões.
         */
        fun hudTest(block: ComposeUiTest.() -> Unit) =
            runDesktopComposeUiTest(width = WINDOW.width.value.roundToInt(), height = WINDOW.height.value.roundToInt(), block = block)

        fun account(index: Int, label: String): HudAccount {
            val percent = 10 + index
            val quota = HudQuota("5h", "$percent%", percent / 100f, AppTone.OK, resetText = null, hasForecast = false)
            return HudAccount(
                targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "perfil-$index"),
                label = label,
                statusLabel = "Ok",
                tone = AppTone.OK,
                quotas = listOf(quota),
                focusIndex = 0,
                sessionActive = false,
                minimized = false
            )
        }

        fun ComposeUiTest.render(
            expanded: Boolean,
            language: AppLanguage = AppLanguage.EN,
            balloon: HudFloatingBalloon? = null,
            update: HudUpdateIndicator? = null,
            onAccountClick: (Int) -> Unit = {},
            onToggleFloating: () -> Unit = {},
            onDragStart: () -> Unit = {},
            onDragEnd: () -> Unit = {}
        ) {
            setContent {
                ScreenTestTheme {
                    HudFloatingHud(
                        accounts = accounts,
                        params = HudOrbitParams(),
                        language = language,
                        expanded = expanded,
                        balloon = balloon,
                        balloonSide = HudEdge.RIGHT,
                        update = update,
                        gearDescription = "Configurações",
                        fallbackLabel = FALLBACK_LABEL,
                        fallbackTone = AppTone.OK,
                        holeScreenY = WINDOW.height / 2f,
                        areaTop = 0.dp,
                        areaBottom = WINDOW.height,
                        onHoverChange = {},
                        onAccountClick = onAccountClick,
                        onGearClick = {},
                        onToggleFloating = onToggleFloating,
                        onDragStart = onDragStart,
                        onDrag = {},
                        onDragEnd = onDragEnd,
                        balloonHeight = 120.dp,
                        accountBalloon = { index -> Text("balloon-account-$index") },
                        appBalloon = { Text("balloon-app") },
                        modifier = Modifier.size(WINDOW.width, WINDOW.height)
                    )
                }
            }
        }
    }
}
