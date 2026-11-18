package com.usagemonitor.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.HudOrbitLabels
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HUD_ORBIT_ACCOUNT_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_ORBIT_FIELD_TEST_TAG
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudOrbitField
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.hudRingDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HudOrbitFieldTest {

    @Test
    fun `seven accounts expose seven clickable nodes`() = runDesktopComposeUiTest {
        renderField(HudOrbitLabels.FULL) {}
        onAllNodesWithTag(HUD_ORBIT_ACCOUNT_TEST_TAG).assertCountEquals(7)
        accounts.forEach { account ->
            onNodeWithContentDescription(hudRingDescription(account, AppLanguage.EN)).assert(hasClickAction())
        }
    }

    @Test
    fun `each account has its own description on its node`() = runDesktopComposeUiTest {
        renderField(HudOrbitLabels.FULL) {}
        accounts.forEach { account ->
            onAllNodesWithContentDescription(hudRingDescription(account, AppLanguage.EN)).assertCountEquals(1)
        }
    }

    @Test
    fun `clicking the third account calls onAccountClick with index 2`() = runDesktopComposeUiTest {
        val clicked = mutableListOf<Int>()
        renderField(HudOrbitLabels.FULL) { index -> clicked += index }
        onAllNodesWithContentDescription(hudRingDescription(accounts[2], AppLanguage.EN)).assertCountEquals(1)
        onNodeWithContentDescription(hudRingDescription(accounts[2], AppLanguage.EN))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(2), clicked)
    }

    @Test
    fun `compact shows the quota and not the account name`() = runDesktopComposeUiTest {
        renderField(HudOrbitLabels.COMPACT) {}
        onAllNodesWithText("12%", substring = true).assertCountEquals(1)
        onAllNodesWithText("Conta Alfa", substring = true).assertCountEquals(0)
    }

    @Test
    fun `off shows no account text`() = runDesktopComposeUiTest {
        renderField(HudOrbitLabels.OFF) {}
        onAllNodesWithTag(HUD_ORBIT_ACCOUNT_TEST_TAG).assertCountEquals(7)
        accounts.forEach { account ->
            onAllNodesWithText(account.label, substring = true).assertCountEquals(0)
            onAllNodesWithText(account.quotas.first().percentText, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun `full field is larger than compact field`() {
        var fullWidth = -1
        runDesktopComposeUiTest {
            renderField(HudOrbitLabels.FULL) {}
            onAllNodesWithTag(HUD_ORBIT_FIELD_TEST_TAG).assertCountEquals(1)
            fullWidth = onNodeWithTag(HUD_ORBIT_FIELD_TEST_TAG).fetchSemanticsNode().size.width
        }
        var compactWidth = -1
        runDesktopComposeUiTest {
            renderField(HudOrbitLabels.COMPACT) {}
            onAllNodesWithTag(HUD_ORBIT_FIELD_TEST_TAG).assertCountEquals(1)
            compactWidth = onNodeWithTag(HUD_ORBIT_FIELD_TEST_TAG).fetchSemanticsNode().size.width
        }
        assertTrue(fullWidth > compactWidth, "FULL $fullWidth px, COMPACT $compactWidth px")
    }

    private companion object {
        val accounts: List<HudAccount> = listOf("Alfa", "Beta", "Gama", "Delta", "Epsilon", "Zeta", "Eta")
            .mapIndexed { index, name -> account(index, "Conta $name") }

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

        fun ComposeUiTest.renderField(labels: HudOrbitLabels, onAccountClick: (Int) -> Unit) {
            setContent {
                ScreenTestTheme {
                    HudOrbitField(
                        accounts = accounts,
                        params = HudOrbitParams(labels = labels),
                        language = AppLanguage.EN,
                        onAccountClick = onAccountClick
                    )
                }
            }
        }
    }
}
