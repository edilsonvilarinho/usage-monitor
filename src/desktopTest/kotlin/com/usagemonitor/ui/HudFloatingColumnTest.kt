package com.usagemonitor.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HUD_RING_ITEM_TEST_TAG
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudFloatingColumn
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coluna do modo flutuante expandido (A11a): a coluna vertical de anéis da barra, ao lado do buraco.
 * Sem campo circular; o anel sob o ponteiro chama [onRingHovered] com o índice dele.
 */
@OptIn(ExperimentalTestApi::class)
class HudFloatingColumnTest {

    private val eightAccounts = listOf("A", "B", "C", "D", "E", "F", "G", "H").map { label -> account(label) }

    @Test
    fun `oito contas viram oito aneis na coluna`() = runDesktopComposeUiTest {
        setColumn(eightAccounts)

        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG).assertCountEquals(8)
    }

    @Test
    fun `hover no terceiro anel chama onRingHovered com o indice dois`() = runDesktopComposeUiTest {
        val hovered = mutableListOf<Int>()
        setColumn(eightAccounts, onRingHovered = { index -> hovered += index })

        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG)[2].performMouseInput { enter(center) }
        waitForIdle()

        assertEquals(listOf(2), hovered)
    }

    private fun ComposeUiTest.setColumn(
        accounts: List<HudAccount>,
        onRingHovered: (Int) -> Unit = {},
    ) {
        setContent {
            ScreenTestTheme(isDark = true, motion = AppMotionPolicy.Reduced) {
                HudFloatingColumn(
                    accounts = accounts,
                    fallbackLabel = "Carregando",
                    fallbackTone = AppTone.NEUTRAL,
                    language = AppLanguage.PT,
                    revealed = true,
                    onRingHovered = onRingHovered,
                    onRingRefresh = {},
                )
            }
        }
    }

    private fun account(label: String): HudAccount {
        val quota = HudQuota("5h", "28%", 0.28f, AppTone.OK, resetText = null, hasForecast = false)
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, label),
            label = label,
            statusLabel = "Ok",
            tone = AppTone.OK,
            quotas = listOf(quota),
            focusIndex = 0,
            sessionActive = false,
            minimized = false
        )
    }
}
