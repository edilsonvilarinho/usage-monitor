package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.FOOTER_ADMIN_OVERVIEW_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_BETA_BADGE_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_COUNTDOWN_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_EXPORT_SNAPSHOT_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_HELP_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_VERSION_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_TEAM_PRESENCE_TEST_TAG
import com.usagemonitor.presentation.ui.components.FooterBar
import kotlinx.coroutines.channels.Channel
import kotlin.time.Instant
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class FooterBarTest {

    @Test
    fun `FooterBar displays version and countdown badges with settings icon`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // Por tag: os dois emblemas viram texto de uma barra de estado, e um
        // assert por texto encontraria a versão em qualquer outro canto da tela.
        // `useUnmergedTree`: o emblema é âncora de tooltip, e o `TooltipBox`
        // agrega os descendentes na árvore merged — a tag só existe na crua.
        onNodeWithTag(FOOTER_VERSION_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("v1.1.0")
        onNodeWithTag(FOOTER_COUNTDOWN_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("02:05")
        onNodeWithContentDescription("Abrir configurações").assertIsDisplayed()
        onAllNodesWithText("Histórico").assertCountEquals(0)
    }

    /** Issue #355: build beta mostra o selo ao lado do número; estável, não. */
    @Test
    fun `FooterBar marks a beta build next to the version`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var version by mutableStateOf("42.0.0-beta.1")

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = version,
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithTag(FOOTER_VERSION_TEST_TAG, useUnmergedTree = true).assertTextEquals("v42.0.0-beta.1")
        onNodeWithTag(FOOTER_BETA_BADGE_TEST_TAG).assertIsDisplayed()
        onNodeWithText("Beta").assertIsDisplayed()

        version = "42.0.0"
        waitForIdle()

        onNodeWithTag(FOOTER_BETA_BADGE_TEST_TAG).assertDoesNotExist()
    }

    /**
     * A ajuda é a porta óbvia do rodapé, e some na HUD —
     * por isso ela também tem item na bandeja e `F1`, que este teste não alcança.
     */
    @Test
    fun `FooterBar abre a ajuda`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var opened = 0

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        onOpenHelp = { opened++ },
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithContentDescription("Abrir ajuda").assertIsDisplayed()
        onNodeWithTag(FOOTER_HELP_TEST_TAG).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `FooterBar esconde a visao de todas as contas sem callback`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // Quem não administra é a maioria: o botão nem existe.
        onAllNodesWithTag(FOOTER_ADMIN_OVERVIEW_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `FooterBar mostra e aciona a visao de todas as contas`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var opened = 0

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        onOpenAdminOverview = { opened += 1 }
                    )
                }
            }
        }

        onNodeWithTag(FOOTER_ADMIN_OVERVIEW_TEST_TAG).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `FooterBar esconde a presenca do time sem o callback`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // A porta do integrante comum é o botão do card, escopado na conta dele.
        onAllNodesWithTag(FOOTER_TEAM_PRESENCE_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `FooterBar mostra e aciona a presenca do time`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var opened = 0

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        onOpenTeamPresence = { opened += 1 }
                    )
                }
            }
        }

        onNodeWithTag(FOOTER_TEAM_PRESENCE_TEST_TAG).performClick()

        assertEquals(1, opened)
    }


    // ------------------------------------- retrato do Dashboard (issue #215)

    @Test
    fun `FooterBar esconde a exportacao sem o callback`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // Os geradores de captura montam o rodapé sem escrever em disco.
        onAllNodesWithTag(FOOTER_EXPORT_SNAPSHOT_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `FooterBar mostra e aciona a exportacao do retrato`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var exported = 0

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        onExportSnapshot = { exported += 1 }
                    )
                }
            }
        }

        onNodeWithTag(FOOTER_EXPORT_SNAPSHOT_TEST_TAG).performClick()

        assertEquals(1, exported)
    }

    // --------------------------------------------- modos de janela (issue #187)

    @Test
    fun `FooterBar opens settings action`() = runDesktopComposeUiTest {
        var opened = false
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = { opened = true },
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithContentDescription("Abrir configurações").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `FooterBar keeps controls accessible in narrow width`() = runDesktopComposeUiTest {
        var opened = false
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(320.dp)) {
                    FooterBar(
                        appVersion = "6.0.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 433.seconds,
                        onRefresh = {},
                        onOpenSettings = { opened = true },
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithText("v6.0.0").assertIsDisplayed()
        onNodeWithText("07:13").assertIsDisplayed()
        onNodeWithContentDescription("Abrir configurações").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `FooterBar decrements countdown and stops at zero without waiting real seconds`() = runDesktopComposeUiTest {
        val start = Instant.parse("2025-01-01T12:00:00Z")
        val tickChannel = Channel<Unit>(Channel.UNLIMITED)
        var currentNow = start

        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "9.0.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = start + 3.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { currentNow },
                        waitNextTick = { tickChannel.receive() }
                    )
                }
            }
        }

        onNodeWithText("00:03").assertIsDisplayed()

        currentNow += 1.seconds
        tickChannel.trySend(Unit)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("00:02").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        currentNow += 1.seconds
        tickChannel.trySend(Unit)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("00:01").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        currentNow += 1.seconds
        tickChannel.trySend(Unit)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("00:00").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        currentNow += 5.seconds
        tickChannel.trySend(Unit)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("00:00").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }
}
