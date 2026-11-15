package com.usagemonitor.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/**
 * Buraco negro (#600, A05): o hover no buraco abre a lista das minimizadas; a linha abre o balão da conta
 * com a volta "← N no buraco" e o Restaurar, que tira só aquela conta. Tag `hudBlackHole` (literal).
 */
@OptIn(ExperimentalTestApi::class)
class HudBlackHoleBalloonTest {

    private val labels = listOf("Anthropic — Alfa", "Anthropic — Bravo", "Anthropic — Charlie", "Anthropic — Delta")
    private val minimized = labels.map { label -> account(label, minimized = true) }

    @Test
    fun `hover no buraco abre a lista com os quatro nomes`() = runDesktopComposeUiTest {
        setNotch(minimized)
        hoverHole()

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(1) }
    }

    @Test
    fun `selecionar uma linha mostra o balao da conta com a volta e Restaurar`() = runDesktopComposeUiTest {
        setNotch(minimized)
        hoverHole()
        onNodeWithText(labels[1]).performClick()

        onAllNodesWithText("← 4 no buraco").assertCountEquals(1)
        onAllNodesWithContentDescription("Restaurar").assertCountEquals(1)
    }

    @Test
    fun `voltar pela linha de volta mostra a lista outra vez`() = runDesktopComposeUiTest {
        setNotch(minimized)
        hoverHole()
        onNodeWithText(labels[1]).performClick()
        onNodeWithText("← 4 no buraco").performClick()

        labels.forEach { label -> onAllNodesWithText(label).assertCountEquals(1) }
    }

    @Test
    fun `restaurar tira so aquela conta do buraco`() = runDesktopComposeUiTest {
        val toggled = mutableListOf<UsageTargetKey>()
        setNotch(minimized, onToggleMinimize = { target -> toggled += target })
        hoverHole()
        onNodeWithText(labels[2]).performClick()
        onNodeWithContentDescription("Restaurar").performClick()

        assertEquals(listOf(minimized[2].targetKey), toggled)
    }

    @Test
    fun `auto minimizada nao tem Restaurar e mostra o texto da auto`() = runDesktopComposeUiTest {
        val accounts = listOf(
            account(labels[0], minimized = true, auto = true),
            account(labels[1], minimized = true)
        )
        setNotch(accounts)
        hoverHole()
        onNodeWithText(labels[0]).performClick()

        onAllNodesWithContentDescription("Restaurar").assertCountEquals(0)
        onAllNodesWithText("volta sozinha quando couber").assertCountEquals(1)
    }

    @Test
    fun `foco na linha da lista e Enter abrem o balao da conta`() = runDesktopComposeUiTest {
        setNotch(minimized)
        hoverHole()
        onNodeWithText(labels[0]).performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithText(labels[0]).performKeyInput { pressKey(Key.Enter) }

        onAllNodesWithText("← 4 no buraco").assertCountEquals(1)
    }

    @Test
    fun `foco na linha da lista e Enter do teclado numerico abrem o balao da conta`() = runDesktopComposeUiTest {
        setNotch(minimized)
        hoverHole()
        onNodeWithText(labels[3]).performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithText(labels[3]).performKeyInput { pressKey(Key.NumPadEnter) }

        onAllNodesWithText("← 4 no buraco").assertCountEquals(1)
    }

    private fun ComposeUiTest.hoverHole() {
        onNodeWithTag("hudBlackHole").performMouseInput { enter(center) }
    }

    private fun ComposeUiTest.setNotch(accounts: List<HudAccount>, onToggleMinimize: (UsageTargetKey) -> Unit = {}) {
        setContent {
            ScreenTestTheme(isDark = true) {
                HudNotch(
                    accounts = accounts,
                    edge = HudEdge.LEFT,
                    sizes = hudNotchSizes(accounts, HudEdge.LEFT, "Carregando", false),
                    fallbackLabel = "Carregando",
                    expanded = true,
                    language = AppLanguage.PT,
                    accountActions = { account ->
                        HudAccountActions(
                            account = account,
                            language = AppLanguage.PT,
                            teamEnabledProfileIds = emptySet(),
                            cliSessionPulse = SessionPulse.EMPTY,
                            teamSessionPulse = SessionPulse.EMPTY,
                            actions = inertShellActions(),
                            onRefresh = {},
                            onToggleMinimize = onToggleMinimize
                        )
                    }
                )
            }
        }
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
}
