package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.WEB_ACCESS_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.WebAccessSection
import com.usagemonitor.presentation.ui.components.WebAccessSectionModel
import com.usagemonitor.presentation.ui.components.WebAccessUiStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WebAccessSectionTest {

    private fun model(enabled: Boolean, onEnabledChange: (Boolean) -> Unit = {}) = WebAccessSectionModel(
        enabled = enabled,
        portText = "47110",
        status = if (enabled) WebAccessUiStatus.RUNNING else WebAccessUiStatus.STOPPED,
        urls = if (enabled) listOf("http://192.168.0.14:47110/?t=abc") else emptyList(),
        failureMessage = null,
        onEnabledChange = onEnabledChange,
        onPortChange = {},
        onRegenerateToken = {}
    )

    @Test
    fun `disabled section shows only the warning and the switch`() = runDesktopComposeUiTest {
        var requested: Boolean? = null
        setContent { ScreenTestTheme { WebAccessSection(model(enabled = false) { requested = it }, AppLanguage.PT) } }

        onNodeWithText("Acesso pela rede local").assertIsDisplayed()
        assertEquals(0, onAllNodesWithText("Copiar").fetchSemanticsNodes().size)
        onNodeWithTag(WEB_ACCESS_SWITCH_TEST_TAG).performClick()
        assertEquals(true, requested)
    }

    @Test
    fun `enabled section lists the address with the copy action`() = runDesktopComposeUiTest {
        setContent { ScreenTestTheme { WebAccessSection(model(enabled = true), AppLanguage.PT) } }

        onNodeWithText("Ativo").assertIsDisplayed()
        onNodeWithText("http://192.168.0.14:47110/?t=abc").assertIsDisplayed()
        onNodeWithText("Copiar").assertIsDisplayed()
        onNodeWithText("Gerar novo endereço").assertIsDisplayed()
    }
}
