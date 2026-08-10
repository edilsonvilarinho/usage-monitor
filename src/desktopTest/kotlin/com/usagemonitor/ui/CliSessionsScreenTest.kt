package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.presentation.ui.CliSessionsContent
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CliSessionsScreenTest {

    @Test
    fun `list shows session count and total cost`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01", costMicros = 1_230_000L))
                        ),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSetHidden = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("1 sessão").assertIsDisplayed()
        onNodeWithText("session-").assertIsDisplayed()
        onNodeWithText("\$1.23").assertIsDisplayed()
        onNodeWithText("custo estimado").assertIsDisplayed()
    }

    @Test
    fun `header names the account the sessions belong to`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary("session-abcdef01")),
                            profileLabel = "INFORMATA2"
                        ),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSetHidden = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("INFORMATA2").assertIsDisplayed()
    }

    @Test
    fun `clicking hide reports the session and the new flag`() = runDesktopComposeUiTest {
        val hideCalls = mutableListOf<Pair<String, Boolean>>()

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = listOf(summary("session-abcdef01"))),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSetHidden = { sessionId, hidden -> hideCalls.add(sessionId to hidden) }
                    )
                }
            }
        }

        onNodeWithText("Ocultar").performClick()

        assertEquals(listOf("session-abcdef01" to true), hideCalls)
    }

    @Test
    fun `empty list explains where transcripts are read from`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(sessions = emptyList()),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSetHidden = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("Nenhuma sessão do Claude Code encontrada em ~/.claude/projects.").assertIsDisplayed()
    }

    @Test
    fun `detail renders the analytics sections`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01", costMicros = 5_000_000L)
        val detail = CliSessionDetail(
            summary = summary,
            turns = listOf(
                turn(seq = 1, cacheReadTokens = 10_000L, cacheWrite1hTokens = 4_000L),
                turn(seq = 2, cacheReadTokens = 30_000L, cacheWrite5mTokens = 2_000L)
            )
        )

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Ready(
                                sessionId = summary.sessionId,
                                result = CliSessionDetailResult(
                                    detail = detail,
                                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSetHidden = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("Taxa de acerto de cache").assertIsDisplayed()
        onNodeWithText("Distribuição de custo").assertIsDisplayed()
        // Abaixo da dobra na altura de teste: basta existirem na árvore.
        onNodeWithText("Economia do cache").assertExists()
        onNodeWithText("Contexto por turno").assertExists()
        onNodeWithText("Custo x economia acumulados").assertExists()
    }

    @Test
    fun `detail banner recommends compacting a saturated session`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01")
        // 650K de contexto vivo numa janela de 1M.
        val detail = CliSessionDetail(
            summary = summary,
            turns = listOf(turn(seq = 1, cacheReadTokens = 650_000L))
        )

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Ready(
                                sessionId = summary.sessionId,
                                result = CliSessionDetailResult(
                                    detail = detail,
                                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSetHidden = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("Sessão saturada").assertIsDisplayed()
        onNodeWithText(
            "Considere /compact ou abrir uma sessão nova: continuar assim sai caro e a janela está perto do limite."
        ).assertIsDisplayed()
        // O motivo do status precisa estar visível para o alerta ser conferível.
        onNodeWithText("65% da janela · \$0.3250 por mensagem").assertIsDisplayed()
    }

    @Test
    fun `detail banner reports a healthy session`() = runDesktopComposeUiTest {
        val summary = summary("session-abcdef01")
        val detail = CliSessionDetail(
            summary = summary,
            turns = listOf(turn(seq = 1, cacheReadTokens = 10_000L))
        )

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Ready(
                                sessionId = summary.sessionId,
                                result = CliSessionDetailResult(
                                    detail = detail,
                                    analytics = ComputeCliSessionAnalyticsUseCase().invoke(detail)
                                )
                            )
                        ),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSetHidden = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("Sessão saudável").assertIsDisplayed()
    }

    @Test
    fun `detail back button returns to the list`() = runDesktopComposeUiTest {
        var closed = false
        val summary = summary("session-abcdef01")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Success(
                            sessions = listOf(summary),
                            detail = CliSessionDetailUiState.Loading(summary.sessionId)
                        ),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = { closed = true },
                        onSetHidden = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("Voltar").performClick()

        assertEquals(true, closed)
    }

    @Test
    fun `error state shows the message`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(900.dp).height(700.dp)) {
                    CliSessionsContent(
                        state = CliSessionsUiState.Error("índice indisponível"),
                        language = AppLanguage.PT,
                        onBack = {},
                        onRefresh = {},
                        onToggleShowHidden = {},
                        onOpenSession = {},
                        onCloseDetail = {},
                        onSetHidden = { _, _ -> }
                    )
                }
            }
        }

        onNodeWithText("índice indisponível").assertIsDisplayed()
    }

    private fun summary(
        sessionId: String,
        costMicros: Long = 0L
    ): CliSessionSummary {
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = "/tmp/$sessionId.jsonl",
            cwd = "/workspace/usage-monitor",
            firstTs = Instant.parse("2026-08-01T10:00:00Z"),
            lastTs = Instant.parse("2026-08-01T11:00:00Z"),
            primaryModel = "claude-opus-5",
            turnCount = 2,
            outputTokens = 1_000L,
            cacheReadTokens = 40_000L,
            costMicros = costMicros
        )
    }

    private fun turn(
        seq: Int,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): CliSessionTurn {
        return CliSessionTurn(
            sessionId = "session-abcdef01",
            seq = seq,
            messageId = "msg-$seq",
            ts = Instant.parse("2026-08-01T10:0$seq:00Z"),
            model = "claude-opus-5",
            outputTokens = 500L,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens
        )
    }
}
