package com.usagemonitor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.AppShellActions
import com.usagemonitor.HUD_BALLOON_WIDTH
import com.usagemonitor.HUD_HANDLE_GAP
import com.usagemonitor.HUD_HANDLE_SIZE
import com.usagemonitor.HudEdge
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.HudWindowContent
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.hudFloatingColumnWindowSize
import com.usagemonitor.hudFloatingBalloonToggle
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HUD_APP_BALLOON_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_BALLOON_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_FLOATING_HOLE_DRAG_TAG
import com.usagemonitor.presentation.ui.HUD_GEAR_HANDLE_TAG
import com.usagemonitor.presentation.ui.HUD_RING_ITEM_TEST_TAG
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudFloatingBalloon
import com.usagemonitor.presentation.ui.HudMinimizeProgress
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.verticalCollapsed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

/**
 * Composable de conteúdo da janela da HUD (`HudWindowContent`) com o view model vazio e as ações no-op.
 * Em `floating = true` o buraco aparece e o notch não; em `floating = false` é o oposto. Os balões são
 * abertos pelos lambdas reais (`onFloatingBalloonToggle` com estado de teste, como o host).
 */
@OptIn(ExperimentalTestApi::class)
class HudWindowContentTest {

    @Test
    fun `flutuante mostra o buraco e nao monta o notch`() = hudTest {
        render(floating = true, expanded = false)
        onAllNodesWithTag(HUD_FLOATING_HOLE_DRAG_TAG).assertCountEquals(1)
        onAllNodesWithTag(HUD_CONTENT_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `ancorada mostra o notch e nao o buraco da flutuante`() = hudTest {
        render(floating = false, expanded = false)
        onAllNodesWithTag(HUD_CONTENT_TEST_TAG).assertCountEquals(1)
        onAllNodesWithTag(HUD_FLOATING_HOLE_DRAG_TAG).assertCountEquals(0)
    }

    @Test
    fun `flutuante expandida, hover num anel abre o balao dessa conta pelo lambda real`() = hudTest {
        render(floating = true, expanded = true)
        onAllNodesWithTag(HUD_BALLOON_TEST_TAG).assertCountEquals(0)
        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG)[2].performMouseInput { enter(center) }
        waitForIdle()
        onAllNodesWithTag(HUD_BALLOON_TEST_TAG).assertCountEquals(1)
        onAllNodesWithTag(HUD_APP_BALLOON_CONTENT_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `flutuante expandida, engrenagem abre o balao do app e um segundo clique o fecha`() = hudTest {
        render(floating = true, expanded = true)
        onNodeWithTag(HUD_GEAR_HANDLE_TAG).performClick()
        waitForIdle()
        onAllNodesWithTag(HUD_APP_BALLOON_CONTENT_TEST_TAG).assertCountEquals(1)
        onNodeWithTag(HUD_GEAR_HANDLE_TAG).performClick()
        waitForIdle()
        onAllNodesWithTag(HUD_APP_BALLOON_CONTENT_TEST_TAG).assertCountEquals(0)
    }

    private companion object {
        const val FALLBACK_LABEL = "Sem conta"

        val accounts: List<HudAccount> = listOf("Alfa", "Beta", "Gama", "Delta", "Epsilon", "Zeta", "Eta")
            .mapIndexed { index, label -> account(index, label) }

        /** Mesma janela que `HudFloatingHudTest` usa para a coluna de contas de exemplo. */
        val WINDOW: DpSize = hudFloatingColumnWindowSize(
            columnSize = verticalCollapsed(accounts, FALLBACK_LABEL, compact = false),
            balloonSize = DpSize(HUD_BALLOON_WIDTH, 240.dp),
            holeDiameter = HudOrbitParams().holeD.dp,
            handleSize = HUD_HANDLE_SIZE,
            gap = HUD_HANDLE_GAP
        )

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

        fun hudTest(block: ComposeUiTest.() -> Unit) =
            runDesktopComposeUiTest(width = WINDOW.width.value.toInt(), height = WINDOW.height.value.toInt(), block = block)

        /** Monta o conteúdo com estado de balão no próprio teste; o `onFloatingBalloonToggle` é o do host. */
        fun ComposeUiTest.render(floating: Boolean, expanded: Boolean) {
            val viewModel = emptyDashboardViewModel(MutableStateFlow(emptySet()))
            val sizes = hudNotchSizes(accounts, HudEdge.RIGHT, FALLBACK_LABEL, hasUpdateIndicator = false)
            val actions = AppShellActions(
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
            setContent {
                var balloon by remember { mutableStateOf<HudFloatingBalloon?>(null) }
                val scope = rememberCoroutineScope()
                ScreenTestTheme {
                    HudWindowContent(
                        floating = floating,
                        expanded = expanded,
                        dragging = false,
                        accounts = accounts,
                        edge = HudEdge.RIGHT,
                        notchCenter = null,
                        sizes = sizes,
                        settledSizes = null,
                        minimize = HudMinimizeProgress(emptyMap(), moving = false),
                        fallbackLabel = FALLBACK_LABEL,
                        fallbackTone = AppTone.OK,
                        updateIndicator = null,
                        updateAction = null,
                        language = AppLanguage.EN,
                        viewModel = viewModel,
                        actions = actions,
                        exportScope = scope,
                        onCloseRequest = {},
                        teamEnabledProfileIds = emptySet(),
                        cliSessionPulses = emptyMap(),
                        teamSessionPulses = emptyMap(),
                        toggleMinimize = {},
                        floatingParams = HudOrbitParams(),
                        floatingBalloon = balloon,
                        onFloatingBalloonToggle = { requested -> balloon = hudFloatingBalloonToggle(balloon, requested) },
                        floatingSide = HudEdge.RIGHT,
                        floatingHoleScreenY = WINDOW.height / 2f,
                        floatingAreaTop = 0.dp,
                        floatingAreaBottom = WINDOW.height,
                        appBalloonHeight = 120.dp,
                        autoRetract = false,
                        onToggleAutoRetract = {},
                        onToggleFloating = {},
                        onHoverChange = {},
                        onDragStart = {},
                        onDragMove = {},
                        onDragEnd = {},
                        onReorderAccounts = {}
                    )
                }
            }
        }
    }
}
