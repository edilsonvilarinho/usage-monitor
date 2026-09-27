package com.usagemonitor.ui

import androidx.compose.ui.test.onAllNodesWithTag
import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.HUD_EMOJI_BADGE_OVERSHOOT
import com.usagemonitor.presentation.ui.HUD_ACCOUNT_EMOJI_TEST_TAG
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import com.usagemonitor.HUD_BALLOON_PADDING
import com.usagemonitor.ScreenWorkArea
import com.usagemonitor.hudBalloonHeight
import com.usagemonitor.hudDockedWindowBounds
import com.usagemonitor.presentation.ui.HUD_BALLOON_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_BALLOON_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_MODE_TAG_PREFIX
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_UPDATE_ACTION_TAG
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_UPDATE_BANNER_TAG
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_VERSION_TEST_TAG
import com.usagemonitor.presentation.ui.HudAppBalloonContent
import com.usagemonitor.presentation.ui.HudAccountBalloonContent
import com.usagemonitor.presentation.ui.HUD_BALLOON_RING_LEGEND_TAG_PREFIX
import com.usagemonitor.presentation.ui.HUD_BALLOON_SESSION_SIGNALS_TAG
import com.usagemonitor.presentation.ui.HudSessionSignal
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.FooterActionGroup
import com.usagemonitor.presentation.ui.components.WindowMode
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.hudAppBalloonHeight
import com.usagemonitor.HUD_BALLOON_WIDTH
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsSelected
import com.usagemonitor.presentation.ui.HUD_GEAR_HANDLE_TAG
import com.usagemonitor.presentation.ui.HUD_MOVE_HANDLE_TAG
import com.usagemonitor.presentation.ui.hudBalloonBoxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import com.usagemonitor.HUD_RING_GAP
import com.usagemonitor.HUD_RING_SIZE
import com.usagemonitor.HUD_RING_STROKE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.AppUsageRing
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.usagemonitor.HudEdge
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HUD_NOTCH_DESCRIPTION
import com.usagemonitor.presentation.ui.HUD_COUNTDOWN_CLOCK_TAG
import com.usagemonitor.presentation.ui.HudCountdown
import com.usagemonitor.presentation.ui.HUD_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_GEAR_HINT_UPDATE_TAG
import com.usagemonitor.presentation.ui.HUD_GEAR_UPDATE_DOT_TAG
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * O notch da HUD: gestos, o que aparece parado e aberto, contagem e atualização.
 *
 * Herda as asserções da barra de linhas que ele substitui — o que a HUD promete
 * não mudou com o desenho: clique abre a janela, arrasto não abre, botão direito
 * vai direto a "Somente cards", a contagem sai uma vez só, o reset só aberto.
 */
@OptIn(ExperimentalTestApi::class)
class HudNotchTest {

    private val now = Instant.parse("2026-09-24T12:00:00Z")

    private companion object {
        const val INFORMATA_RING = "INFORMATA2 (Max 20x) · Crítico · anel externo 7d 9% · anel interno 5h 28% · " +
            "Contexto saturado · 1 sessão · Sem resposta há 2h10"
        const val DEEPSEEK_RING = "DeepSeek · Sem projeção · Saldo \$2.27"
        const val GEAR = "Configurações"

        /** O texto e o rótulo que `updateBannerContent` dá ao estado pronto. */
        val READY_INDICATOR = HudUpdateIndicator(
            tone = AppTone.OK,
            description = "Versão 38.1.0 pronta — será aplicada ao fechar o Usage Monitor",
            actionLabel = "Reiniciar o app e atualizar",
            headline = "Versão 38.1.0 pronta",
            detail = "Aplicada ao fechar o Usage Monitor"
        )

        val DOWNLOADING_INDICATOR = HudUpdateIndicator(
            tone = AppTone.INFO,
            description = "Baixando a versão 38.1.0 — 42%",
            headline = "Baixando 38.1.0",
            detail = "42% concluído"
        )
    }
    private val countdown = "Próxima atualização automática"

    private val accounts = listOf(
        account(
            "INFORMATA2", "Crítico", AppTone.CRITICAL,
            HudQuota("5h", "28%", 0.28f, AppTone.OK, resetText = "22h59", hasForecast = true, title = "Sessão 5h", usedLeftText = "28% usado · 72% restante", periodType = PeriodType.INTERVAL),
            HudQuota("7d", "9%", 0.09f, AppTone.CRITICAL, resetText = "Ter 21h00", hasForecast = true, title = "Semanal", usedLeftText = "9% usado · 91% restante", periodType = PeriodType.WEEKLY)
        ).copy(
            // O emoji da conta (#287) no fixture principal: o teste de geometria
            // que percorre as quatro bordas afirma que o selo não muda o notch
            // nem o cabeçalho do balão.
            accountEmoji = AccountEmoji.FOX,
            // Os sinais de sessão (#265) no fixture principal: o teste de geometria
            // que percorre as contas passa a cobrir a seção nova do balão.
            sessionSignals = listOf(
                HudSessionSignal("Contexto saturado · 1 sessão", AppTone.CRITICAL),
                HudSessionSignal("Sem resposta há 2h10", AppTone.WARNING)
            )
        ),
        account(
            "DeepSeek", "Sem projeção", AppTone.NEUTRAL,
            HudQuota("Saldo", "\$2.27", 0.3f, AppTone.NEUTRAL, resetText = null, hasForecast = false)
        )
    )

    @Composable
    private fun notch(
        expanded: Boolean = false,
        edge: HudEdge = HudEdge.TOP,
        list: List<HudAccount> = accounts,
        fallbackLabel: String = "Carregando",
        updateIndicator: HudUpdateIndicator? = null,
        onHoverChange: (Boolean) -> Unit = {},
        onDragStart: () -> Unit = {},
        onDragMove: () -> Unit = {},
        onDragEnd: () -> Unit = {},
        onRefreshAccount: (UsageTargetKey) -> Unit = {},
        accountActions: (@Composable (HudAccount) -> Unit)? = null,
        onSwitchToCardsOnly: () -> Unit = {},
        dragging: Boolean = false,
        onGearClick: () -> Unit = {}
    ) {
        AppTheme(isDark = true) {
            Box(modifier = Modifier.size(900.dp, 600.dp)) {
                HudNotch(
                    accounts = list,
                    edge = edge,
                    sizes = hudNotchSizes(list, edge, fallbackLabel, updateIndicator != null),
                    fallbackLabel = fallbackLabel,
                    expanded = expanded,
                    updateIndicator = updateIndicator,
                    onHoverChange = onHoverChange,
                    onDragStart = onDragStart,
                    onDragMove = onDragMove,
                    onDragEnd = onDragEnd,
                    onRefreshAccount = onRefreshAccount,
                    accountActions = accountActions,
                    onSwitchToCardsOnly = onSwitchToCardsOnly,
                    dragging = dragging,
                    onGearClick = onGearClick,
                    gearDescription = GEAR
                )
            }
        }
    }

    // ------------------------------------------------------------ conteúdo

    /** Parado, cada conta tem percentual **e** palavra: cor nunca informa sozinha. */
    @Test
    fun `parado o notch mostra percentual e palavra de cada conta`() = runDesktopComposeUiTest {
        setContent { notch() }

        onNodeWithText("5h 28%").assertIsDisplayed()
        onNodeWithText("Crítico").assertIsDisplayed()
        onNodeWithText("Sem projeção").assertIsDisplayed()
        onNodeWithContentDescription(INFORMATA_RING).assertExists()
    }

    /**
     * As duas janelas com o rótulo, nas quatro bordas (#286): o número da
     * semanal e o da 5h, e não só o da cota em foco sem dizer qual é.
     */
    @Test
    fun `parado o notch mostra cada janela com o rotulo em toda borda`() {
        for (edge in HudEdge.entries) {
            runDesktopComposeUiTest {
                setContent { notch(edge = edge) }

                onNodeWithText("7d 9%").assertIsDisplayed()
                onNodeWithText("5h 28%").assertIsDisplayed()
                // Conta de cota única continua só com o número.
                onNodeWithText("\$2.27").assertIsDisplayed()
            }
        }
    }

    /** O balão é de **uma** conta, a do anel sob o ponteiro — como no Codenotch. */
    @Test
    fun `aberto o balao mostra so a conta do anel sob o ponteiro`() = runDesktopComposeUiTest {
        setContent { notch(expanded = true) }

        // Aberto e sem anel sob o ponteiro ainda não há balão.
        onNodeWithTag(HUD_BALLOON_TEST_TAG).assertDoesNotExist()

        hoverRing(INFORMATA_RING)
        onNodeWithText("INFORMATA2").assertIsDisplayed()
        // O plano no rodapé, e o reinício de cada cota com a palavra.
        onNodeWithText("Max 20x").assertIsDisplayed()
        onNodeWithText("Reinicia 22h59").assertIsDisplayed()
        onNodeWithText("Reinicia Ter 21h00").assertIsDisplayed()
        onNodeWithText("28% usado · 72% restante").assertIsDisplayed()
        onNodeWithText("DeepSeek").assertDoesNotExist()

        hoverRing(DEEPSEEK_RING)
        onNodeWithText("DeepSeek").assertIsDisplayed()
        onNodeWithText("INFORMATA2").assertDoesNotExist()
        onNodeWithText("Reinicia 22h59").assertDoesNotExist()
    }

    /** O notch parado fica na tela o tempo todo: o reset é detalhe sob demanda (#189). */
    @Test
    fun `parado o notch nao mostra a hora do reinicio`() = runDesktopComposeUiTest {
        setContent { notch(expanded = false) }

        hoverRing(INFORMATA_RING)
        onNodeWithTag(HUD_BALLOON_TEST_TAG).assertDoesNotExist()
        onNodeWithText("Reinicia 22h59").assertDoesNotExist()
    }

    /** Saldo que não expira não imprime nada no lugar do reset — nem traço. */
    @Test
    fun `cota sem reset nao imprime nada no lugar`() = runDesktopComposeUiTest {
        setContent { notch(expanded = true) }

        hoverRing(DEEPSEEK_RING)
        // O valor aparece no notch e na linha do balão, que para saldo é o próprio valor.
        onAllNodesWithText("\$2.27").assertCountEquals(2)
        onNodeWithText("Reinicia", substring = true).assertDoesNotExist()
        onNodeWithText("—").assertDoesNotExist()
        onNodeWithText("-").assertDoesNotExist()
    }

    @Test
    fun `sem contas o notch mostra a linha de carregamento`() = runDesktopComposeUiTest {
        setContent { notch(list = emptyList()) }

        onNodeWithText("Carregando").assertIsDisplayed()
    }

    // ------------------------------------------------------------ tamanho

    /**
     * A costura entre a geometria e o que o Compose dispõe. A janela é
     * dimensionada antes de existir composição, e as duas contas podiam divergir
     * sem nada reclamar — foi o que cortou o texto da barra antiga ao meio.
     *
     * Nas quatro bordas: o notch tem exatamente o tamanho recolhido, parado **e**
     * aberto (ele não cresce), e com a janela do tamanho aberto que a geometria
     * calcula, o balão de cada conta cabe inteiro nela, com a altura calculada —
     * a coluna de linhas dele mede o mesmo que `hudBalloonHeight` soma.
     */
    @Test
    fun `o notch e o balao tem exatamente o tamanho que a geometria calcula`() {
        val screen = ScreenWorkArea(x = 0.dp, y = 0.dp, size = DpSize(1920.dp, 1080.dp))
        for (edge in HudEdge.entries) {
            val sizes = hudNotchSizes(accounts, edge, "Carregando", hasUpdateIndicator = false)
            for ((index, ring) in listOf(INFORMATA_RING, DEEPSEEK_RING).withIndex()) {
                runDesktopComposeUiTest {
                    val window = hudDockedWindowBounds(edge, 0.5f, sizes, screen)
                    setContent {
                        AppTheme(isDark = true) {
                            Box(modifier = Modifier.size(window.size)) {
                                HudNotch(
                                    accounts = accounts,
                                    edge = edge,
                                    sizes = sizes,
                                    fallbackLabel = "Carregando",
                                    expanded = true,
                                    notchCenter = window.notchCenterInWindow,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    val notchBounds = onNodeWithTag(HUD_CONTENT_TEST_TAG).getUnclippedBoundsInRoot()
                    assertEquals(sizes.collapsed.width, notchBounds.width, "$edge: largura do notch")
                    assertEquals(sizes.collapsed.height, notchBounds.height, "$edge: altura do notch")

                    hoverRing(ring)
                    val account = accounts[index]
                    val expected = hudBalloonBoxSize(edge, hudBalloonHeight(account))
                    val balloon = onNodeWithTag(HUD_BALLOON_TEST_TAG).getUnclippedBoundsInRoot()
                    assertEquals(expected.width, balloon.width, "$edge conta $index: largura do balão")
                    assertEquals(expected.height, balloon.height, "$edge conta $index: altura do balão")
                    assertTrue(balloon.left >= 0.dp && balloon.top >= 0.dp, "$edge conta $index: balão fora da janela ($balloon)")
                    assertTrue(
                        balloon.right <= window.size.width && balloon.bottom <= window.size.height,
                        "$edge conta $index: balão fora da janela ($balloon em ${window.size})"
                    )
                    val column = onNodeWithTag(HUD_BALLOON_CONTENT_TEST_TAG).getUnclippedBoundsInRoot()
                    assertEquals(hudBalloonHeight(account) - HUD_BALLOON_PADDING * 2, column.height, "$edge conta $index: linhas do balão")
                    for (tag in listOf(HUD_MOVE_HANDLE_TAG, HUD_GEAR_HANDLE_TAG)) {
                        val handle = onNodeWithTag(tag).getUnclippedBoundsInRoot()
                        assertTrue(
                            handle.left >= 0.dp && handle.top >= 0.dp && handle.right <= window.size.width && handle.bottom <= window.size.height,
                            "$edge: alça $tag fora da janela ($handle em ${window.size})"
                        )
                    }
                    // Aberto, o notch continua do mesmo tamanho e no mesmo lugar.
                    assertEquals(notchBounds, onNodeWithTag(HUD_CONTENT_TEST_TAG).getUnclippedBoundsInRoot(), "$edge: o notch mudou")
                }
            }
        }
    }

    /**
     * O emoji da conta (issue #287) é selo no canto de cima à direita do anel, só
     * na conta que o tem, e passa no máximo `HUD_EMOJI_BADGE_OVERSHOOT` para fora
     * do anel — em toda borda, porque o selo não gira.
     */
    @Test
    fun `o emoji da conta vira selo no canto do anel e so nela`() {
        for (edge in HudEdge.entries) {
            runDesktopComposeUiTest {
                setContent { notch(edge = edge) }
                val badges = onAllNodesWithTag(HUD_ACCOUNT_EMOJI_TEST_TAG, useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals(1, badges.size, "$edge: um selo, o da conta com emoji")
                val badge = onNodeWithTag(HUD_ACCOUNT_EMOJI_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
                val ring = onNodeWithContentDescription(INFORMATA_RING, useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertEquals(ring.right + HUD_EMOJI_BADGE_OVERSHOOT, badge.right, "$edge: direita do selo")
                assertEquals(ring.top - HUD_EMOJI_BADGE_OVERSHOOT, badge.top, "$edge: topo do selo")
                val notch = onNodeWithTag(HUD_CONTENT_TEST_TAG).getUnclippedBoundsInRoot()
                assertTrue(
                    badge.left >= notch.left && badge.right <= notch.right && badge.top >= notch.top && badge.bottom <= notch.bottom,
                    "$edge: o selo sai do notch ($badge em $notch)"
                )
            }
        }
    }

    // ------------------------------------------------------------ gestos

    /** Como no Codenotch: o clique num anel recoleta aquela conta, e só ela. */
    @Test
    fun `o clique num anel atualiza aquela conta`() = runDesktopComposeUiTest {
        val refreshed = mutableListOf<UsageTargetKey>()
        setContent { notch(onRefreshAccount = { target -> refreshed += target }) }

        onNodeWithContentDescription(DEEPSEEK_RING).performClick()
        assertEquals(listOf(accounts[1].targetKey), refreshed)
        onNodeWithContentDescription(INFORMATA_RING).performClick()
        assertEquals(listOf(accounts[1].targetKey, accounts[0].targetKey), refreshed)
        // Fora dos anéis — a margem antes do primeiro — o clique não atualiza nada.
        onNodeWithTag(HUD_CONTENT_TEST_TAG).performMouseInput { click(Offset(14f, centerY)) }
        assertEquals(2, refreshed.size)
    }

    /** O leitor de tela chega à mesma ação pela semântica do anel. */
    @Test
    fun `cada anel declara a acao de atualizar a conta`() = runDesktopComposeUiTest {
        val refreshed = mutableListOf<UsageTargetKey>()
        setContent { notch(onRefreshAccount = { target -> refreshed += target }) }

        onNode(hasClickAction() and hasAnyDescendant(hasContentDescription(DEEPSEEK_RING)))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(accounts[1].targetKey), refreshed)
    }

    /** O balão traz os botões do card daquela conta, embaixo. */
    @Test
    fun `o balao traz os botoes do card da conta`() = runDesktopComposeUiTest {
        setContent {
            notch(expanded = true, accountActions = { account -> Text("botões de ${account.label}") })
        }

        hoverRing(DEEPSEEK_RING)
        onNodeWithText("botões de DeepSeek").assertIsDisplayed()
    }

    /**
     * Só a mão move: arrastar pelo corpo não tira o notch do lugar, e o ponteiro
     * que escorregou além do limiar também não recoleta a conta do anel.
     */
    @Test
    fun `arrastar pelo corpo nao move nem atualiza`() = runDesktopComposeUiTest {
        var clicks = 0
        val events = mutableListOf<String>()
        setContent {
            notch(
                onDragStart = { events += "start" },
                onDragMove = { events += "move" },
                onDragEnd = { events += "end" },
                onRefreshAccount = { clicks += 1 }
            )
        }

        onNodeWithContentDescription(HUD_NOTCH_DESCRIPTION).performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(60f, 0f))
            release()
        }
        waitForIdle()

        assertEquals(0, clicks)
        assertTrue(events.isEmpty(), "esperava nenhum evento de arrasto, veio $events")
    }

    /** O arrasto sobrevive à recomposição que ele mesmo provoca na janela da HUD. */
    @Test
    fun `o arrasto sobrevive a recomposicao a cada movimento`() = runDesktopComposeUiTest {
        var moves by mutableStateOf(0)
        setContent { notch(expanded = true, fallbackLabel = "movimentos $moves", onDragMove = { moves += 1 }) }

        val target = onNodeWithTag(HUD_MOVE_HANDLE_TAG)
        target.performMouseInput {
            moveTo(center)
            press()
        }
        waitForIdle()
        repeat(3) { step ->
            target.performMouseInput { moveTo(center + Offset(30f * (step + 1), 0f)) }
            waitForIdle()
        }
        target.performMouseInput { release() }
        waitForIdle()

        assertTrue(moves >= 3, "esperava o arrasto continuar depois de recompor, veio $moves")
    }

    @Test
    fun `botao direito troca direto para somente cards sem abrir nem arrastar`() = runDesktopComposeUiTest {
        var opens = 0
        var switches = 0
        val events = mutableListOf<String>()
        setContent {
            notch(
                onDragStart = { events += "start" },
                onDragEnd = { events += "end" },
                onRefreshAccount = { opens += 1 },
                onSwitchToCardsOnly = { switches += 1 }
            )
        }

        onNodeWithContentDescription(HUD_NOTCH_DESCRIPTION).performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        waitForIdle()

        assertEquals(1, switches)
        assertEquals(0, opens)
        assertTrue(events.isEmpty(), "esperava nenhum evento de arrasto, veio $events")
    }

    @Test
    fun `o notch avisa quando o ponteiro entra e sai`() = runDesktopComposeUiTest {
        val reported = mutableListOf<Boolean>()
        setContent { notch(onHoverChange = { hovered -> reported += hovered }) }

        onNodeWithContentDescription(HUD_NOTCH_DESCRIPTION).performMouseInput { enter(center) }
        waitForIdle()
        assertEquals(true, reported.last())

        onNodeWithContentDescription(HUD_NOTCH_DESCRIPTION).performMouseInput { exit(Offset(-1f, -1f)) }
        waitForIdle()
        assertEquals(false, reported.last())
    }

    // ------------------------------------------------------------ faixa compacta

    private val manyAccounts = (1..7).map { index ->
        account("Conta $index", "Sem projeção", AppTone.NEUTRAL, HudQuota("5h", "${index}%", index / 100f, AppTone.NEUTRAL, resetText = null, hasForecast = false))
    }

    /** Compacta, a faixa é a célula do Codenotch: anel e percentual, a palavra vai para o balão. */
    @Test
    fun `compacta a palavra sai da faixa e continua no balao e no anel`() = runDesktopComposeUiTest {
        val sizes = hudNotchSizes(manyAccounts, HudEdge.TOP, "Carregando", false, maxAlong = 400.dp)
        assertTrue(sizes.compact)
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.size(1200.dp, 600.dp)) {
                    HudNotch(accounts = manyAccounts, edge = HudEdge.TOP, sizes = sizes, fallbackLabel = "Carregando", expanded = true)
                }
            }
        }

        onNodeWithText("1%").assertIsDisplayed()
        onAllNodesWithText("Sem projeção").assertCountEquals(0)
        hoverRing("Conta 1 · Sem projeção · 5h 1%")
        onNodeWithText("Conta 1").assertIsDisplayed()
        onNodeWithText("Sem projeção").assertIsDisplayed()
    }

    /** A costura de tamanho vale também compacta, nas quatro bordas. */
    @Test
    fun `compacta o notch tem o tamanho que a geometria calcula`() {
        for (edge in HudEdge.entries) {
            runDesktopComposeUiTest {
                val sizes = hudNotchSizes(manyAccounts, edge, "Carregando", false, maxAlong = 300.dp)
                assertTrue(sizes.compact, "$edge")
                setContent {
                    AppTheme(isDark = true) {
                        Box(modifier = Modifier.size(1400.dp, 1000.dp)) {
                            HudNotch(
                                accounts = manyAccounts, edge = edge, sizes = sizes, fallbackLabel = "Carregando"
                            )
                        }
                    }
                }
                val bounds = onNodeWithTag(HUD_CONTENT_TEST_TAG).getUnclippedBoundsInRoot()
                assertEquals(sizes.collapsed.width, bounds.width, "$edge: largura")
                assertEquals(sizes.collapsed.height, bounds.height, "$edge: altura")
            }
        }
    }

    // ------------------------------------------------------------ alças (rodada 3)

    @Test
    fun `as alcas aparecem so com o notch aberto`() = runDesktopComposeUiTest {
        var open by mutableStateOf(false)
        setContent { notch(expanded = open) }

        onNodeWithTag(HUD_MOVE_HANDLE_TAG).assertDoesNotExist()
        onNodeWithTag(HUD_GEAR_HANDLE_TAG).assertDoesNotExist()
        open = true
        waitForIdle()
        onNodeWithContentDescription("Mover a barra HUD").assertIsDisplayed()
        onNodeWithContentDescription(GEAR).assertIsDisplayed()
    }

    /** A mão é o jeito descobrível de mover: arrastar por ela move e não abre nada. */
    @Test
    fun `arrastar pela mao move o notch`() = runDesktopComposeUiTest {
        var opens = 0
        val events = mutableListOf<String>()
        setContent {
            notch(
                expanded = true,
                onDragStart = { events += "start" },
                onDragMove = { events += "move" },
                onDragEnd = { events += "end" },
                onRefreshAccount = { opens += 1 }
            )
        }

        onNodeWithTag(HUD_MOVE_HANDLE_TAG).performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(0f, 80f))
            release()
        }
        waitForIdle()

        assertEquals(0, opens)
        assertEquals("start", events.first())
        assertEquals("end", events.last())
    }

    /** Carregando, a mão continua na tela: tirá-la da composição cancelaria o gesto. */
    @Test
    fun `durante o arrasto a mao continua e o balao some`() = runDesktopComposeUiTest {
        setContent { notch(expanded = false, dragging = true) }

        onNodeWithTag(HUD_MOVE_HANDLE_TAG).assertIsDisplayed()
        onNodeWithTag(HUD_BALLOON_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `a engrenagem chama a acao dela`() = runDesktopComposeUiTest {
        var gears = 0
        setContent { notch(expanded = true, onGearClick = { gears += 1 }) }

        onNodeWithContentDescription(GEAR).performClick()
        assertEquals(1, gears)
    }

    /** Ir do notch até uma alça não pode fechar o notch: a alça conta como "em cima". */
    @Test
    fun `o ponteiro sobre uma alca conta como sobre o notch`() = runDesktopComposeUiTest {
        val reported = mutableListOf<Boolean>()
        setContent { notch(expanded = true, onHoverChange = { hovered -> reported += hovered }) }

        onNodeWithTag(HUD_GEAR_HANDLE_TAG).performMouseInput { enter(center) }
        waitForIdle()
        assertEquals(true, reported.last())
    }

    // ------------------------------------------------------------ balão da engrenagem (rodada 3)

    /** O conteúdo de teste do balão da engrenagem: os modos e uma ação do rodapé. */
    @Composable
    private fun appBalloonFixture(onMode: (WindowMode) -> Unit, onRefresh: () -> Unit) {
        HudAppBalloonContent(
            language = AppLanguage.PT,
            appVersion = CURRENT_APP_VERSION,
            countdown = null,
            updateIndicator = null,
            onWindowModeChange = onMode,
            actions = {
                FooterActionGroup(language = AppLanguage.PT, onRefresh = onRefresh, onOpenSettings = {})
            }
        )
    }

    @Composable
    private fun notchWithActions(onMode: (WindowMode) -> Unit = {}, onRefresh: () -> Unit = {}) {
        AppTheme(isDark = true) {
            Box(modifier = Modifier.size(900.dp, 600.dp)) {
                HudNotch(
                    accounts = accounts,
                    edge = HudEdge.TOP,
                    sizes = hudNotchSizes(accounts, HudEdge.TOP, "Carregando", false),
                    fallbackLabel = "Carregando",
                    expanded = true,
                    appBalloon = { appBalloonFixture(onMode, onRefresh) },
                    appBalloonHeight = hudAppBalloonHeight(hasUpdateIndicator = false),
                    gearDescription = GEAR
                )
            }
        }
    }

    /** Na barra HUD não há rodapé: a engrenagem abre o que ele oferece. */
    @Test
    fun `a engrenagem abre o balao com as acoes do rodape e o segundo clique nao fecha`() = runDesktopComposeUiTest {
        var refreshes = 0
        setContent { notchWithActions(onRefresh = { refreshes += 1 }) }

        onNodeWithContentDescription(GEAR).performClick()
        waitForIdle()
        onNodeWithTag(HUD_APP_BALLOON_CONTENT_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(HUD_APP_BALLOON_VERSION_TEST_TAG).assertTextEquals("v$CURRENT_APP_VERSION")
        onNodeWithText("Modo de janela").assertIsDisplayed()
        // A mesma fileira do rodapé, pelas mesmas descrições.
        onNodeWithContentDescription("Atualizar agora").performClick()
        assertEquals(1, refreshes)
        onNodeWithContentDescription("Abrir configurações").assertIsDisplayed()
        onNodeWithContentDescription("Abrir ajuda").assertIsDisplayed()

        // Com o hover abrindo o balão (#317), alternar no clique fecharia o que
        // o próprio ponteiro acabou de abrir.
        onNodeWithContentDescription(GEAR).performClick()
        waitForIdle()
        onNodeWithTag(HUD_APP_BALLOON_CONTENT_TEST_TAG).assertIsDisplayed()
    }

    /** O ponteiro sobre a engrenagem abre o balão dela sem clique, como o anel abre o da conta (#317). */
    @Test
    fun `o ponteiro sobre a engrenagem abre o balao dela`() = runDesktopComposeUiTest {
        setContent { notchWithActions() }

        onNodeWithTag(HUD_GEAR_HANDLE_TAG).performMouseInput { enter(center) }
        waitForIdle()
        onNodeWithTag(HUD_APP_BALLOON_CONTENT_TEST_TAG).assertIsDisplayed()
    }

    /** Sem balão da engrenagem, o hover não dispara a ação dela: ação é do clique. */
    @Test
    fun `sem balao o ponteiro sobre a engrenagem nao chama a acao dela`() = runDesktopComposeUiTest {
        var gears = 0
        setContent { notch(expanded = true, onGearClick = { gears += 1 }) }

        onNodeWithTag(HUD_GEAR_HANDLE_TAG).performMouseInput { enter(center) }
        waitForIdle()
        assertEquals(0, gears)
        onNodeWithTag(HUD_BALLOON_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `os modos de janela saem do balao da engrenagem com o corrente marcado`() = runDesktopComposeUiTest {
        val chosen = mutableListOf<WindowMode>()
        setContent { notchWithActions(onMode = { mode -> chosen += mode }) }

        onNodeWithContentDescription(GEAR).performClick()
        waitForIdle()
        onNodeWithTag(HUD_APP_BALLOON_MODE_TAG_PREFIX + WindowMode.HUD.name).assertIsSelected()
        onNodeWithTag(HUD_APP_BALLOON_MODE_TAG_PREFIX + WindowMode.STANDARD.name).performClick()
        onNodeWithTag(HUD_APP_BALLOON_MODE_TAG_PREFIX + WindowMode.CARDS_ONLY.name).performClick()

        assertEquals(listOf(WindowMode.STANDARD, WindowMode.CARDS_ONLY), chosen)
    }

    /** Com o balão da engrenagem aberto, passar por um anel mostra aquela conta. */
    @Test
    fun `um anel sob o ponteiro troca o balao da engrenagem pelo da conta`() = runDesktopComposeUiTest {
        setContent { notchWithActions() }

        onNodeWithContentDescription(GEAR).performClick()
        waitForIdle()
        hoverRing(INFORMATA_RING)
        onNodeWithText("INFORMATA2").assertIsDisplayed()
        onNodeWithTag(HUD_APP_BALLOON_CONTENT_TEST_TAG).assertDoesNotExist()
    }

    /**
     * A coluna do balão da engrenagem mede o que `hudAppBalloonHeight` soma: sem
     * atualização, só com o banner (baixando) e com o banner mais o botão.
     */
    @Test
    fun `o balao da engrenagem tem a altura que a geometria calcula`() {
        val cases = listOf(
            null to null,
            DOWNLOADING_INDICATOR to null,
            READY_INDICATOR to {}
        )
        for ((update, action) in cases) {
            runDesktopComposeUiTest {
                setContent {
                    AppTheme(isDark = true) {
                        Box(modifier = Modifier.width(HUD_BALLOON_WIDTH - HUD_BALLOON_PADDING * 2)) {
                            HudAppBalloonContent(
                                language = AppLanguage.PT,
                                appVersion = CURRENT_APP_VERSION,
                                countdown = null,
                                updateIndicator = update,
                                onWindowModeChange = {},
                                actions = { FooterActionGroup(language = AppLanguage.PT, onRefresh = {}, onOpenSettings = {}) },
                                onUpdateAction = action
                            )
                        }
                    }
                }
                val column = onNodeWithTag(HUD_APP_BALLOON_CONTENT_TEST_TAG).getUnclippedBoundsInRoot()
                assertEquals(
                    hudAppBalloonHeight(update != null, action != null) - HUD_BALLOON_PADDING * 2,
                    column.height,
                    "atualização=$update, ação=${action != null}"
                )
            }
        }
    }

    @Composable
    private fun appBalloonWithUpdate(update: HudUpdateIndicator, onUpdateAction: (() -> Unit)?) {
        AppTheme(isDark = true) {
            Box(modifier = Modifier.width(HUD_BALLOON_WIDTH - HUD_BALLOON_PADDING * 2)) {
                HudAppBalloonContent(
                    language = AppLanguage.PT,
                    appVersion = CURRENT_APP_VERSION,
                    countdown = null,
                    updateIndicator = update,
                    onWindowModeChange = {},
                    actions = { FooterActionGroup(language = AppLanguage.PT, onRefresh = {}, onOpenSettings = {}) },
                    onUpdateAction = onUpdateAction
                )
            }
        }
    }

    /**
     * A mesma ação da faixa do modo padrão, no balão da engrenagem (#225), como
     * banner e botão (#291): o rótulo com seta sobre fundo transparente não
     * parecia botão.
     */
    @Test
    fun `o balao da engrenagem oferece reiniciar o app e atualizar`() = runDesktopComposeUiTest {
        var restarts = 0
        setContent { appBalloonWithUpdate(READY_INDICATOR, onUpdateAction = { restarts += 1 }) }

        onNodeWithTag(HUD_APP_BALLOON_VERSION_TEST_TAG).assertTextEquals("v$CURRENT_APP_VERSION")
        onNodeWithTag(HUD_APP_BALLOON_UPDATE_BANNER_TAG).assertIsDisplayed()
        onNodeWithText("Versão 38.1.0 pronta").assertIsDisplayed()
        onNodeWithText("Aplicada ao fechar o Usage Monitor").assertIsDisplayed()
        onNodeWithTag(HUD_APP_BALLOON_UPDATE_ACTION_TAG)
            .assertIsDisplayed()
            .assertTextEquals("Reiniciar o app e atualizar")
            .performClick()
        assertEquals(1, restarts)
    }

    /** Baixando não tem ação, como na faixa: alvo clicável sem rótulo seria invisível. */
    @Test
    fun `baixando o balao da engrenagem nao tem acao`() = runDesktopComposeUiTest {
        setContent {
            appBalloonWithUpdate(DOWNLOADING_INDICATOR, onUpdateAction = null)
        }

        onNodeWithText("Baixando 38.1.0").assertIsDisplayed()
        onNodeWithText("42% concluído").assertIsDisplayed()
        onNodeWithTag(HUD_APP_BALLOON_UPDATE_ACTION_TAG).assertDoesNotExist()
    }

    // ------------------------------------------------------------ contagem (#185, #269)

    /**
     * Com a cadência de 60 s (issue #269) a contagem reiniciava a cada minuto na
     * borda da tela. Ela saiu da faixa: nem parado nem aberto o notch a mostra.
     */
    @Test
    fun `a faixa do notch nao mostra a contagem`() = runDesktopComposeUiTest {
        var open by mutableStateOf(false)
        setContent { notch(expanded = open) }

        onAllNodesWithContentDescription(countdown).assertCountEquals(0)
        onNodeWithTag(HUD_COUNTDOWN_CLOCK_TAG).assertDoesNotExist()
        open = true
        waitForIdle()
        onAllNodesWithContentDescription(countdown).assertCountEquals(0)
        onNodeWithTag(HUD_COUNTDOWN_CLOCK_TAG).assertDoesNotExist()
    }

    /** A contagem mora no cabeçalho do balão da engrenagem, com o relógio. */
    @Test
    fun `o balao da engrenagem mostra a contagem`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                HudAppBalloonContent(
                    language = AppLanguage.PT,
                    appVersion = CURRENT_APP_VERSION,
                    countdown = { countdownOf(now + 2.minutes + 5.seconds) },
                    updateIndicator = null,
                    onWindowModeChange = {},
                    actions = {}
                )
            }
        }

        onNodeWithText("02:05").assertIsDisplayed()
        onNodeWithContentDescription(countdown).assertIsDisplayed()
        onNodeWithTag(HUD_COUNTDOWN_CLOCK_TAG).assertIsDisplayed()
    }

    @Composable
    private fun countdownOf(
        nextRefreshAt: Instant,
        interval: Duration? = 10.minutes,
        nowProvider: () -> Instant = { now },
        waitNextTick: suspend () -> Unit = {},
        updatesEnabled: Boolean = false
    ) {
        HudCountdown(
            nextRefreshAt = nextRefreshAt,
            description = countdown,
            interval = interval,
            nowProvider = nowProvider,
            waitNextTick = waitNextTick,
            updatesEnabled = updatesEnabled
        )
    }

    @Test
    fun `a contagem decrementa e para em zero`() = runDesktopComposeUiTest {
        val tickChannel = Channel<Unit>(capacity = Channel.UNLIMITED)
        var currentNow = now
        setContent {
            AppTheme(isDark = true) {
                countdownOf(
                    nextRefreshAt = now + 3.seconds,
                    nowProvider = { currentNow },
                    waitNextTick = { tickChannel.receive() },
                    updatesEnabled = true
                )
            }
        }

        onNodeWithText("00:03").assertIsDisplayed()
        currentNow = now + 1.seconds
        tickChannel.trySend(Unit)
        waitForIdle()
        onNodeWithText("00:02").assertIsDisplayed()
        currentNow = now + 3.seconds
        tickChannel.trySend(Unit)
        waitForIdle()
        onNodeWithText("00:00").assertIsDisplayed()
    }

    /**
     * Com o intervalo, o ícone da contagem é o relógio que esvazia (#293); sem
     * ele continua o ↻. Nos dois casos a descrição é a mesma.
     */
    @Test
    fun `com o intervalo a contagem usa o relogio`() = runDesktopComposeUiTest {
        var interval by mutableStateOf<Duration?>(10.minutes)
        setContent { AppTheme(isDark = true) { countdownOf(now + 2.minutes + 5.seconds, interval = interval) } }

        onNodeWithTag(HUD_COUNTDOWN_CLOCK_TAG).assertIsDisplayed()
        onNodeWithContentDescription(countdown).assertIsDisplayed()
        interval = null
        waitForIdle()
        onNodeWithTag(HUD_COUNTDOWN_CLOCK_TAG).assertDoesNotExist()
        onNodeWithContentDescription(countdown).assertIsDisplayed()
    }

    /**
     * O relógio é desenho, e só bitmap o pega: com o intervalo quase inteiro pela
     * frente o setor ocupa o quadrante de baixo à esquerda; faltando pouco ele
     * sumiu dali. Compara o mesmo pixel nos dois instantes.
     */
    @Test
    fun `o relogio esvazia com o tempo`() {
        fun lowerLeft(next: Instant): Color {
            var pixel = Color.Unspecified
            runDesktopComposeUiTest {
                setContent { AppTheme(isDark = true) { countdownOf(next) } }
                val pixels = onNodeWithTag(HUD_COUNTDOWN_CLOCK_TAG).captureToImage().toPixelMap()
                pixel = pixels[pixels.width / 2 - 2, pixels.height / 2 + 2]
            }
            return pixel
        }
        val full = lowerLeft(now + 9.minutes + 50.seconds)
        val almostDue = lowerLeft(now + 10.seconds)
        assertTrue(full != almostDue, "o setor devia ter saído do quadrante de baixo à esquerda")
    }

    // ------------------------------------------------------------ atualização (#225, #291)

    @Test
    fun `sem atualizacao a engrenagem nao tem ponto`() = runDesktopComposeUiTest {
        var open by mutableStateOf(false)
        setContent { notch(expanded = open) }

        onNodeWithTag(HUD_GEAR_HINT_UPDATE_TAG).assertDoesNotExist()
        open = true
        waitForIdle()
        onNodeWithTag(HUD_GEAR_UPDATE_DOT_TAG).assertDoesNotExist()
        onNodeWithContentDescription(GEAR).assertIsDisplayed()
    }

    /**
     * A atualização não ocupa a faixa de anéis (#291): o ícone de celular com seta
     * não dizia "versão nova". Parado, o arco da engrenagem toma o tom; aberto, a
     * engrenagem ganha o ponto e a frase vai na descrição dela, porque cor nunca
     * informa sozinha. E nenhum clique no notch reinicia o app (#225).
     */
    @Test
    fun `a atualizacao e o ponto da engrenagem, parado e aberto`() = runDesktopComposeUiTest {
        var refreshes = 0
        var gearClicks = 0
        var open by mutableStateOf(false)
        setContent {
            notch(
                expanded = open,
                updateIndicator = READY_INDICATOR,
                onRefreshAccount = { refreshes += 1 },
                onGearClick = { gearClicks += 1 }
            )
        }

        onNodeWithTag(HUD_GEAR_HINT_UPDATE_TAG).assertExists()
        onAllNodesWithContentDescription(READY_INDICATOR.description, substring = true).assertCountEquals(0)
        open = true
        waitForIdle()
        onNodeWithTag(HUD_GEAR_UPDATE_DOT_TAG).assertIsDisplayed()
        onNodeWithContentDescription("$GEAR · ${READY_INDICATOR.description}").assertIsDisplayed()
        assertEquals(0, refreshes)
        assertEquals(0, gearClicks)
    }

    /** O notch recolhido tem o mesmo tamanho com e sem atualização: ela não entra na faixa. */
    @Test
    fun `a atualizacao nao muda o tamanho do notch recolhido`() {
        HudEdge.entries.forEach { edge ->
            listOf(accounts, emptyList()).forEach { list ->
                val without = hudNotchSizes(list, edge, "Carregando", hasUpdateIndicator = false)
                val withUpdate = hudNotchSizes(list, edge, "Carregando", hasUpdateIndicator = true, hasUpdateAction = true)
                assertEquals(without.collapsed, withUpdate.collapsed, "$edge, ${list.size} contas")
            }
        }
    }

    // ------------------------------------------------------------ movimento contínuo

    /**
     * O arco de sessão ativa **gira** só com a política contínua: com ela e o
     * relógio manual, dois instantes desenham o anel diferente. Sem ela
     * (`waitForIdle` voltou em todos os outros testes deste arquivo) nada gira.
     */
    @Test
    fun `o arco de sessao ativa gira so com a politica continua`() {
        val active = listOf(accounts.first().copy(sessionActive = true))
        fun frames(policy: AppMotionPolicy): Pair<PixelMap, PixelMap> {
            lateinit var first: PixelMap
            lateinit var second: PixelMap
            runDesktopComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    AppTheme(isDark = true, motion = policy) {
                        // Fundo opaco: sobre transparente o antialiasing acumula
                        // alfa a cada quadro composto, e dois instantes diferem
                        // mesmo parados (o mesmo artefato do teste da barra).
                        // A caixa em volta: a órbita gira fora dos limites do anel.
                        Box(modifier = Modifier.testTag(RING_FRAME).background(Color.Black).padding(6.dp)) {
                            AppUsageRing(
                                arcs = listOf(AppRingArc(0.3f, AppTone.OK)),
                                description = "anel",
                                active = active.first().sessionActive
                            )
                        }
                    }
                }
                // Depois do desenho de entrada dos arcos (issue #322), que é
                // finito e acontece com qualquer política não reduzida.
                mainClock.advanceTimeBy(RING_ENTRANCE_SETTLE_MILLIS)
                first = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()
                mainClock.advanceTimeBy(350)
                second = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()
            }
            return first to second
        }

        val (liveA, liveB) = frames(AppMotionPolicy.Live)
        assertTrue(differs(liveA, liveB), "com a política contínua o arco devia ter girado")
        val (staticA, staticB) = frames(AppMotionPolicy.Static)
        assertTrue(!differs(staticA, staticB), "sem a política o arco devia ficar parado")
    }

    /**
     * Na primeira composição o arco se desenha a partir de zero (issue #322):
     * no quadro inicial ele ainda não está lá, e depois está. Com "Reduzir
     * animações" o primeiro quadro já é o final.
     */
    @Test
    fun `o arco se desenha na entrada e nasce pronto com reduzir animacoes`() {
        fun frames(policy: AppMotionPolicy): Pair<PixelMap, PixelMap> {
            lateinit var first: PixelMap
            lateinit var settled: PixelMap
            runDesktopComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    AppTheme(isDark = true, motion = policy) {
                        Box(modifier = Modifier.testTag(RING_FRAME).background(Color.Black).padding(6.dp)) {
                            AppUsageRing(arcs = listOf(AppRingArc(0.6f, AppTone.OK)), description = "anel")
                        }
                    }
                }
                mainClock.advanceTimeByFrame()
                first = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()
                mainClock.advanceTimeBy(RING_ENTRANCE_SETTLE_MILLIS)
                settled = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()
            }
            return first to settled
        }

        val (animatedStart, animatedEnd) = frames(AppMotionPolicy.Static)
        assertTrue(differs(animatedStart, animatedEnd), "o arco devia se desenhar depois do primeiro quadro")
        val (reducedStart, reducedEnd) = frames(AppMotionPolicy.Reduced)
        assertTrue(!differs(reducedStart, reducedEnd), "com reduzir animações o arco devia nascer pronto")
    }

    /**
     * A órbita de sessão ativa gira **por fora** do anel: o miolo fica igual com
     * ela e sem ela, e é isso que mantém a marca do fornecedor do mesmo tamanho
     * que a das contas paradas. Fora do anel ela aparece.
     */
    @Test
    fun `a sessao ativa nao encolhe o miolo do anel`() = runDesktopComposeUiTest {
        var active by mutableStateOf(false)
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.testTag(RING_FRAME).background(Color.Black).padding(6.dp)) {
                    AppUsageRing(
                        arcs = listOf(AppRingArc(0.3f, AppTone.OK), AppRingArc(0.6f, AppTone.OK)),
                        description = "anel",
                        size = HUD_RING_SIZE,
                        stroke = HUD_RING_STROKE,
                        gap = HUD_RING_GAP,
                        active = active
                    )
                }
            }
        }
        val still = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()
        active = true
        waitForIdle()
        val working = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()

        // O miolo: o quadrado inscrito no arco de dentro, com a moldura de 6dp.
        val frame = 6.dp.value * density.density
        val ring = HUD_RING_SIZE.value * density.density
        val inner = (HUD_RING_STROKE.value + HUD_RING_GAP.value) * 2 * density.density
        val from = (frame + inner + 1).toInt()
        val to = (frame + ring - inner - 1).toInt()
        for (y in from until to) {
            for (x in from until to) {
                assertEquals(still[x, y], working[x, y], "miolo mudou em ($x, $y)")
            }
        }
        assertTrue(differs(still, working), "a órbita devia aparecer em volta do anel")
    }

    /**
     * O arco de fora é o da semanal (issue #278), medido no **bitmap**: a ordem
     * de `rings` está no modelo, mas quem decide qual círculo fica por fora é o
     * desenho, e o que o usuário via era o desenho. A semanal crítica e a 5h
     * normal têm tons diferentes; o topo de cada círculo diz qual é qual.
     */
    @Test
    fun `o arco de fora e o da semanal`() = runDesktopComposeUiTest {
        val informata = accounts.first()
        var critical = Color.Unspecified
        var ok = Color.Unspecified
        setContent {
            AppTheme(isDark = true) {
                critical = AppTone.CRITICAL.color()
                ok = AppTone.OK.color()
                Box(modifier = Modifier.testTag(RING_FRAME).background(Color.Black)) {
                    AppUsageRing(
                        // Fração cheia: o topo de cada círculo fica pintado.
                        arcs = informata.rings.map { quota -> AppRingArc(1f, quota.tone) },
                        description = "anel",
                        size = HUD_RING_SIZE,
                        stroke = HUD_RING_STROKE,
                        gap = HUD_RING_GAP
                    )
                }
            }
        }
        val pixels = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()
        val stroke = HUD_RING_STROKE.value * density.density
        val gap = HUD_RING_GAP.value * density.density
        val center = pixels.width / 2
        val outer = pixels[center, (stroke / 2).toInt()]
        val inner = pixels[center, (stroke + gap + stroke / 2).toInt()]

        assertTrue(close(outer, critical), "arco de fora $outer devia ser o da semanal (crítica, $critical)")
        assertTrue(close(inner, ok), "arco de dentro $inner devia ser o da 5h (normal, $ok)")
    }

    /** Cada cota do balão carrega o glifo do anel dela, e só as que viram anel. */
    @Test
    fun `o balao marca cada cota com a posicao do anel`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(HUD_BALLOON_WIDTH - HUD_BALLOON_PADDING * 2)) {
                    HudAccountBalloonContent(account = accounts.first(), language = AppLanguage.PT)
                }
            }
        }
        onNodeWithTag(HUD_BALLOON_RING_LEGEND_TAG_PREFIX + 0).assertIsDisplayed()
        onNodeWithTag(HUD_BALLOON_RING_LEGEND_TAG_PREFIX + 1).assertIsDisplayed()
        onNodeWithTag(HUD_BALLOON_RING_LEGEND_TAG_PREFIX + 2).assertDoesNotExist()
    }

    /** A seção de sessões CLI aparece com os sinais da conta, e some sem eles (#265). */
    @Test
    fun `o balao mostra os sinais de sessao da conta`() = runDesktopComposeUiTest {
        var shown by mutableStateOf(accounts.first())
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(HUD_BALLOON_WIDTH - HUD_BALLOON_PADDING * 2)) {
                    HudAccountBalloonContent(account = shown, language = AppLanguage.PT)
                }
            }
        }
        onNodeWithTag(HUD_BALLOON_SESSION_SIGNALS_TAG).assertIsDisplayed()
        onNodeWithText("Sessões CLI").assertIsDisplayed()
        onNodeWithText("Contexto saturado · 1 sessão").assertIsDisplayed()
        onNodeWithText("Sem resposta há 2h10").assertIsDisplayed()

        shown = accounts.first().copy(sessionSignals = emptyList())
        waitForIdle()
        onNodeWithTag(HUD_BALLOON_SESSION_SIGNALS_TAG).assertDoesNotExist()
    }

    /** Um anel só não tem posição a apontar: o saldo do DeepSeek fica sem glifo. */
    @Test
    fun `conta de um anel so nao tem glifo`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(HUD_BALLOON_WIDTH - HUD_BALLOON_PADDING * 2)) {
                    HudAccountBalloonContent(account = accounts.last(), language = AppLanguage.PT)
                }
            }
        }
        onNodeWithTag(HUD_BALLOON_RING_LEGEND_TAG_PREFIX + 0).assertDoesNotExist()
    }

    private fun close(a: Color, b: Color): Boolean {
        val tolerance = 0.08f
        return kotlin.math.abs(a.red - b.red) < tolerance &&
            kotlin.math.abs(a.green - b.green) < tolerance &&
            kotlin.math.abs(a.blue - b.blue) < tolerance
    }

    private fun differs(a: PixelMap, b: PixelMap): Boolean {
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a[x, y] != b[x, y]) return true
            }
        }
        return false
    }

    private val RING_FRAME = "ringFrame"
    /** Folga para o desenho de entrada dos arcos assentar (mola `GENTLE` + escalonamento). */
    private val RING_ENTRANCE_SETTLE_MILLIS = 1_500L

    /** Põe o ponteiro no anel da conta, achado pela frase inteira da semântica dele. */
    private fun ComposeUiTest.hoverRing(description: String) {
        onNodeWithContentDescription(description).performMouseInput { moveTo(center) }
        waitForIdle()
    }

    private fun account(label: String, word: String, tone: AppTone, vararg quotas: HudQuota): HudAccount {
        return HudAccount(
            planLabel = if (label == "INFORMATA2") "Max 20x" else null,
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, label),
            label = label,
            statusLabel = word,
            tone = tone,
            quotas = quotas.toList(),
            focusIndex = quotas.indices.maxByOrNull { index -> quotas[index].fraction } ?: 0
        )
    }
}
