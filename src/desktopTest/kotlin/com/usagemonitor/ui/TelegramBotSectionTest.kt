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

    private fun model(enabled: Boolean, code: String? = "AB12CD", onPair: () -> Unit = {}) = TelegramBotSectionModel(
        enabled = enabled,
        token = "123:abc",
        statusLabel = "Conectado como @usage_monitor_bot · 1 conversa(s)",
        statusTone = AppTone.OK,
        chats = listOf(TelegramChat(51L, "@edilson")),
        pairingCode = code,
        pairingHint = code?.let { "Vale por mais 10 min." },
        statusPreview = "📊 Usage Monitor\ncoleta 11:42 BRT · há 1 min\n\nAnthropic · Padrão — 🔴 Crítico\nClaude 5h · reinicia 13:05\n▰▰▰▰▰▰▱▱▱▱  68%",
        onEnabledChange = {},
        onTokenChange = {},
        onStartPairing = onPair,
        onRemoveChat = {},
        onSendTest = {},
        connected = true,
        botUsername = "usage_monitor_bot"
    )

    @Test
    fun `pairing step shows the command with copy and the telegram link`() = runDesktopComposeUiTest {
        var paired = false
        setContent {
            ScreenTestTheme { Box(Modifier.width(640.dp)) { TelegramBotSection(model(enabled = true) { paired = true }, AppLanguage.PT) } }
        }

        onNodeWithText("1 · Token do bot").assertIsDisplayed()
        onNodeWithText("2 · Parear uma conversa").assertIsDisplayed()
        onNodeWithText("@edilson").assertIsDisplayed()
        onNodeWithText("/start AB12CD").assertIsDisplayed()
        onNodeWithText("Abrir no Telegram").assertIsDisplayed()
        onNodeWithText("Copiar").performClick()
        onNodeWithText("Copiado ✓").assertIsDisplayed()
        onNodeWithText("Gerar novo código").performClick()
        assertEquals(true, paired)

        val output = File("build/issue396-screenshots").also { it.mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(output, "telegram-section.png"))
    }

    @Test
    fun `the sample reply opens on demand`() = runDesktopComposeUiTest {
        setContent { ScreenTestTheme { Box(Modifier.width(640.dp)) { TelegramBotSection(model(enabled = true, code = null), AppLanguage.PT) } } }

        assertEquals(0, onAllNodesWithText("Claude 5h", substring = true).fetchSemanticsNodes().size)
        onNodeWithText("Parear outra conversa").assertIsDisplayed()
        onNodeWithText("▸ Ver como o bot responde").performClick()
        waitForIdle()
        onNodeWithText("Claude 5h", substring = true).assertIsDisplayed()
    }

    @Test
    fun `disabled bot hides chats and pairing`() = runDesktopComposeUiTest {
        setContent { ScreenTestTheme { Box(Modifier.width(640.dp)) { TelegramBotSection(model(enabled = false), AppLanguage.PT) } } }

        assertEquals(0, onAllNodesWithText("@edilson").fetchSemanticsNodes().size)
        assertEquals(0, onAllNodesWithText("/start AB12CD").fetchSemanticsNodes().size)
        onNodeWithText("1 · Token do bot").assertIsDisplayed()
    }
}
