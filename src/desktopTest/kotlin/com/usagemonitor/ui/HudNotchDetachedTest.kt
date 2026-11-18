package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.hudFloatingHoleDescription
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Modo flutuante (#619, A10b): o `HudNotch` destacado repousa no buraco e abre a coluna de sempre. */
@OptIn(ExperimentalTestApi::class)
class HudNotchDetachedTest {

    private val language = AppLanguage.EN
    private val holeDescription = hudFloatingHoleDescription(language)

    @Test
    fun `destacada em repouso mostra o buraco e nenhum anel de conta`() = runDesktopComposeUiTest {
        setNotch(detached = true, expanded = false)

        onNodeWithContentDescription(holeDescription).assertIsDisplayed()
        onAllNodes(hasContentDescription("INFORMATA", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `destacada aberta mostra os anéis e não o buraco`() = runDesktopComposeUiTest {
        setNotch(detached = true, expanded = true)

        assertTrue(onAllNodes(hasContentDescription("INFORMATA", substring = true)).fetchSemanticsNodes().isNotEmpty())
        onAllNodesWithContentDescription(holeDescription).assertCountEquals(0)
    }

    @Test
    fun `sem destacar o repouso segue a barra encaixada e nunca mostra o buraco`() = runDesktopComposeUiTest {
        setNotch(detached = false, expanded = false)

        assertTrue(onAllNodes(hasContentDescription("INFORMATA", substring = true)).fetchSemanticsNodes().isNotEmpty())
        onAllNodesWithContentDescription(holeDescription).assertCountEquals(0)
    }

    private fun ComposeUiTest.setNotch(detached: Boolean, expanded: Boolean) {
        setContent {
            AppTheme(isDark = true, motion = AppMotionPolicy.Static) {
                Box(modifier = Modifier.size(900.dp, 600.dp)) {
                    HudNotch(
                        accounts = accounts,
                        edge = HudEdge.TOP,
                        sizes = hudNotchSizes(accounts, HudEdge.TOP, "Carregando", false),
                        fallbackLabel = "Carregando",
                        expanded = expanded,
                        detached = detached,
                        language = language
                    )
                }
            }
        }
    }

    private val accounts = listOf(
        account("INFORMATA", "Ativo", AppTone.OK, HudQuota("5h", "28%", 0.28f, AppTone.OK, resetText = null, hasForecast = false)),
        account("OUTRA", "Ativo", AppTone.NEUTRAL, HudQuota("7d", "9%", 0.09f, AppTone.NEUTRAL, resetText = null, hasForecast = false))
    )

    private fun account(label: String, word: String, tone: AppTone, vararg quotas: HudQuota): HudAccount {
        return HudAccount(
            planLabel = null,
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, label),
            label = label,
            statusLabel = word,
            tone = tone,
            quotas = quotas.toList(),
            focusIndex = quotas.indices.maxByOrNull { index -> quotas[index].fraction } ?: 0
        )
    }
}
