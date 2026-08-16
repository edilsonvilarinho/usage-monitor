package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.FOOTER_ADMIN_OVERVIEW_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_TEAM_PRESENCE_TEST_TAG
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Instant
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class FooterBarTest {

    @Test
    fun `FooterBar displays version and countdown badges with settings icon`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
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

        onNodeWithText("v1.1.0").assertIsDisplayed()
        onNodeWithText("02:05").assertIsDisplayed()
        onNodeWithContentDescription("Abrir configurações").assertIsDisplayed()
        onAllNodesWithText("Histórico").assertCountEquals(0)
    }

    @Test
    fun `FooterBar esconde a visao de todas as contas sem callback`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
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
            AppTheme(isDark = true) {
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
            AppTheme(isDark = true) {
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
            AppTheme(isDark = true) {
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

    @Test
    fun `FooterBar opens settings action`() = runDesktopComposeUiTest {
        var opened = false
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
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
            AppTheme(isDark = true) {
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
            AppTheme(isDark = true) {
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
