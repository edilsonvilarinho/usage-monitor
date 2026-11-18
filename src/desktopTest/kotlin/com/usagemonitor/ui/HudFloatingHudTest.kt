package com.usagemonitor.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import com.usagemonitor.presentation.ui.HUD_FLOATING_COLUMN_TEST_TAG
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
import com.usagemonitor.presentation.ui.hudBlackHoleCountLabel
import com.usagemonitor.verticalCollapsed
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Composable da janela flutuante (A11c-2): o buraco fica sempre visível; expandido mostra a coluna de anéis,
 * as alças (engrenagem e encaixar) e o balão alinhado ao anel em foco; hover num anel chama o índice dele;
 * arrastar o buraco chama início e fim do arrasto.
 */
@OptIn(ExperimentalTestApi::class)
class HudFloatingHudTest {

    @Test
    fun `minimizado mostra o buraco, nenhuma alca e anel nao recebe hover`() = hudTest {
        // A coluna fica na árvore recolhida (A11e), então os anéis existem, mas a coluna recolhida não repassa hover.
        val hovered = mutableListOf<Int>()
        render(expanded = false, onAccountClick = { hovered += it })
        onAllNodesWithContentDescription("Black hole", substring = true).assertCountEquals(1)
        onAllNodesWithTag(HUD_GEAR_HANDLE_TAG).assertCountEquals(0)
        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG)[2].performMouseInput { enter(center) }
        waitForIdle()
        assertEquals(emptyList(), hovered)
    }

    /** A coluna existe minimizada (recolhida pela íris), para a íris revelar no mesmo lugar (A11e). */
    @Test
    fun `minimizado monta a coluna recolhida no lugar dela, para a iris animar`() = hudTest {
        render(expanded = false)
        onAllNodesWithTag(HUD_FLOATING_COLUMN_TEST_TAG).assertCountEquals(1)
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

    /** A11g: hover no buraco da coluna abre a lista das contas minimizadas, com a contagem no topo. */
    @Test
    fun `expandido, hover no buraco da coluna abre a lista das minimizadas`() = hudTestWith(MINIMIZED_WINDOW) {
        render(expanded = true, language = AppLanguage.EN, accountList = MINIMIZED_ACCOUNTS, window = MINIMIZED_WINDOW)
        onNodeWithTag(HUD_BLACK_HOLE_CELL_TAG).performMouseInput { enter(center) }
        waitForIdle()
        onAllNodesWithText(hudBlackHoleCountLabel(3, AppLanguage.EN)).assertCountEquals(1)
    }

    /** A11f: o vão entre o buraco e a coluna não é a janela; o ponteiro ali não conta como hover. */
    @Test
    fun `expandido, ponteiro no vao entre o buraco e a coluna nao conta como hover`() = hudTest {
        val hover = mutableListOf<Boolean>()
        render(expanded = true, onHoverChange = { hover += it })
        val hole = boundsOf(HUD_FLOATING_HOLE_DRAG_TAG)
        val column = boundsOf(HUD_FLOATING_COLUMN_TEST_TAG)
        assertTrue(column.left > hole.right || column.right < hole.left, "a coluna precisa ficar ao lado do buraco")
        onRoot().performMouseInput { enter(hole.center) }
        waitForIdle()
        assertEquals(true, hover.last())
        onRoot().performMouseInput { moveTo(gapBetween(hole, column)) }
        waitForIdle()
        assertEquals(false, hover.last())
    }

    @Test
    fun `expandido, ponteiro na coluna e no buraco contam como hover`() = hudTest {
        val hover = mutableListOf<Boolean>()
        render(expanded = true, onHoverChange = { hover += it })
        val hole = boundsOf(HUD_FLOATING_HOLE_DRAG_TAG)
        val column = boundsOf(HUD_FLOATING_COLUMN_TEST_TAG)
        onRoot().performMouseInput { enter(column.center) }
        waitForIdle()
        assertEquals(true, hover.last())
        onRoot().performMouseInput { moveTo(hole.center) }
        waitForIdle()
        assertEquals(true, hover.last())
    }

    @Test
    fun `minimizado, ponteiro sobre a coluna recolhida nao conta como hover`() = hudTest {
        val hover = mutableListOf<Boolean>()
        render(expanded = false, onHoverChange = { hover += it })
        val column = boundsOf(HUD_FLOATING_COLUMN_TEST_TAG)
        onRoot().performMouseInput { enter(column.center) }
        waitForIdle()
        assertEquals(false, hover.last())
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

        /** Tag da célula do buraco negro na coluna (`HudBlackHoleCell`). */
        const val HUD_BLACK_HOLE_CELL_TAG = "hudBlackHole"

        val accounts: List<HudAccount> = listOf("Alfa", "Beta", "Gama", "Delta", "Epsilon", "Zeta", "Eta")
            .mapIndexed { index, name -> account(index, "Conta $name") }

        /** As três últimas contas minimizadas: o buraco da coluna lista três. */
        val MINIMIZED_ACCOUNTS: List<HudAccount> = accounts.mapIndexed { index, account ->
            if (index >= 4) account.copy(minimized = true) else account
        }

        /** Janela do teste: largura e altura da coluna de contas de exemplo, com balão de 240 dp de altura. */
        fun windowFor(list: List<HudAccount>): DpSize = hudFloatingColumnWindowSize(
            columnSize = verticalCollapsed(list, FALLBACK_LABEL, compact = false),
            balloonSize = DpSize(HUD_BALLOON_WIDTH, 240.dp),
            holeDiameter = HudOrbitParams().holeD.dp,
            handleSize = HUD_HANDLE_SIZE,
            gap = HUD_HANDLE_GAP
        )

        val WINDOW: DpSize = windowFor(accounts)
        val MINIMIZED_WINDOW: DpSize = windowFor(MINIMIZED_ACCOUNTS)

        /**
         * Roda o teste com a raiz do tamanho de [window]. Sem isso a raiz cai no 768 px padrão do teste e o
         * `maxHeight` do composable fica menor que a janela real (1748 px), o que desloca a coluna e os botões.
         */
        fun hudTestWith(window: DpSize, block: ComposeUiTest.() -> Unit) =
            runDesktopComposeUiTest(width = window.width.value.roundToInt(), height = window.height.value.roundToInt(), block = block)

        fun hudTest(block: ComposeUiTest.() -> Unit) = hudTestWith(WINDOW, block)

        /** Retângulo, em px de raiz, do nó com a tag. */
        fun ComposeUiTest.boundsOf(tag: String): Rect = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

        /** Ponto no meio do vão horizontal entre o buraco e a coluna, na altura do centro do buraco. */
        fun gapBetween(hole: Rect, column: Rect): Offset {
            val x = if (column.left >= hole.right) (hole.right + column.left) / 2f else (column.right + hole.left) / 2f
            return Offset(x, hole.center.y)
        }

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
            onDragEnd: () -> Unit = {},
            onHoverChange: (Boolean) -> Unit = {},
            accountList: List<HudAccount> = accounts,
            window: DpSize = WINDOW
        ) {
            setContent {
                // Estado do balão no próprio teste: o hover e o clique do buraco mudam o que a tela mostra, como no host.
                var openBalloon by remember { mutableStateOf(balloon) }
                ScreenTestTheme {
                    HudFloatingHud(
                        accounts = accountList,
                        params = HudOrbitParams(),
                        language = language,
                        expanded = expanded,
                        balloon = openBalloon,
                        balloonSide = HudEdge.RIGHT,
                        update = update,
                        gearDescription = "Configurações",
                        fallbackLabel = FALLBACK_LABEL,
                        fallbackTone = AppTone.OK,
                        holeScreenY = window.height / 2f,
                        areaTop = 0.dp,
                        areaBottom = window.height,
                        onHoverChange = onHoverChange,
                        onAccountClick = onAccountClick,
                        onGearClick = {},
                        onToggleFloating = onToggleFloating,
                        onDragStart = onDragStart,
                        onDrag = {},
                        onDragEnd = onDragEnd,
                        balloonHeight = 120.dp,
                        accountBalloon = { index -> Text("balloon-account-$index") },
                        appBalloon = { Text("balloon-app") },
                        accountActions = {},
                        onHoleHover = { if (openBalloon !is HudFloatingBalloon.Hole) openBalloon = HudFloatingBalloon.Hole(null) },
                        onHoleClick = {
                            openBalloon = if (openBalloon is HudFloatingBalloon.Hole) null else HudFloatingBalloon.Hole(null)
                        },
                        onHoleSelect = { index -> openBalloon = HudFloatingBalloon.Hole(index) },
                        onHoleBack = { openBalloon = HudFloatingBalloon.Hole(null) },
                        modifier = Modifier.size(window.width, window.height)
                    )
                }
            }
        }
    }
}
