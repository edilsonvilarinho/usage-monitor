package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.TelegramBotSection
import com.usagemonitor.presentation.ui.components.TelegramBotSectionModel
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TelegramBotSectionTest {

    private fun model(enabled: Boolean, onPair: () -> Unit = {}) = TelegramBotSectionModel(
        enabled = enabled,
        token = "123:abc",
        statusLabel = "Conectado · 1 conversa(s)",
        statusTone = AppTone.OK,
        chats = listOf(TelegramChat(51L, "@edilson")),
        pairingCode = "AB12CD",
        pairingHint = "Vale por mais 10 min.",
        statusPreview = "Usage Monitor · 11:42 BRT\nAnthropic · Padrão · em uso\n  Claude 5h 68% (13:05)",
        onEnabledChange = {},
        onTokenChange = {},
        onStartPairing = onPair,
        onRemoveChat = {},
        onSendTest = {}
    )

    @Test
    fun `enabled bot shows chats, pairing command and a real status reply`() = runDesktopComposeUiTest {
        var paired = false
        setContent {
            ScreenTestTheme { Box(Modifier.width(640.dp)) { TelegramBotSection(model(enabled = true) { paired = true }, AppLanguage.PT) } }
        }

        onNodeWithText("@edilson").assertIsDisplayed()
        onNodeWithText("No Telegram, envie ao bot: /start AB12CD").assertIsDisplayed()
        onNodeWithText("você › /status").assertIsDisplayed()
        onNodeWithText("Parear conversa").performClick()
        assertEquals(true, paired)

        val output = File("build/issue387-screenshots").also { it.mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(output, "telegram-section.png"))
    }

    @Test
    fun `disabled bot hides chats and pairing`() = runDesktopComposeUiTest {
        setContent { ScreenTestTheme { Box(Modifier.width(640.dp)) { TelegramBotSection(model(enabled = false), AppLanguage.PT) } } }

        assertEquals(0, onAllNodesWithText("@edilson").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("Parear conversa").fetchSemanticsNodes().size)
    }
}
