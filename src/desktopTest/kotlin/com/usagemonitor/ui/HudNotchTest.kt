package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.usagemonitor.HudEdge
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HUD_BAR_OPEN_DESCRIPTION
import com.usagemonitor.presentation.ui.HUD_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_UPDATE_INDICATOR_TAG
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
    private val countdown = "Próxima atualização automática"

    private val accounts = listOf(
        account(
            "INFORMATA2", "Crítico", AppTone.CRITICAL,
            HudQuota("5h", "28%", 0.28f, AppTone.OK, resetText = "22h59", hasForecast = true),
            HudQuota("7d", "9%", 0.09f, AppTone.CRITICAL, resetText = "Ter 21h00", hasForecast = true)
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
        nextRefreshAt: Instant? = null,
        nowProvider: () -> Instant = { now },
        waitNextTick: suspend () -> Unit = {},
        countdownUpdatesEnabled: Boolean = false,
        onHoverChange: (Boolean) -> Unit = {},
        onDragStart: () -> Unit = {},
        onDragMove: () -> Unit = {},
        onDragEnd: () -> Unit = {},
        onOpenFull: () -> Unit = {},
        onSwitchToCardsOnly: () -> Unit = {}
    ) {
        AppTheme(isDark = true) {
            Box(modifier = Modifier.size(900.dp, 600.dp)) {
                HudNotch(
                    accounts = list,
                    edge = edge,
                    sizes = hudNotchSizes(list, edge, fallbackLabel, nextRefreshAt != null, updateIndicator != null),
                    fallbackLabel = fallbackLabel,
                    expanded = expanded,
                    updateIndicator = updateIndicator,
                    nextRefreshAt = nextRefreshAt,
                    countdownDescription = countdown,
                    nowProvider = nowProvider,
                    waitNextTick = waitNextTick,
                    countdownUpdatesEnabled = countdownUpdatesEnabled,
                    onHoverChange = onHoverChange,
                    onDragStart = onDragStart,
                    onDragMove = onDragMove,
                    onDragEnd = onDragEnd,
                    onOpenFull = onOpenFull,
                    onSwitchToCardsOnly = onSwitchToCardsOnly
                )
            }
        }
    }

    // ------------------------------------------------------------ conteúdo

    /** Parado, cada conta tem percentual **e** palavra: cor nunca informa sozinha. */
    @Test
    fun `parado o notch mostra percentual e palavra de cada conta`() = runDesktopComposeUiTest {
        setContent { notch() }

        onNodeWithText("28%").assertIsDisplayed()
        onNodeWithText("Crítico").assertIsDisplayed()
        onNodeWithText("Sem projeção").assertIsDisplayed()
        onNodeWithContentDescription("INFORMATA2 (Max 20x) · Crítico · 5h 28% · 7d 9%").assertExists()
    }

    @Test
    fun `aberto cada cota ganha linha com reset e o painel diz o nome da conta`() = runDesktopComposeUiTest {
        setContent { notch(expanded = true) }

        onNodeWithText("INFORMATA2").assertIsDisplayed()
        // O plano da conta, ao lado do nome, como no ai-usagebar.
        onNodeWithText("Max 20x").assertIsDisplayed()
        onNodeWithText("22h59").assertIsDisplayed()
        onNodeWithText("Ter 21h00").assertIsDisplayed()
    }

    /** O notch parado fica na tela o tempo todo: o reset é detalhe sob demanda (#189). */
    @Test
    fun `parado o notch nao mostra a hora do reinicio`() = runDesktopComposeUiTest {
        setContent { notch(expanded = false) }

        onNodeWithText("22h59").assertDoesNotExist()
        onNodeWithText("Ter 21h00").assertDoesNotExist()
    }

    /** Saldo que não expira não imprime nada no lugar do reset — nem traço. */
    @Test
    fun `cota sem reset nao imprime nada no lugar`() = runDesktopComposeUiTest {
        setContent { notch(expanded = true) }

        // Aberto, o percentual aparece no notch e na linha do painel.
        onAllNodesWithText("\$2.27").assertCountEquals(2)
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
     * sem nada reclamar — foi o que cortou o texto da barra antiga ao meio. Aqui
     * o contêiner usa os números da geometria, e o teste afirma que o que foi
     * disposto é exatamente eles, nas quatro bordas, parado e aberto.
     */
    @Test
    fun `o contêiner tem exatamente o tamanho que a geometria calcula`() {
        for (edge in HudEdge.entries) {
            for (open in listOf(false, true)) {
                runDesktopComposeUiTest {
                    setContent { notch(expanded = open, edge = edge, nextRefreshAt = now + 2.minutes) }
                    val expected = hudNotchSizes(accounts, edge, "Carregando", showsCountdown = true, hasUpdateIndicator = false)
                    val target = if (open) expected.expanded else expected.collapsed
                    val bounds = onNodeWithTag(HUD_CONTENT_TEST_TAG).getUnclippedBoundsInRoot()
                    assertEquals(target.width, bounds.width, "$edge aberto=$open: largura")
                    assertEquals(target.height, bounds.height, "$edge aberto=$open: altura")
                }
            }
        }
    }

    // ------------------------------------------------------------ gestos

    @Test
    fun `o clique em qualquer ponto abre a janela completa`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent { notch(onOpenFull = { clicks += 1 }) }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `arrastar o notch nao abre a janela completa`() = runDesktopComposeUiTest {
        var clicks = 0
        val events = mutableListOf<String>()
        setContent {
            notch(
                onDragStart = { events += "start" },
                onDragMove = { events += "move" },
                onDragEnd = { events += "end" },
                onOpenFull = { clicks += 1 }
            )
        }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(60f, 0f))
            release()
        }
        waitForIdle()

        assertEquals(0, clicks)
        assertEquals("start", events.first())
        assertEquals("end", events.last())
        assertTrue(events.contains("move"), "esperava movimento em $events")
    }

    /** O arrasto sobrevive à recomposição que ele mesmo provoca na janela da HUD. */
    @Test
    fun `o arrasto sobrevive a recomposicao a cada movimento`() = runDesktopComposeUiTest {
        var moves by mutableStateOf(0)
        setContent { notch(fallbackLabel = "movimentos $moves", onDragMove = { moves += 1 }) }

        val target = onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION)
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
                onOpenFull = { opens += 1 },
                onSwitchToCardsOnly = { switches += 1 }
            )
        }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performMouseInput {
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

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performMouseInput { enter(center) }
        waitForIdle()
        assertEquals(true, reported.last())

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performMouseInput { exit(Offset(-1f, -1f)) }
        waitForIdle()
        assertEquals(false, reported.last())
    }

    // ------------------------------------------------------------ contagem (#185)

    @Test
    fun `a contagem aparece uma vez so, parado e aberto`() = runDesktopComposeUiTest {
        var open by mutableStateOf(false)
        setContent { notch(expanded = open, nextRefreshAt = now + 2.minutes + 5.seconds) }

        onNodeWithText("02:05").assertIsDisplayed()
        onNodeWithContentDescription(countdown).assertIsDisplayed()
        open = true
        waitForIdle()
        onAllNodesWithText("02:05").assertCountEquals(1)
    }

    @Test
    fun `sem proxima coleta a contagem nao existe`() = runDesktopComposeUiTest {
        setContent { notch() }

        onAllNodesWithText("02:05").assertCountEquals(0)
        onAllNodesWithContentDescription(countdown).assertCountEquals(0)
    }

    @Test
    fun `a linha de carregamento tambem mostra a contagem`() = runDesktopComposeUiTest {
        setContent { notch(list = emptyList(), nextRefreshAt = now + 2.minutes + 5.seconds) }

        onNodeWithText("Carregando").assertIsDisplayed()
        onNodeWithText("02:05").assertIsDisplayed()
    }

    @Test
    fun `a contagem decrementa e para em zero`() = runDesktopComposeUiTest {
        val tickChannel = Channel<Unit>(capacity = Channel.UNLIMITED)
        var currentNow = now
        setContent {
            notch(
                nextRefreshAt = now + 3.seconds,
                nowProvider = { currentNow },
                waitNextTick = { tickChannel.receive() },
                countdownUpdatesEnabled = true
            )
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

    // ------------------------------------------------------------ atualização (#225)

    @Test
    fun `sem indicador nenhum icone de atualizacao aparece`() = runDesktopComposeUiTest {
        setContent { notch() }

        onNodeWithTag(HUD_UPDATE_INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun `o indicador aparece uma vez e o clique nele abre a janela completa`() = runDesktopComposeUiTest {
        var opens = 0
        var open by mutableStateOf(false)
        setContent {
            notch(
                expanded = open,
                updateIndicator = HudUpdateIndicator(tone = AppTone.OK, description = "Atualização pronta"),
                onOpenFull = { opens += 1 }
            )
        }

        onNodeWithTag(HUD_UPDATE_INDICATOR_TAG).assertIsDisplayed()
        open = true
        waitForIdle()
        onAllNodesWithContentDescription("Atualização pronta").assertCountEquals(1)
        onNodeWithTag(HUD_UPDATE_INDICATOR_TAG).performClick()
        assertEquals(1, opens)
    }

    @Test
    fun `a linha de carregamento tambem mostra o indicador`() = runDesktopComposeUiTest {
        setContent {
            notch(
                list = emptyList(),
                updateIndicator = HudUpdateIndicator(tone = AppTone.INFO, description = "Atualização disponível")
            )
        }

        onNodeWithTag(HUD_UPDATE_INDICATOR_TAG).assertIsDisplayed()
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
                        Box(modifier = Modifier.background(Color.Black)) {
                            AppUsageRing(
                                arcs = listOf(AppRingArc(0.3f, AppTone.OK)),
                                description = "anel",
                                active = active.first().sessionActive
                            )
                        }
                    }
                }
                mainClock.advanceTimeBy(400)
                first = onNodeWithContentDescription("anel").captureToImage().toPixelMap()
                mainClock.advanceTimeBy(350)
                second = onNodeWithContentDescription("anel").captureToImage().toPixelMap()
            }
            return first to second
        }

        val (liveA, liveB) = frames(AppMotionPolicy.Live)
        assertTrue(differs(liveA, liveB), "com a política contínua o arco devia ter girado")
        val (staticA, staticB) = frames(AppMotionPolicy.Static)
        assertTrue(!differs(staticA, staticB), "sem a política o arco devia ficar parado")
    }

    private fun differs(a: PixelMap, b: PixelMap): Boolean {
        for (y in 0 until minOf(a.height, b.height)) {
            for (x in 0 until minOf(a.width, b.width)) {
                if (a[x, y] != b[x, y]) return true
            }
        }
        return false
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
