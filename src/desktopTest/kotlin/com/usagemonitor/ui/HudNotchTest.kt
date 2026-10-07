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
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_UPDATE_ACTION_TAG
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_UPDATE_BANNER_TAG
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_VERSION_TEST_TAG
import com.usagemonitor.presentation.ui.HudAppBalloonContent
import com.usagemonitor.presentation.ui.HudAccountBalloonContent
import com.usagemonitor.presentation.ui.HUD_BALLOON_RING_LEGEND_TAG_PREFIX
import com.usagemonitor.presentation.ui.HUD_BALLOON_SESSION_SIGNALS_TAG
import com.usagemonitor.presentation.ui.HudSessionSignal
import com.usagemonitor.presentation.ui.HudUsedLeft
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.FooterActionGroup
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.hudAppBalloonHeight
import com.usagemonitor.HUD_BALLOON_WIDTH
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertIsSelected
import com.usagemonitor.presentation.ui.HUD_GEAR_HANDLE_TAG
import com.usagemonitor.presentation.ui.HUD_MOVE_HANDLE_TAG
import com.usagemonitor.presentation.ui.HUD_RETRACT_HANDLE_TAG
import com.usagemonitor.presentation.ui.HUD_RETRACTED_STRIP_TAG
import androidx.compose.ui.test.onRoot
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.captureToImage
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
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
import com.usagemonitor.presentation.ui.HudPresence
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlin.time.Instant
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
 * não faz nada, a contagem sai uma vez só, o reset só aberto.
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
            HudQuota("5h", "28%", 0.28f, AppTone.OK, resetText = "22h59", hasForecast = true, title = "Sessão 5h", usedLeft = HudUsedLeft("28% usado", "72% restante"), periodType = PeriodType.INTERVAL),
            HudQuota("7d", "9%", 0.09f, AppTone.CRITICAL, resetText = "Ter 21h00", hasForecast = true, title = "Semanal", usedLeft = HudUsedLeft("9% usado", "91% restante"), periodType = PeriodType.WEEKLY)
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
        dragging: Boolean = false,
        onGearClick: () -> Unit = {},
        motion: AppMotionPolicy = AppMotionPolicy.Static,
        autoRetract: Boolean = false,
        onToggleAutoRetract: (() -> Unit)? = null
    ) {
        AppTheme(isDark = true, motion = motion) {
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
                    dragging = dragging,
                    onGearClick = onGearClick,
                    gearDescription = GEAR,
                    autoRetract = autoRetract,
                    onToggleAutoRetract = onToggleAutoRetract
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
        // F10 adaptado: o usado e o restante em dois textos, lidos como uma linha só.
        onNodeWithContentDescription("28% usado · 72% restante").assertIsDisplayed()
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

    /** O botão direito levava ao modo somente cards, que saiu do app; hoje é engolido. */
    @Test
    fun `botao direito nao recoleta nem arrasta`() = runDesktopComposeUiTest {
        var opens = 0
        val events = mutableListOf<String>()
        setContent {
            notch(
                onDragStart = { events += "start" },
                onDragEnd = { events += "end" },
                onRefreshAccount = { opens += 1 }
            )
        }

        onNodeWithContentDescription(HUD_NOTCH_DESCRIPTION).performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
        waitForIdle()

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

    // ------------------------------------------------------------ saída de conta (K1)

    /**
     * Desligar uma API (K1): durante o colapso o notch não se mexe; depois a vaga
     * fecha e o notch recolhe **dentro da janela** até o tamanho sem a conta,
     * enquanto as que ficam passam de compactas a completas. Sem salto no fim.
     */
    @Test
    fun `ao sair uma conta o notch recolhe ate o tamanho sem ela e as vizinhas desdobram`() = runDesktopComposeUiTest {
        val staying = listOf(
            account("Padrão", "Crítico", AppTone.CRITICAL,
                HudQuota("7d", "26%", 0.26f, AppTone.OK, resetText = null, hasForecast = true),
                HudQuota("5h", "92%", 0.92f, AppTone.CRITICAL, resetText = null, hasForecast = true)),
            account("Codex", "Normal", AppTone.OK,
                HudQuota("7d", "28%", 0.28f, AppTone.OK, resetText = null, hasForecast = true),
                HudQuota("5h", "22%", 0.22f, AppTone.OK, resetText = null, hasForecast = true))
        )
        val leaving = account("Go", "Sem projeção", AppTone.NEUTRAL,
            HudQuota("mensal", "3%", 0.03f, AppTone.NEUTRAL, resetText = null, hasForecast = false)
        ).copy(presence = HudPresence.LEAVING)
        val all = staying + leaving
        // O teto exato da faixa completa das duas: com a terceira, compacta.
        val budget = hudNotchSizes(staying, HudEdge.RIGHT, "", false).collapsed.height
        val before = hudNotchSizes(all, HudEdge.RIGHT, "", false, maxAlong = budget)
        val after = hudNotchSizes(staying, HudEdge.RIGHT, "", false, maxAlong = budget)
        assertTrue(before.compact && !after.compact)
        mainClock.autoAdvance = false
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.size(600.dp, 900.dp)) {
                    HudNotch(accounts = all, edge = HudEdge.RIGHT, sizes = before, settledSizes = after, fallbackLabel = "")
                }
            }
        }
        val height = { onNodeWithTag(HUD_CONTENT_TEST_TAG).getUnclippedBoundsInRoot().height }
        mainClock.advanceTimeByFrame()
        assertEquals(before.collapsed.height, height(), "o notch mudou antes do colapso acabar")
        onAllNodesWithText("Normal").assertCountEquals(0)

        mainClock.advanceTimeBy(AppGargantuaTokens.collapseMillis - 50L)
        assertEquals(before.collapsed.height, height(), "o notch mudou durante o colapso")

        mainClock.advanceTimeBy(50L + AppGargantuaTokens.departureSettleMillis / 2L)
        val middle = height()
        assertTrue(middle < before.collapsed.height && middle > after.collapsed.height, "no meio da vaga o notch devia estar recolhendo: $middle")

        mainClock.advanceTimeBy(AppGargantuaTokens.departureSettleMillis.toLong())
        assertEquals(after.collapsed.height, height(), "assentado, o notch tem o tamanho sem a conta")
        onNodeWithText("Normal").assertIsDisplayed()
        onNodeWithText("5h 22%").assertIsDisplayed()
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

    // ------------------------------------------------------------ modo recolher (#400)

    /**
     * Opacidade do pixel no meio do notch, perto da borda de dentro: fora da
     * faixa recolhida, então só o notch revelado pela íris o pinta.
     */
    private fun ComposeUiTest.notchInnerAlpha(edge: HudEdge = HudEdge.TOP, atCorner: Boolean = false): Float {
        val bounds = onNodeWithTag(HUD_CONTENT_TEST_TAG).getUnclippedBoundsInRoot()
        val pixels = onRoot().captureToImage().toPixelMap()
        val x = when {
            edge == HudEdge.LEFT -> bounds.right - 12.dp
            edge == HudEdge.RIGHT -> bounds.left + 12.dp
            // O canto de dentro, o último ponto que o disco alcança.
            atCorner -> bounds.left + 20.dp
            else -> (bounds.left + bounds.right) / 2
        }
        val y = when (edge) {
            HudEdge.TOP -> bounds.bottom - 12.dp
            HudEdge.BOTTOM -> bounds.top + 12.dp
            else -> (bounds.top + bounds.bottom) / 2
        }
        return pixels[x.value.toInt(), y.value.toInt()].alpha
    }

    @Test
    fun `sem o modo recolher a hud nao tem faixa nem alfinete`() = runDesktopComposeUiTest {
        setContent { notch(expanded = true) }

        onNodeWithTag(HUD_RETRACTED_STRIP_TAG).assertDoesNotExist()
        onNodeWithTag(HUD_RETRACT_HANDLE_TAG).assertDoesNotExist()
        assertTrue(notchInnerAlpha() > 0.9f)
    }

    /** Recolhida, só a faixa: o notch some e cada conta vira um ponto com a palavra na descrição. */
    @Test
    fun `recolhida so a faixa fica a vista em toda borda`() {
        for (edge in HudEdge.entries) {
            runDesktopComposeUiTest {
                setContent { notch(edge = edge, autoRetract = true) }

                onNodeWithContentDescription(
                    "Barra HUD recolhida · INFORMATA2: Crítico · DeepSeek: Sem projeção"
                ).assertIsDisplayed()
                assertTrue(notchInnerAlpha(edge) < 0.05f, "$edge: o notch devia estar recolhido")
            }
        }
    }

    /** O ponteiro na faixa conta como sobre a HUD: é por ela que a íris abre. */
    @Test
    fun `o ponteiro sobre a faixa conta como sobre o notch`() = runDesktopComposeUiTest {
        val reported = mutableListOf<Boolean>()
        setContent { notch(autoRetract = true, onHoverChange = { hovered -> reported += hovered }) }

        onNodeWithTag(HUD_RETRACTED_STRIP_TAG).performMouseInput { enter(center) }
        waitForIdle()
        assertEquals(true, reported.last())
    }

    /** Aberta, a íris revela o notch inteiro e o alfinete aparece ao lado da mão. */
    @Test
    fun `aberta a iris revela o notch e o alfinete`() = runDesktopComposeUiTest {
        var open by mutableStateOf(false)
        setContent { notch(expanded = open, autoRetract = true, onToggleAutoRetract = {}) }

        assertTrue(notchInnerAlpha() < 0.05f)
        onNodeWithTag(HUD_RETRACT_HANDLE_TAG).assertDoesNotExist()
        open = true
        waitForIdle()
        assertTrue(notchInnerAlpha() > 0.9f)
        onNodeWithContentDescription("Manter a barra HUD aberta").assertIsDisplayed()
        open = false
        waitForIdle()
        assertTrue(notchInnerAlpha() < 0.05f)
    }

    /** No meio da íris o notch está só em parte: a revelação é um quadro, não um salto. */
    @Test
    fun `a iris abre por quadros e com animacao reduzida abre de uma vez`() {
        fun midway(motion: AppMotionPolicy): Float {
            var alpha = 0f
            runDesktopComposeUiTest {
                var open by mutableStateOf(false)
                setContent { notch(expanded = open, autoRetract = true, motion = motion) }
                mainClock.autoAdvance = false
                open = true
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeBy(AppGargantuaTokens.irisOpenMillis * 15L / 100)
                alpha = notchInnerAlpha(atCorner = true)
            }
            return alpha
        }
        // Aos 15% o disco ainda não chegou ao canto de dentro do notch.
        assertTrue(midway(AppMotionPolicy.Static) < 0.05f, "com animação o notch não pode surgir inteiro")
        assertTrue(midway(AppMotionPolicy.Reduced) > 0.9f, "com animação reduzida é corte seco")
    }

    /** O alfinete alterna o modo e diz a ação, não só o estado. */
    @Test
    fun `o alfinete alterna o modo recolher`() = runDesktopComposeUiTest {
        var toggles = 0
        setContent { notch(expanded = true, onToggleAutoRetract = { toggles += 1 }) }

        onNodeWithContentDescription("Recolher a barra HUD quando parada").performClick()
        assertEquals(1, toggles)
    }

    // ------------------------------------------------------------ balão da engrenagem (rodada 3)

    /** O conteúdo de teste do balão da engrenagem: uma ação do rodapé. */
    @Composable
    private fun appBalloonFixture(onRefresh: () -> Unit) {
        HudAppBalloonContent(
            language = AppLanguage.PT,
            appVersion = CURRENT_APP_VERSION,
            countdown = null,
            updateIndicator = null,
            actions = {
                FooterActionGroup(language = AppLanguage.PT, onRefresh = onRefresh, onOpenSettings = {})
            }
        )
    }

    @Composable
    private fun notchWithActions(onRefresh: () -> Unit = {}) {
        AppTheme(isDark = true) {
            Box(modifier = Modifier.size(900.dp, 600.dp)) {
                HudNotch(
                    accounts = accounts,
                    edge = HudEdge.TOP,
                    sizes = hudNotchSizes(accounts, HudEdge.TOP, "Carregando", false),
                    fallbackLabel = "Carregando",
                    expanded = true,
                    appBalloon = { appBalloonFixture(onRefresh) },
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
        onNodeWithText("Modo de janela").assertDoesNotExist()
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
     * Em repouso — sem sessão, sem atenção, sem coleta — o reflexo corre pelos
     * arcos com a política contínua (issue #322), e sem ela o anel fica parado.
     */
    @Test
    fun `o reflexo corre pelo anel parado so com a politica continua`() {
        assertIdleRingMoves(listOf(AppRingArc(0.8f, AppTone.OK), AppRingArc(0.6f, AppTone.WARNING)))
    }

    /**
     * O anel do Codex no print da #322 (3% e 0%) ficava inteiramente parado.
     * Aqui 1% e 0%: abaixo do mínimo do reflexo do valor, então só o brilho da
     * trilha pode mexer o anel — e com a política contínua ele mexe.
     */
    @Test
    fun `anel quase vazio tambem se mexe com a politica continua`() {
        assertIdleRingMoves(listOf(AppRingArc(0.01f, AppTone.OK), AppRingArc(0f, AppTone.OK)))
    }

    private fun assertIdleRingMoves(ringArcs: List<AppRingArc>) {
        fun frames(policy: AppMotionPolicy): Pair<PixelMap, PixelMap> {
            lateinit var first: PixelMap
            lateinit var second: PixelMap
            runDesktopComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    AppTheme(isDark = true, motion = policy) {
                        Box(modifier = Modifier.testTag(RING_FRAME).background(Color.Black).padding(6.dp)) {
                            AppUsageRing(arcs = ringArcs, description = "anel")
                        }
                    }
                }
                mainClock.advanceTimeBy(RING_ENTRANCE_SETTLE_MILLIS)
                first = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()
                mainClock.advanceTimeBy(500)
                second = onNodeWithTag(RING_FRAME).captureToImage().toPixelMap()
            }
            return first to second
        }

        val (liveA, liveB) = frames(AppMotionPolicy.Live)
        assertTrue(differs(liveA, liveB), "com a política contínua o reflexo devia ter andado")
        val (staticA, staticB) = frames(AppMotionPolicy.Static)
        assertTrue(!differs(staticA, staticB), "sem a política o anel parado não devia mudar: ${diffReport(staticA, staticB)}")
    }

    private fun diffReport(a: PixelMap, b: PixelMap): String {
        var count = 0
        var maxDelta = 0f
        val where = mutableListOf<String>()
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a[x, y] != b[x, y]) {
                    count++
                    val d = maxOf(kotlin.math.abs(a[x, y].red - b[x, y].red), kotlin.math.abs(a[x, y].green - b[x, y].green), kotlin.math.abs(a[x, y].alpha - b[x, y].alpha))
                    maxDelta = maxOf(maxDelta, d)
                    if (where.size < 5) where += "($x,$y)"
                }
            }
        }
        return "px=$count maxDelta=$maxDelta size=${a.width}x${a.height} em $where"
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
        // F10: os sinais são as linhas do detalhe do aviso, um por linha.
        onNodeWithText("Contexto saturado · 1 sessão\nSem resposta há 2h10").assertIsDisplayed()

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

    // ------------------------------------------------ corpo: horizonte (M1)

    /** Um notch sem contas: nenhum anel se mexendo no quadro, só o corpo. */
    private fun ComposeUiTest.horizonBody(dark: Boolean, motion: AppMotionPolicy = AppMotionPolicy.Static) {
        setContent {
            AppTheme(isDark = dark, motion = motion) {
                Box(modifier = Modifier.size(900.dp, 600.dp)) {
                    HudNotch(
                        accounts = emptyList(),
                        edge = HudEdge.TOP,
                        sizes = hudNotchSizes(emptyList(), HudEdge.TOP, "Nenhuma API", false),
                        fallbackLabel = "Nenhuma API",
                        expanded = false,
                        onHoverChange = {},
                        onDragStart = {},
                        onDragMove = {},
                        onDragEnd = {},
                        onRefreshAccount = {},
                        gearDescription = GEAR
                    )
                }
            }
        }
    }

    /** Ponto do corpo longe do texto e do filete: depois do ombro de 8dp e do filete. */
    private fun PixelMap.bodyPixel(): Color = this[22, height / 2]

    /** A borda de dentro (embaixo, no topo da tela), do lado quente. */
    private fun PixelMap.rimPixel(): Color = this[width * 3 / 4, height - 1]

    @Test
    fun `no tema escuro o corpo e o nucleo escuro e a borda e luz quente`() = runDesktopComposeUiTest {
        horizonBody(dark = true)
        val pixels = onNodeWithTag(HUD_CONTENT_TEST_TAG).captureToImage().toPixelMap()
        val body = pixels.bodyPixel()
        val horizon = AppGargantuaTokens.horizon
        assertTrue(
            kotlin.math.abs(body.red - horizon.red) + kotlin.math.abs(body.green - horizon.green) +
                kotlin.math.abs(body.blue - horizon.blue) < 0.02f,
            "o corpo devia ser o núcleo escuro: $body"
        )
        val rim = pixels.rimPixel()
        assertTrue(rim.red - rim.blue > 0.08f, "a borda devia ser luz quente, não cinza: $rim")
    }

    @Test
    fun `no tema claro o corpo continua a superficie e so a borda ganha o doppler`() = runDesktopComposeUiTest {
        horizonBody(dark = false)
        val pixels = onNodeWithTag(HUD_CONTENT_TEST_TAG).captureToImage().toPixelMap()
        assertTrue(pixels.bodyPixel().luminance() > 0.5f, "o tema claro não pode virar núcleo escuro: ${pixels.bodyPixel()}")
        val rim = pixels.rimPixel()
        assertTrue(rim.red - rim.blue > 0.08f, "a borda devia ter o tom da paleta Gargantua: $rim")
    }

    /**
     * A respiração do anel de fótons é contínua: com a política, dois instantes
     * pintam a borda diferente; sem ela, fica o quadro zero.
     */
    @Test
    fun `a borda respira so com a politica continua`() {
        fun frames(policy: AppMotionPolicy): Pair<PixelMap, PixelMap> {
            lateinit var first: PixelMap
            lateinit var second: PixelMap
            runDesktopComposeUiTest {
                mainClock.autoAdvance = false
                horizonBody(dark = true, motion = policy)
                mainClock.advanceTimeBy(500)
                first = onNodeWithTag(HUD_CONTENT_TEST_TAG).captureToImage().toPixelMap()
                mainClock.advanceTimeBy(AppGargantuaTokens.horizonBreathMillis / 4L)
                second = onNodeWithTag(HUD_CONTENT_TEST_TAG).captureToImage().toPixelMap()
            }
            return first to second
        }

        val (liveA, liveB) = frames(AppMotionPolicy.Live)
        assertTrue(liveA.rimPixel() != liveB.rimPixel(), "com a política contínua a borda devia ter respirado")
        val (staticA, staticB) = frames(AppMotionPolicy.Static)
        assertTrue(!differs(staticA, staticB), "sem a política a borda devia ficar parada")
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
    /** Folga para o desenho de entrada dos arcos assentar (mola `GENTLE`). */
    private val RING_ENTRANCE_SETTLE_MILLIS = 1_500L

    /** Põe o ponteiro no anel da conta, achado pela frase inteira da semântica dele. */
    // ------------------------------------------------------------ B3 · jato relativístico

    /**
     * O balão abre pelo jato: no começo só o feixe atravessa a caixa (quase nada
     * pintado), e ao fim dos [AppGargantuaTokens.jetOpenMillis] ele está inteiro.
     */
    @Test
    fun `o balao abre desdobrando a partir do feixe`() = runDesktopComposeUiTest {
        setContent { notch(expanded = true) }
        mainClock.autoAdvance = false
        onNodeWithContentDescription(INFORMATA_RING).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(AppGargantuaTokens.jetOpenMillis * 15L / 100)
        val crossing = paintedPixels(onNodeWithTag(HUD_BALLOON_TEST_TAG).captureToImage().toPixelMap())
        mainClock.advanceTimeBy(AppGargantuaTokens.jetOpenMillis + 100L)
        val open = paintedPixels(onNodeWithTag(HUD_BALLOON_TEST_TAG).captureToImage().toPixelMap())
        assertTrue(open > 0, "o balão aberto não pintou nada")
        assertTrue(crossing * 5 < open, "no começo o balão já estava pintado: $crossing de $open")
    }

    /** Trocar de anel com o balão aberto repete o jato a partir do anel novo. */
    @Test
    fun `trocar de anel repete a abertura pelo feixe`() = runDesktopComposeUiTest {
        setContent { notch(expanded = true) }
        hoverRing(INFORMATA_RING)
        mainClock.advanceTimeBy(AppGargantuaTokens.jetOpenMillis + 100L)
        mainClock.autoAdvance = false
        onNodeWithContentDescription(DEEPSEEK_RING).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(AppGargantuaTokens.jetOpenMillis * 15L / 100)
        val crossing = paintedPixels(onNodeWithTag(HUD_BALLOON_TEST_TAG).captureToImage().toPixelMap())
        mainClock.advanceTimeBy(AppGargantuaTokens.jetOpenMillis + 100L)
        val open = paintedPixels(onNodeWithTag(HUD_BALLOON_TEST_TAG).captureToImage().toPixelMap())
        onNodeWithText("DeepSeek").assertIsDisplayed()
        assertTrue(crossing * 5 < open, "a troca não repetiu o jato: $crossing de $open")
    }

    /**
     * Com "Reduzir animações" não há quadro intermediário: o balão sai do nada
     * direto para inteiro, sem feixe nem desdobrar.
     */
    @Test
    fun `reduzir animacoes abre o balao inteiro de uma vez`() = runDesktopComposeUiTest {
        setContent { notch(expanded = true, motion = AppMotionPolicy.Reduced) }
        mainClock.autoAdvance = false
        onNodeWithContentDescription(INFORMATA_RING).performMouseInput { moveTo(center) }
        val frames = (1..6).mapNotNull {
            mainClock.advanceTimeByFrame()
            val balloon = onAllNodesWithTag(HUD_BALLOON_TEST_TAG).fetchSemanticsNodes()
            if (balloon.isEmpty()) null else paintedPixels(onNodeWithTag(HUD_BALLOON_TEST_TAG).captureToImage().toPixelMap())
        }
        mainClock.advanceTimeBy(AppGargantuaTokens.jetOpenMillis + 100L)
        val settled = paintedPixels(onNodeWithTag(HUD_BALLOON_TEST_TAG).captureToImage().toPixelMap())
        assertTrue(frames.isNotEmpty(), "o balão não abriu")
        assertEquals(settled, frames.last())
        assertTrue(frames.all { painted -> painted == 0 || painted == settled }, "quadro intermediário: $frames de $settled")
    }

    // ------------------------------------------------------------ D5 · horizonte de eventos

    /** A conta principal com o 7d em [percent]. */
    private fun withWeekly(percent: String): List<HudAccount> {
        val first = accounts.first()
        val quotas = first.quotas.map { quota -> if (quota.shortLabel == "7d") quota.copy(percentText = percent) else quota }
        return listOf(first.copy(quotas = quotas)) + accounts.drop(1)
    }

    /** Dado novo rola pelo horizonte: no meio da troca a linha não é a final, e no fim é. */
    @Test
    fun `percentual novo rola so os digitos que mudaram`() = runDesktopComposeUiTest {
        var list by mutableStateOf(withWeekly("9%"))
        setContent { notch(list = list) }
        waitForIdle()
        mainClock.autoAdvance = false
        list = withWeekly("12%")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(AppGargantuaTokens.rollMillis / 3L)
        val rolling = onNodeWithText("7d 12%").captureToImage().toPixelMap()
        mainClock.advanceTimeBy(AppGargantuaTokens.rollMillis * 2L)
        val settled = onNodeWithText("7d 12%").captureToImage().toPixelMap()
        assertTrue(differs(rolling, settled), "no meio da troca a linha já estava parada")
        mainClock.advanceTimeBy(AppGargantuaTokens.rollMillis.toLong())
        assertTrue(!differs(settled, onNodeWithText("7d 12%").captureToImage().toPixelMap()), "a linha ainda se mexia depois da troca")
    }

    /** Com "Reduzir animações" o dado novo aparece de uma vez. */
    @Test
    fun `reduzir animacoes troca o percentual sem rolar`() = runDesktopComposeUiTest {
        var list by mutableStateOf(withWeekly("9%"))
        setContent { notch(list = list, motion = AppMotionPolicy.Reduced) }
        waitForIdle()
        mainClock.autoAdvance = false
        list = withWeekly("12%")
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val first = onNodeWithText("7d 12%").captureToImage().toPixelMap()
        mainClock.advanceTimeBy(AppGargantuaTokens.rollMillis * 2L)
        assertTrue(!differs(first, onNodeWithText("7d 12%").captureToImage().toPixelMap()), "com animação reduzida o número rolou")
    }

    /**
     * Pixels da caixa do balão que não são o fundo. O canto (0, 0) fica na faixa
     * da cauda do lado do notch, fora do corpo arredondado: é sempre o fundo.
     */
    private fun paintedPixels(pixels: PixelMap): Int {
        val background = pixels[0, 0]
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                val distance = kotlin.math.abs(pixel.red - background.red) +
                    kotlin.math.abs(pixel.green - background.green) +
                    kotlin.math.abs(pixel.blue - background.blue)
                if (distance > 0.06f) count++
            }
        }
        return count
    }

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
