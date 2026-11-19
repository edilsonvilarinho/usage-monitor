package com.usagemonitor.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.semantics.SemanticsActions
import com.usagemonitor.AppShellActions
import com.usagemonitor.HudAccountActions
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Buraco negro (#600, A05; #602, A02): o clique (ou Enter com foco) no buraco alterna a lista das minimizadas;
 * hover não abre. Esc e clique na margem do notch, fora do buraco, fecham a lista. A linha abre o balão da conta
 * só por clique ou Enter, com a volta "← N no buraco" e o Restaurar, que tira só aquela conta.
 * Tags `hudBlackHole` e `hudContent` (literais).
 */
@OptIn(ExperimentalTestApi::class)
class HudBlackHoleBalloonTest {

    private val labels = listOf("Anthropic — Alfa", "Anthropic — Bravo", "Anthropic — Charlie", "Anthropic — Delta")
    private val minimized = labels.map { label -> account(label, minimized = true) }

    @Test
    fun `hover no buraco abre a lista`() = runDesktopComposeUiTest {
        setNotch(minimized)
        hoverHole()
        waitForIdle()

        assertTrue(
            onAllNodesWithText(labels[0], useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "nome '${labels[0]}' deveria aparecer na lista do buraco após o hover (#612)"
        )
    }

    @Test
    fun `clique no buraco abre a lista com os quatro nomes`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(1) }
    }

    @Test
    fun `segundo clique no buraco fecha a lista`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        clickHole()

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(0) }
    }

    @Test
    fun `Enter com foco no buraco alterna a lista`() = runDesktopComposeUiTest {
        setNotch(minimized)
        onNodeWithTag("hudBlackHole").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("hudBlackHole").performKeyInput { pressKey(Key.Enter) }
        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(1) }

        onNodeWithTag("hudBlackHole").performKeyInput { pressKey(Key.Enter) }
        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(0) }
    }

    @Test
    fun `Esc fecha a lista do buraco`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        onNodeWithTag("hudBlackHole").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("hudBlackHole").performKeyInput { pressKey(Key.Escape) }

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(0) }
    }

    @Test
    fun `clique na margem do notch fora do buraco fecha a lista`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        clickNotchOutsideHole()

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(0) }
    }

    @Test
    fun `hover numa linha da lista nao abre o balao da conta`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        onNodeWithText(labels[1]).performMouseInput { enter(center) }
        Thread.sleep(HOVER_SETTLE_MS)
        waitForIdle()

        onAllNodesWithText("← 4 no buraco").assertCountEquals(0)
        onAllNodesWithText(labels[1]).assertCountEquals(1)
    }

    @Test
    fun `hover numa linha da lista realca so aquela linha e nao abre o balao`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        onNodeWithText(labels[1], useUnmergedTree = true).performMouseInput { enter(center) }
        waitForIdle()

        onAllNodesWithTag(HOVERED_ROW_TAG, useUnmergedTree = true).assertCountEquals(1)
        onAllNodes(hasTestTag(HOVERED_ROW_TAG) and hasAnyDescendant(hasText(labels[1])), useUnmergedTree = true).assertCountEquals(1)
        onAllNodesWithText("← 4 no buraco").assertCountEquals(0)
    }

    @Test
    fun `selecionar uma linha mostra o balao da conta com a volta e Restaurar`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        onNodeWithText(labels[1]).performClick()

        onAllNodesWithText("← 4 no buraco").assertCountEquals(1)
        onAllNodesWithContentDescription("Restaurar").assertCountEquals(1)
    }

    @Test
    fun `voltar pela linha de volta mostra a lista outra vez`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        onNodeWithText(labels[1]).performClick()
        onNodeWithText("← 4 no buraco").performClick()

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(1) }
    }

    @Test
    fun `restaurar tira so aquela conta do buraco`() = runDesktopComposeUiTest {
        val toggled = mutableListOf<UsageTargetKey>()
        setNotch(minimized, onToggleMinimize = { target -> toggled += target })
        clickHole()
        onNodeWithText(labels[2]).performClick()
        onNodeWithContentDescription("Restaurar").performClick()

        assertEquals(listOf(minimized[2].targetKey), toggled)
    }

    @Test
    fun `restaurar no balao da conta fecha o balao, sem devolver a lista do buraco`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        onNodeWithText(labels[2]).performClick()
        onNodeWithContentDescription("Restaurar").performClick()

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(0) }
        onAllNodesWithText("← 3 no buraco").assertCountEquals(0)
    }

    @Test
    fun `conta que sai do buraco por outro caminho com o balao aberto fecha o balao`() = runDesktopComposeUiTest {
        val state = setNotch(minimized)
        clickHole()
        onNodeWithText(labels[1]).performClick()
        runOnIdle { state.value = state.value.map { account -> if (account.label == labels[1]) account.copy(minimized = false) else account } }
        waitForIdle()

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(0) }
        onAllNodesWithText("← 3 no buraco").assertCountEquals(0)
    }

    @Test
    fun `balao de um anel fecha quando o Minimizar muda a conta que ele mostra`() = runDesktopComposeUiTest {
        setNotch(listOf(account(labels[0], minimized = false), account(labels[1], minimized = true)))
        hoverRingOf(labels[0])
        onNodeWithContentDescription("Minimizar").performClick()
        waitForIdle()

        onAllNodesWithContentDescription("Restaurar").assertCountEquals(0)
        onAllNodesWithText(labels[0]).assertCountEquals(0)
    }

    @Test
    fun `outra conta mudar de minimized nao fecha o balao de um anel`() = runDesktopComposeUiTest {
        val state = setNotch(listOf(account(labels[0], minimized = false), account(labels[1], minimized = false)))
        hoverRingOf(labels[0])
        onAllNodesWithContentDescription("Minimizar").assertCountEquals(1)

        runOnIdle { state.value = state.value.map { account -> if (account.label == labels[1]) account.copy(minimized = true) else account } }
        waitForIdle()

        onAllNodesWithContentDescription("Minimizar").assertCountEquals(1)
    }

    @Test
    fun `buraco esvaziado fecha o balao e nao reabre quando a conta volta a minimizar`() = runDesktopComposeUiTest {
        val state = setNotch(listOf(minimized[0]))
        clickHole()
        onNodeWithText(labels[0]).performClick()
        onNodeWithContentDescription("Restaurar").performClick()
        waitForIdle()
        onAllNodesWithContentDescription("Restaurar").assertCountEquals(0)

        runOnIdle { state.value = listOf(minimized[0]) }
        waitForIdle()

        onAllNodesWithText(labels[0]).assertCountEquals(0)
    }

    @Test
    fun `auto minimizada nao tem Restaurar e mostra o texto da auto`() = runDesktopComposeUiTest {
        val accounts = listOf(
            account(labels[0], minimized = true, auto = true),
            account(labels[1], minimized = true)
        )
        setNotch(accounts)
        clickHole()
        onNodeWithText(labels[0]).performClick()

        onAllNodesWithContentDescription("Restaurar").assertCountEquals(0)
        onAllNodesWithText("volta sozinha quando couber").assertCountEquals(1)
    }

    @Test
    fun `foco na linha da lista e Enter abrem o balao da conta`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        onNodeWithText(labels[0]).performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithText(labels[0]).performKeyInput { pressKey(Key.Enter) }

        onAllNodesWithText("← 4 no buraco").assertCountEquals(1)
    }

    @Test
    fun `foco na linha da lista e Enter do teclado numerico abrem o balao da conta`() = runDesktopComposeUiTest {
        setNotch(minimized)
        clickHole()
        onNodeWithText(labels[3]).performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithText(labels[3]).performKeyInput { pressKey(Key.NumPadEnter) }

        onAllNodesWithText("← 4 no buraco").assertCountEquals(1)
    }

    /** Clique num glifo em órbita força a coleta da conta dele e não abre a lista (#631, A03). */
    @Test
    fun `clique num glifo do buraco chama refresh da conta e mantem a lista aberta`() = runDesktopComposeUiTest {
        val refreshed = mutableListOf<UsageTargetKey>()
        setNotch(
            listOf(account("Ativa", minimized = false), account("Zeta", minimized = true)),
            onRefreshAccount = { target -> refreshed += target }
        )

        // Hover no buraco abre a lista (#612); o clique no glifo, que está dentro do buraco, não pode fechá-la.
        onNodeWithTag("hudBlackHole").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithTag(HOLE_GLYPH_TAG, useUnmergedTree = true).performMouseInput { click(center) }
        waitForIdle()

        assertEquals(
            listOf(UsageTargetKey(ApiSource.ANTHROPIC, "Zeta")),
            refreshed,
            "clique no glifo da conta 'Zeta' deve chamar refresh dela (#631, A03)"
        )
        assertEquals(
            true,
            onAllNodesWithText("Zeta", useUnmergedTree = true).fetchSemanticsNodes().size >= 1,
            "clique no glifo não deve fechar a lista aberta por hover (#631, A03)"
        )
    }

    /** Clique no centro do buraco continua abrindo a lista, sem coleta (#631, A03). */
    @Test
    fun `clique no centro do buraco abre a lista sem chamar refresh`() = runDesktopComposeUiTest {
        val refreshed = mutableListOf<UsageTargetKey>()
        setNotch(
            listOf(account("Ativa", minimized = false), account("Zeta", minimized = true)),
            onRefreshAccount = { target -> refreshed += target }
        )

        clickHole()
        waitForIdle()

        assertEquals(emptyList<UsageTargetKey>(), refreshed, "clique no centro do buraco não chama refresh (#631, A03)")
        assertTrue(
            onAllNodesWithText("Zeta", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "clique no centro do buraco deve abrir a lista com 'Zeta' (#631, A03)"
        )
    }

    private fun ComposeUiTest.hoverRingOf(label: String) {
        onNodeWithContentDescription(label, substring = true, useUnmergedTree = true).performMouseInput { moveTo(center) }
        waitForIdle()
    }

    private fun ComposeUiTest.hoverHole() {
        onNodeWithTag("hudBlackHole").performMouseInput { enter(center) }
    }

    private fun ComposeUiTest.clickHole() {
        onNodeWithTag("hudBlackHole").performClick()
    }

    /** Clique a 2 px de uma quina do notch, do lado oposto ao buraco: margem, sem anel e sem buraco. */
    private fun ComposeUiTest.clickNotchOutsideHole() {
        val notch = onNodeWithTag(NOTCH_TAG).fetchSemanticsNode().boundsInRoot
        val hole = onNodeWithTag("hudBlackHole").fetchSemanticsNode().boundsInRoot
        // Canto do notch cai fora do corpo arredondado: o gesto nao recebe o clique (medido, HUDPROBE).
        // Ponto no meio da faixa acima do buraco, dentro do corpo.
        val point = if (hole.top - notch.top > MARGIN_PX) {
            Offset(notch.width / 2f, (hole.top - notch.top) / 2f)
        } else {
            Offset(notch.width - MARGIN_PX, notch.height - MARGIN_PX)
        }
        onNodeWithTag(NOTCH_TAG).performMouseInput { click(point) }
    }

    /** Estado das contas fica aqui; o toggle de minimizar troca a conta como o app, então o Restaurar a tira do buraco. */
    private fun ComposeUiTest.setNotch(
        initial: List<HudAccount>,
        onToggleMinimize: (UsageTargetKey) -> Unit = {},
        onRefreshAccount: (UsageTargetKey) -> Unit = {}
    ): MutableState<List<HudAccount>> {
        val accounts = mutableStateOf(initial)
        setContent {
            ScreenTestTheme(isDark = true) {
                HudNotch(
                    accounts = accounts.value,
                    edge = HudEdge.LEFT,
                    sizes = hudNotchSizes(accounts.value, HudEdge.LEFT, "Carregando", false),
                    fallbackLabel = "Carregando",
                    expanded = true,
                    language = AppLanguage.PT,
                    onRefreshAccount = onRefreshAccount,
                    accountActions = { account ->
                        HudAccountActions(
                            account = account,
                            language = AppLanguage.PT,
                            teamEnabledProfileIds = emptySet(),
                            cliSessionPulse = SessionPulse.EMPTY,
                            teamSessionPulse = SessionPulse.EMPTY,
                            actions = inertShellActions(),
                            onRefresh = {},
                            onToggleMinimize = { target ->
                                onToggleMinimize(target)
                                accounts.value = accounts.value.map { item ->
                                    if (item.targetKey == target) item.copy(minimized = !item.minimized) else item
                                }
                            }
                        )
                    }
                )
            }
        }
        return accounts
    }

    /** Ações do app sem efeito: o balão da conta só precisa delas para montar as janelas do card. */
    private fun inertShellActions() = AppShellActions(
        refreshAll = {},
        openSettings = {},
        openHelp = {},
        exportSnapshot = { null },
        onExportFailure = {},
        openAdminOverview = null,
        openTeamPresenceOverview = null,
        selectDashboardTeam = {},
        openComparison = {},
        openHistory = { _, _ -> },
        openCliSessions = {},
        openCodexCliSessions = {},
        openTeamUsage = {},
        openTeamPresence = {},
        openAnalysis = {}
    )

    private fun account(label: String, minimized: Boolean, auto: Boolean = false): HudAccount {
        val quota = HudQuota("5h", "28%", 0.28f, AppTone.OK, resetText = null, hasForecast = false)
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, label),
            label = label,
            statusLabel = "Ok",
            tone = AppTone.OK,
            quotas = listOf(quota),
            focusIndex = 0,
            minimized = minimized,
            autoMinimized = auto
        )
    }

    private companion object {
        const val NOTCH_TAG = "hudContent"
        const val HOVERED_ROW_TAG = "hudBlackHoleListRowHovered"
        const val HOLE_GLYPH_TAG = "hudBlackHoleOrbitGlyph"
        const val MARGIN_PX = 2f

        /** Espera real para o atraso de 160 ms da linha antiga: se ele ainda existisse, a linha abriria o balão. */
        const val HOVER_SETTLE_MS = 400L
    }
}
