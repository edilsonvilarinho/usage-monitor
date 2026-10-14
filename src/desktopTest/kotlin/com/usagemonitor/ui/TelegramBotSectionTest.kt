package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.TELEGRAM_CHAT_CONTROL_TEST_TAG_PREFIX
import com.usagemonitor.presentation.ui.components.TELEGRAM_CRITICAL_SOUND_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.TELEGRAM_GROUP_ALERTS_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.TELEGRAM_BATCH_ALERTS_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.TELEGRAM_COMPACT_PANEL_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.TELEGRAM_SOURCE_HEALTH_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.TELEGRAM_PC_ACTIONS_SWITCH_TEST_TAG
import com.usagemonitor.domain.entity.TelegramBotPreferences
import com.usagemonitor.presentation.ui.components.TELEGRAM_SOURCE_CONTROL_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.TelegramBotSection
import com.usagemonitor.presentation.ui.components.TelegramBotSectionModel
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TelegramBotSectionTest {

    @Test
    fun `each chat has a control switch that turns it read only`() = runDesktopComposeUiTest {
        var change: Pair<Long, Boolean>? = null
        val base = model(enabled = true, code = null)
        val withRoles = TelegramBotSectionModel(
            enabled = base.enabled, token = base.token, statusLabel = base.statusLabel, statusTone = base.statusTone,
            chats = listOf(TelegramChat(51L, "@edilson"), TelegramChat(52L, "Time Infra", readOnly = true)),
            pairingCode = null, pairingHint = null, statusPreview = base.statusPreview,
            onEnabledChange = {}, onTokenChange = {}, onStartPairing = {}, onRemoveChat = {}, onSendTest = {},
            connected = true, onChatControlChange = { id, control -> change = id to control }
        )
        setContent { ScreenTestTheme { Box(Modifier.width(640.dp)) { TelegramBotSection(withRoles, AppLanguage.PT) } } }

        onNodeWithText("Controle").assertIsDisplayed()
        onNodeWithText("Só leitura").assertIsDisplayed()
        onNodeWithTag(TELEGRAM_CHAT_CONTROL_TEST_TAG_PREFIX + 52L).assertIsOff()
        onNodeWithTag(TELEGRAM_CHAT_CONTROL_TEST_TAG_PREFIX + 51L).performClick()
        assertEquals(51L to false, change)
    }

    @Test
    fun `pc actions switch starts off and sends the preference`() = runDesktopComposeUiTest {
        var sent: TelegramBotPreferences? = null
        val base = model(enabled = true, code = null)
        val withCallback = TelegramBotSectionModel(
            enabled = base.enabled, token = base.token, statusLabel = base.statusLabel, statusTone = base.statusTone,
            chats = base.chats, pairingCode = null, pairingHint = null, statusPreview = base.statusPreview,
            onEnabledChange = {}, onTokenChange = {}, onStartPairing = {}, onRemoveChat = {}, onSendTest = {},
            connected = true, onPreferencesChange = { preferences -> sent = preferences }
        )
        setContent { ScreenTestTheme { Box(Modifier.width(640.dp)) { TelegramBotSection(withCallback, AppLanguage.PT) } } }

        onNodeWithTag(TELEGRAM_PC_ACTIONS_SWITCH_TEST_TAG).assertIsOff()
        onNodeWithTag(TELEGRAM_PC_ACTIONS_SWITCH_TEST_TAG).performClick()
        assertEquals(TelegramBotPreferences(allowPcActions = true), sent)
    }

    /** #408, AA26: "Só alerta crítico toca" nasce ligado; desligar devolve o conjunto com o campo invertido. */
    @Test
    fun `critical sound switch starts on and sends the preference`() = runDesktopComposeUiTest(width = 1024, height = 2000) {
        var sent: TelegramBotPreferences? = null
        val base = model(enabled = true, code = null)
        val withCallback = TelegramBotSectionModel(
            enabled = base.enabled, token = base.token, statusLabel = base.statusLabel, statusTone = base.statusTone,
            chats = base.chats, pairingCode = null, pairingHint = null, statusPreview = base.statusPreview,
            onEnabledChange = {}, onTokenChange = {}, onStartPairing = {}, onRemoveChat = {}, onSendTest = {},
            connected = true, onPreferencesChange = { preferences -> sent = preferences }
        )
        setContent { ScreenTestTheme { Box(Modifier.width(640.dp)) { TelegramBotSection(withCallback, AppLanguage.PT) } } }

        onNodeWithTag(TELEGRAM_CRITICAL_SOUND_SWITCH_TEST_TAG).assertIsOn()
        onNodeWithTag(TELEGRAM_CRITICAL_SOUND_SWITCH_TEST_TAG).performClick()
        assertEquals(TelegramBotPreferences(soundOnlyOnCritical = false), sent)
        // #408, AA1: "Agrupar alertas repetidos" também nasce ligado.
        onNodeWithTag(TELEGRAM_GROUP_ALERTS_SWITCH_TEST_TAG).assertIsOn()
        // #408, AA25: "Juntar alertas" também.
        onNodeWithTag(TELEGRAM_BATCH_ALERTS_SWITCH_TEST_TAG).assertIsOn()
        // #408, AA15: "Painel em uma linha" também.
        onNodeWithTag(TELEGRAM_COMPACT_PANEL_SWITCH_TEST_TAG).assertIsOn()
        // #408, AA18: "Avisar fonte com falha" também.
        onNodeWithTag(TELEGRAM_SOURCE_HEALTH_SWITCH_TEST_TAG).assertIsOn()
    }

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
    // Cena alta: as opções da #408 empurram o exemplo para baixo da altura padrão (768).
    fun `the sample reply opens on demand`() = runDesktopComposeUiTest(width = 1024, height = 2000) {
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

    /** #398, Y8: a permissão de mudar fontes é um interruptor do card, nascido desligado. */
    @Test
    fun `source control switch reports the change`() = runDesktopComposeUiTest {
        var allowed: Boolean? = null
        setContent {
            ScreenTestTheme {
                Box(Modifier.width(640.dp)) {
                    TelegramBotSection(
                        model(enabled = true, code = null).copyWith(onAllowSourceControlChange = { value -> allowed = value }),
                        AppLanguage.PT
                    )
                }
            }
        }

        onNodeWithText("Permitir mudar fontes pelo bot").assertIsDisplayed()
        onNodeWithTag(TELEGRAM_SOURCE_CONTROL_SWITCH_TEST_TAG).assertIsOff().performClick()
        assertEquals(true, allowed)
    }

    private fun TelegramBotSectionModel.copyWith(onAllowSourceControlChange: (Boolean) -> Unit) = TelegramBotSectionModel(
        enabled = enabled, token = token, statusLabel = statusLabel, statusTone = statusTone, chats = chats,
        pairingCode = pairingCode, pairingHint = pairingHint, statusPreview = statusPreview,
        onEnabledChange = onEnabledChange, onTokenChange = onTokenChange, onStartPairing = onStartPairing,
        onRemoveChat = onRemoveChat, onSendTest = onSendTest, connected = connected, botUsername = botUsername,
        allowSourceControl = allowSourceControl, onAllowSourceControlChange = onAllowSourceControlChange,
        dailySummaryHour = dailySummaryHour, onDailySummaryHourChange = onDailySummaryHourChange
    )

    /** #398, Y1: o seletor do resumo vai de "Desligado" às horas oferecidas. */
    @Test
    fun `daily summary selector reports the chosen hour`() = runDesktopComposeUiTest {
        var hour: Int? = -1
        setContent {
            ScreenTestTheme {
                Box(Modifier.width(720.dp)) {
                    TelegramBotSection(
                        TelegramBotSectionModel(
                            enabled = true, token = "123:abc", statusLabel = "Conectado", statusTone = AppTone.OK,
                            chats = listOf(TelegramChat(51L, "@edilson")), pairingCode = null, pairingHint = null,
                            statusPreview = "", onEnabledChange = {}, onTokenChange = {}, onStartPairing = {},
                            onRemoveChat = {}, onSendTest = {}, connected = true,
                            dailySummaryHour = null, onDailySummaryHourChange = { value -> hour = value }
                        ),
                        AppLanguage.PT
                    )
                }
            }
        }

        onNodeWithText("Resumo diário").assertIsDisplayed()
        onNodeWithText("08:00").performClick()
        assertEquals(8, hour)
    }
}
