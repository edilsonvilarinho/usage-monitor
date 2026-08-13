package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.presentation.ui.CliSessionRow
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SESSION_ID = "f66da412-9c3a-4e51-b7d2-1a8e0c5d7734"

@OptIn(ExperimentalTestApi::class)
class CopySessionCommandButtonTest {

    @Test
    fun `copies the resume command with the whole session id`() = runDesktopComposeUiTest {
        val copied = mutableListOf<String>()
        setContent {
            AppTheme(isDark = true) {
                CopySessionCommandButton(
                    sessionId = SESSION_ID,
                    language = AppLanguage.PT,
                    onCopy = { text -> copied += text }
                )
            }
        }

        onNodeWithContentDescription("Copiar comando de retomada").performClick()

        assertEquals(listOf("claude --resume $SESSION_ID"), copied)
    }

    /** Sessão de outra máquina: o `--resume` não teria transcript para abrir aqui. */
    @Test
    fun `a team session copies only the identifier`() = runDesktopComposeUiTest {
        val copied = mutableListOf<String>()
        setContent {
            AppTheme(isDark = true) {
                CopySessionCommandButton(
                    sessionId = SESSION_ID,
                    language = AppLanguage.PT,
                    isLocalSession = false,
                    onCopy = { text -> copied += text }
                )
            }
        }

        onNodeWithContentDescription("Copiar id da sessão").performClick()

        assertEquals(listOf(SESSION_ID), copied)
    }

    @Test
    fun `the label confirms the copy and goes back on its own`() = runDesktopComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            AppTheme(isDark = true) {
                CopySessionCommandButton(
                    sessionId = SESSION_ID,
                    language = AppLanguage.PT,
                    showLabel = true,
                    onCopy = {}
                )
            }
        }

        onNodeWithContentDescription("Copiar comando de retomada").performClick()
        mainClock.advanceTimeBy(100L)
        onNodeWithContentDescription("Copiado").assertIsDisplayed()

        mainClock.advanceTimeBy(2_500L)
        onNodeWithContentDescription("Copiar comando de retomada").assertIsDisplayed()
    }

    @Test
    fun `the session row offers the copy button`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(200.dp)) {
                    CliSessionRow(
                        session = summary(),
                        language = AppLanguage.PT,
                        onOpen = {}
                    )
                }
            }
        }

        onNodeWithContentDescription("Copiar comando de retomada").assertIsDisplayed()
    }

    @Test
    fun `the team session row offers the identifier instead of the command`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(200.dp)) {
                    CliSessionRow(
                        session = summary(),
                        language = AppLanguage.PT,
                        onOpen = {},
                        isLocalSession = false
                    )
                }
            }
        }

        onNodeWithContentDescription("Copiar id da sessão").assertIsDisplayed()
    }

    private fun summary(): CliSessionSummary {
        return CliSessionSummary(
            sessionId = SESSION_ID,
            filePath = "/tmp/$SESSION_ID.jsonl",
            cwd = "/workspace/usage-monitor",
            firstTs = Instant.parse("2026-08-13T10:00:00Z"),
            lastTs = Instant.parse("2026-08-13T11:00:00Z"),
            primaryModel = "claude-opus-5",
            turnCount = 2,
            outputTokens = 1_000L,
            cacheReadTokens = 40_000L
        )
    }
}
